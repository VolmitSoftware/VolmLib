package art.arcane.volmit.packaging;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class LibraryRelocator {
    private LibraryRelocator() {
    }

    public static List<File> relocate(List<File> libraries, Map<String, String> relocations, File workDirectory)
            throws IOException {
        if (relocations.isEmpty()) {
            return libraries;
        }
        Map<String, String> prefixes = new LinkedHashMap<>();
        for (Map.Entry<String, String> relocation : relocations.entrySet()) {
            prefixes.put(relocation.getKey().replace('.', '/') + "/", relocation.getValue().replace('.', '/') + "/");
        }
        PrefixRemapper remapper = new PrefixRemapper(prefixes);
        Files.createDirectories(workDirectory.toPath());
        List<File> result = new ArrayList<>(libraries);
        for (File library : libraries) {
            if (library.isFile() && contains(library, prefixes)) {
                result.add(rewrite(library, remapper, prefixes, workDirectory, result.size()));
            }
        }
        return result;
    }

    private static boolean contains(File library, Map<String, String> prefixes) throws IOException {
        try (ZipFile zip = new ZipFile(library)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.endsWith(".class") && !name.startsWith("META-INF/") && remapped(name, prefixes)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean remapped(String name, Map<String, String> prefixes) {
        for (String prefix : prefixes.keySet()) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static File rewrite(File library, PrefixRemapper remapper, Map<String, String> prefixes,
                                File workDirectory, int index) throws IOException {
        Path target = workDirectory.toPath().resolve(index + "-" + library.getName().replaceAll("\\.jar$", "")
                + "-relocated.jar");
        try (ZipFile zip = new ZipFile(library);
             ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(target))) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.endsWith(".class") || name.startsWith("META-INF/") || !remapped(name, prefixes)) {
                    continue;
                }
                byte[] content;
                try (InputStream input = zip.getInputStream(entry)) {
                    content = input.readAllBytes();
                }
                ClassReader reader = new ClassReader(content);
                ClassWriter writer = new ClassWriter(0);
                reader.accept(new ClassRemapper(writer, remapper), 0);
                output.putNextEntry(new ZipEntry(remapper.map(reader.getClassName()) + ".class"));
                output.write(writer.toByteArray());
                output.closeEntry();
            }
        }
        return target.toFile();
    }

    private static final class PrefixRemapper extends Remapper {
        private final Map<String, String> prefixes;

        private PrefixRemapper(Map<String, String> prefixes) {
            super(Opcodes.ASM9);
            this.prefixes = prefixes;
        }

        @Override
        public String map(String internalName) {
            for (Map.Entry<String, String> prefix : prefixes.entrySet()) {
                if (internalName.startsWith(prefix.getKey())) {
                    return prefix.getValue() + internalName.substring(prefix.getKey().length());
                }
            }
            return internalName;
        }
    }
}

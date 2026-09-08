package art.arcane.volmit.packaging;

import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypePath;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;

public final class AdviceClasses {
    private static final String MARKER = "bytebuddy/asm/Advice$";
    private static final String CLASS_SUFFIX = ".class";
    private static final int SCAN_FLAGS = ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES;

    private AdviceClasses() {
    }

    public static Set<String> scan(File archive) throws IOException {
        Set<String> families = new HashSet<>();
        List<String> classes = new ArrayList<>();
        try (ZipFile zip = ZipFile.builder().setFile(archive).get()) {
            Enumeration<ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.endsWith(CLASS_SUFFIX) || name.startsWith("META-INF/")) {
                    continue;
                }
                String internalName = name.substring(0, name.length() - CLASS_SUFFIX.length());
                classes.add(internalName);
                if (carriesAdviceAnnotation(zip, entry)) {
                    families.add(family(internalName));
                }
            }
        }
        Set<String> restored = new TreeSet<>();
        for (String internalName : classes) {
            if (families.contains(family(internalName))) {
                restored.add(internalName.replace('/', '.'));
            }
        }
        return restored;
    }

    public static void restore(File original, File shrunk, Set<String> classes) throws IOException {
        if (classes.isEmpty()) {
            return;
        }
        Map<String, byte[]> originals = new LinkedHashMap<>(classes.size());
        try (ZipFile source = ZipFile.builder().setFile(original).get()) {
            for (String name : classes) {
                String entryName = name.replace('.', '/') + CLASS_SUFFIX;
                ZipArchiveEntry entry = source.getEntry(entryName);
                if (entry == null) {
                    throw new IOException("Advice class " + name + " is missing from " + original.getName());
                }
                try (InputStream input = source.getInputStream(entry)) {
                    originals.put(entryName, input.readAllBytes());
                }
            }
        }
        Path target = shrunk.toPath();
        Path temporary = target.resolveSibling(shrunk.getName() + ".restore");
        Set<String> pending = new HashSet<>(originals.keySet());
        try (ZipFile input = ZipFile.builder().setFile(shrunk).get();
             ZipArchiveOutputStream output = new ZipArchiveOutputStream(temporary.toFile())) {
            output.setLevel(9);
            output.setUseZip64(Zip64Mode.AsNeeded);
            Enumeration<ZipArchiveEntry> entries = input.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                byte[] content = originals.get(entry.getName());
                if (content == null) {
                    try (InputStream raw = input.getRawInputStream(entry)) {
                        output.addRawArchiveEntry(entry, raw);
                    }
                    continue;
                }
                pending.remove(entry.getName());
                ZipArchiveEntry copy = new ZipArchiveEntry(entry.getName());
                copy.setTime(entry.getTime());
                copy.setMethod(ZipEntry.DEFLATED);
                output.putArchiveEntry(copy);
                output.write(content);
                output.closeArchiveEntry();
            }
        } catch (IOException | RuntimeException failure) {
            Files.deleteIfExists(temporary);
            throw failure;
        }
        if (!pending.isEmpty()) {
            Files.deleteIfExists(temporary);
            throw new IOException("ProGuard dropped advice classes despite keep rules: " + pending);
        }
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static boolean carriesAdviceAnnotation(ZipFile zip, ZipArchiveEntry entry) throws IOException {
        Detector detector = new Detector();
        try (InputStream input = zip.getInputStream(entry)) {
            new ClassReader(input).accept(detector, SCAN_FLAGS);
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException failure) {
            throw new IOException("Cannot scan " + entry.getName() + " for advice annotations", failure);
        }
        return detector.found;
    }

    private static String family(String internalName) {
        int nested = internalName.indexOf('$', internalName.lastIndexOf('/') + 1);
        return nested < 0 ? internalName : internalName.substring(0, nested);
    }

    private static final class Detector extends ClassVisitor {
        private boolean found;

        private Detector() {
            super(Opcodes.ASM9);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            mark(descriptor);
            return null;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            return new FieldVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                    mark(annotation);
                    return null;
                }

                @Override
                public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String annotation,
                                                             boolean visible) {
                    mark(annotation);
                    return null;
                }
            };
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                         String[] exceptions) {
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                    mark(annotation);
                    return null;
                }

                @Override
                public AnnotationVisitor visitParameterAnnotation(int parameter, String annotation, boolean visible) {
                    mark(annotation);
                    return null;
                }

                @Override
                public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String annotation,
                                                             boolean visible) {
                    mark(annotation);
                    return null;
                }
            };
        }

        private void mark(String descriptor) {
            if (descriptor.contains(MARKER)) {
                found = true;
            }
        }
    }
}

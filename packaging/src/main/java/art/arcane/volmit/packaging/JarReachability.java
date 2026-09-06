package art.arcane.volmit.packaging;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class JarReachability {
    private static final String SERVICES_PREFIX = "META-INF/services/";

    private JarReachability() {
    }

    public static Set<String> unusedClasses(File artifact, List<String> prunePrefixes, List<String> keepPrefixes)
            throws IOException {
        if (prunePrefixes.isEmpty()) {
            return Set.of();
        }
        Map<String, Set<String>> references = new HashMap<>();
        Map<String, List<String>> entriesByClass = new HashMap<>();
        Map<String, Set<String>> families = new HashMap<>();
        Set<String> roots = new LinkedHashSet<>();
        try (JarFile jar = new JarFile(artifact)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                if (entry.getName().startsWith(SERVICES_PREFIX)) {
                    readServices(jar, entry, roots);
                    continue;
                }
                if (!entry.getName().endsWith(".class")) {
                    continue;
                }
                ClassReader reader;
                try (InputStream input = jar.getInputStream(entry)) {
                    reader = new ClassReader(input);
                }
                String name = reader.getClassName();
                entriesByClass.computeIfAbsent(name, ignored -> new ArrayList<>()).add(entry.getName());
                Set<String> dependencies = references.computeIfAbsent(name, ignored -> new LinkedHashSet<>());
                ReferenceCollector collector = new ReferenceCollector(dependencies);
                reader.accept(new ClassRemapper(new ClassWriter(0), collector), ClassReader.SKIP_DEBUG);
                char[] buffer = new char[reader.getMaxStringLength()];
                for (int index = 1; index < reader.getItemCount(); index++) {
                    int offset = reader.getItem(index);
                    if (offset != 0 && reader.readByte(offset - 1) == 7) {
                        collector.mapValue(reader.readConst(index, buffer));
                    }
                }
                families.computeIfAbsent(family(name), ignored -> new LinkedHashSet<>()).add(name);
                if (!startsWithAny(name, prunePrefixes) || startsWithAny(name, keepPrefixes)) {
                    roots.add(name);
                }
            }
        }

        Set<String> retained = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>(roots);
        while (!pending.isEmpty()) {
            String name = pending.removeFirst();
            if (!references.containsKey(name) || !retained.add(name)) {
                continue;
            }
            pending.addAll(references.get(name));
            pending.addAll(families.getOrDefault(family(name), Set.of()));
        }
        Set<String> unused = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : entriesByClass.entrySet()) {
            if (!retained.contains(entry.getKey())) {
                unused.addAll(entry.getValue());
            }
        }
        return unused;
    }

    private static void readServices(JarFile jar, JarEntry entry, Set<String> roots) throws IOException {
        roots.add(entry.getName().substring(SERVICES_PREFIX.length()).replace('.', '/'));
        String contents;
        try (InputStream input = jar.getInputStream(entry)) {
            contents = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String line : contents.split("\\R")) {
            int comment = line.indexOf('#');
            String provider = (comment < 0 ? line : line.substring(0, comment)).trim();
            if (!provider.isEmpty()) {
                roots.add(provider.replace('.', '/'));
            }
        }
    }

    private static boolean startsWithAny(String name, List<String> prefixes) {
        for (String prefix : prefixes) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String family(String name) {
        int nested = name.indexOf('$');
        return nested < 0 ? name : name.substring(0, nested);
    }

    private static final class ReferenceCollector extends Remapper {
        private final Set<String> references;

        private ReferenceCollector(Set<String> references) {
            super(Opcodes.ASM9);
            this.references = references;
        }

        @Override
        public String map(String internalName) {
            references.add(internalName);
            return internalName;
        }

        @Override
        public Object mapValue(Object value) {
            if (value instanceof String name) {
                String reference = name.replace('.', '/');
                if (reference.startsWith("[") && reference.endsWith(";")) {
                    int component = reference.lastIndexOf('[') + 1;
                    if (reference.charAt(component) == 'L') {
                        reference = reference.substring(component + 1, reference.length() - 1);
                    }
                }
                references.add(reference);
            }
            return super.mapValue(value);
        }
    }
}

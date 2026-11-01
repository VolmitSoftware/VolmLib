package art.arcane.volmit.packaging;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * Keeps the public surface of every bundled type reachable from a public API class signature, so addons compiled
 * against an API method's return, parameter or supertype can call members the plugin itself never uses.
 */
final class ApiSurface {
    private static final Pattern API_CLASS = Pattern.compile("(?:art/arcane|com/volmit)/.+/api/.+");
    private static final int VISIBLE = Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED;

    private ApiSurface() {
    }

    static List<String> keeps(File artifact) throws IOException {
        try (JarFile jar = new JarFile(artifact)) {
            Set<String> bundled = new LinkedHashSet<>();
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.endsWith(".class") && !name.startsWith("META-INF/") && !name.endsWith("module-info.class")) {
                    bundled.add(name.substring(0, name.length() - ".class".length()));
                }
            }
            Set<String> roots = new LinkedHashSet<>();
            for (String name : bundled) {
                if (API_CLASS.matcher(name).matches() && (reader(jar, name).getAccess() & Opcodes.ACC_PUBLIC) != 0) {
                    roots.add(name);
                }
            }
            Set<String> reached = new LinkedHashSet<>(roots);
            Deque<String> queue = new ArrayDeque<>(roots);
            while (!queue.isEmpty()) {
                for (String reference : references(reader(jar, queue.poll()))) {
                    if (bundled.contains(reference) && reached.add(reference)) {
                        queue.add(reference);
                    }
                }
            }
            List<String> rules = new ArrayList<>();
            for (String name : reached) {
                if (!roots.contains(name)) {
                    rules.add("-keep class " + name.replace('/', '.') + " { public protected *; }");
                }
            }
            return rules;
        }
    }

    private static ClassReader reader(JarFile jar, String name) throws IOException {
        try (InputStream input = jar.getInputStream(jar.getEntry(name + ".class"))) {
            return new ClassReader(input);
        }
    }

    private static Set<String> references(ClassReader reader) {
        Set<String> references = new LinkedHashSet<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName,
                              String[] interfaces) {
                if (superName != null) {
                    references.add(superName);
                }
                if (interfaces != null) {
                    references.addAll(List.of(interfaces));
                }
                signature(signature, false, references);
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                if ((access & VISIBLE) != 0) {
                    signature(descriptor, true, references);
                    signature(signature, true, references);
                }
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                             String[] exceptions) {
                if ((access & VISIBLE) != 0) {
                    signature(descriptor, false, references);
                    signature(signature, false, references);
                    if (exceptions != null) {
                        references.addAll(List.of(exceptions));
                    }
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return references;
    }

    private static void signature(String signature, boolean type, Set<String> references) {
        if (signature == null) {
            return;
        }
        SignatureVisitor visitor = new SignatureVisitor(Opcodes.ASM9) {
            private String current;

            @Override
            public void visitClassType(String name) {
                current = name;
                references.add(name);
            }

            @Override
            public void visitInnerClassType(String name) {
                current = current + '$' + name;
                references.add(current);
            }
        };
        SignatureReader reader = new SignatureReader(signature);
        if (type) {
            reader.acceptType(visitor);
        } else {
            reader.accept(visitor);
        }
    }
}

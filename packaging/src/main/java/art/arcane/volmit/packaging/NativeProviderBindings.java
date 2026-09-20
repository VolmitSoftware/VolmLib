package art.arcane.volmit.packaging;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

final class NativeProviderBindings {
    private static final String ANNOTATION_SUFFIX = "/nativelib/NativeBinding;";
    private static final Pattern VERSION = Pattern.compile("v[0-9]+(?:_[0-9]+)*_R[0-9]+");

    private NativeProviderBindings() {
    }

    static Map<String, Set<String>> providers(File artifact) throws IOException {
        Set<String> classes = new LinkedHashSet<>();
        Map<String, Binding> bindings = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(artifact)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                ClassReader reader;
                try (InputStream input = jar.getInputStream(entry)) {
                    reader = new ClassReader(input);
                }
                String name = reader.getClassName();
                classes.add(name);
                reader.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        if (!descriptor.endsWith(ANNOTATION_SUFFIX)) {
                            return null;
                        }
                        String root = descriptor.substring(1, descriptor.length() - "NativeBinding;".length());
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override
                            public void visit(String member, Object value) {
                                if (member.equals("value") && value instanceof String suffix) {
                                    bindings.put(name, new Binding(root, suffix.replace('.', '/')));
                                }
                            }
                        };
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        Map<String, Set<String>> providers = new LinkedHashMap<>();
        for (Map.Entry<String, Binding> entry : bindings.entrySet()) {
            Binding binding = entry.getValue();
            Set<String> implementations = new LinkedHashSet<>();
            for (String name : classes) {
                String suffix = "/" + binding.suffix();
                if (!name.startsWith(binding.root()) || !name.endsWith(suffix)
                        || name.length() <= binding.root().length() + suffix.length()) {
                    continue;
                }
                String version = name.substring(binding.root().length(), name.length() - suffix.length());
                if (VERSION.matcher(version).matches()) {
                    implementations.add(name);
                }
            }
            if (!implementations.isEmpty()) {
                providers.put(entry.getKey(), implementations);
            }
        }
        return providers;
    }

    private record Binding(String root, String suffix) {
    }
}

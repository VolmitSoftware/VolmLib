package art.arcane.volmit.packaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JarReachabilityTest {
    @TempDir
    Path temporary;

    @Test
    void retainsTransitiveDescriptorsInheritanceAndOwnedClasses() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        ClassWriter consumer = writer("plugin/Main", "java/lang/Object");
        consumer.visitField(Opcodes.ACC_PUBLIC, "dependency", "Llib/Used;", null, null).visitEnd();
        add(entries, "plugin/Main", consumer);
        add(entries, "plugin/Unused", writer("plugin/Unused", "java/lang/Object"));
        ClassWriter used = writer("lib/Used", "lib/Parent");
        used.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "use", "([Llib/Argument;)Llib/Return;", null, null).visitEnd();
        add(entries, "lib/Used", used);
        addEmpty(entries, "lib/Parent", "lib/Argument", "lib/Return", "lib/Dead");

        assertEquals(Set.of("lib/Dead.class"), unused(entries, List.of()));
    }

    @Test
    void retainsExactReflectiveNamesAndRecursiveNestedFamily() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        ClassWriter consumer = writer("plugin/Main", "java/lang/Object");
        MethodVisitor method = consumer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "use", "()V", null, null);
        method.visitCode();
        method.visitLdcInsn("lib.Reflected");
        method.visitInsn(Opcodes.POP);
        method.visitLdcInsn("lib/Internal");
        method.visitInsn(Opcodes.POP);
        method.visitLdcInsn("[[Llib.ArrayComponent;");
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        add(entries, "plugin/Main", consumer);
        addEmpty(entries, "lib/Reflected", "lib/Reflected$Nested", "lib/Reflected$Nested$Deep",
                "lib/Internal", "lib/ArrayComponent", "lib/Dead");

        assertEquals(Set.of("lib/Dead.class"), unused(entries, List.of()));
    }

    @Test
    void retainsServiceProvidersAndConfiguredFamilies() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("META-INF/services/lib.Service", "# Providers\nlib.Provider # enabled\n".getBytes(StandardCharsets.UTF_8));
        entries.put("settings.yml", "any resource remains".getBytes(StandardCharsets.UTF_8));
        addEmpty(entries, "lib/Service", "lib/Provider", "lib/Generated", "lib/Generated$Nested", "lib/Dead");

        assertEquals(Set.of("lib/Dead.class"), unused(entries, List.of("lib/Generated")));
    }

    @Test
    void retainsGenericSignaturesAnnotationsAndDynamicConstants() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        ClassWriter consumer = writer("plugin/Main", "java/lang/Object");
        consumer.visitField(Opcodes.ACC_PUBLIC, "types", "Ljava/util/List;", "Ljava/util/List<Llib/Generic;>;", null).visitEnd();
        AnnotationVisitor annotation = consumer.visitAnnotation("Llib/Annotation;", true);
        annotation.visit("type", Type.getObjectType("lib/AnnotationType"));
        annotation.visitEnd();
        MethodVisitor method = consumer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "use", "()V", null, null);
        method.visitCode();
        method.visitLdcInsn(new ConstantDynamic("value", "Llib/DynamicValue;",
                new Handle(Opcodes.H_INVOKESTATIC, "lib/Bootstrap", "bootstrap",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;", false),
                Type.getObjectType("lib/BootstrapArgument")));
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        add(entries, "plugin/Main", consumer);
        addEmpty(entries, "lib/Generic", "lib/Annotation", "lib/AnnotationType", "lib/DynamicValue",
                "lib/Bootstrap", "lib/BootstrapArgument", "lib/Dead");

        assertEquals(Set.of("lib/Dead.class"), unused(entries, List.of()));
    }

    @Test
    void retainsClassConstantsLeftByInlinedFields() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        ClassWriter consumer = writer("plugin/Main", "java/lang/Object");
        consumer.newClass("lib/InlinedConstants");
        add(entries, "plugin/Main", consumer);
        addEmpty(entries, "lib/InlinedConstants", "lib/Dead");

        assertEquals(Set.of("lib/Dead.class"), unused(entries, List.of()));
    }

    @Test
    void retainsEveryReleaseOfReferencedClass() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        ClassWriter consumer = writer("plugin/Main", "lib/Base");
        add(entries, "plugin/Main", consumer);
        addEmpty(entries, "lib/Base", "lib/VersionDependency", "lib/Dead");
        ClassWriter version = writer("lib/Base", "lib/VersionDependency");
        version.visitEnd();
        entries.put("META-INF/versions/25/lib/Base.class", version.toByteArray());

        assertEquals(Set.of("lib/Dead.class"), unused(entries, List.of()));
    }

    private Set<String> unused(Map<String, byte[]> entries, List<String> keepPrefixes) throws IOException {
        Path artifact = temporary.resolve("plugin.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(artifact))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return JarReachability.unusedClasses(artifact.toFile(), List.of("lib/"), keepPrefixes);
    }

    private static void addEmpty(Map<String, byte[]> entries, String... names) {
        for (String name : names) {
            add(entries, name, writer(name, "java/lang/Object"));
        }
    }

    private static void add(Map<String, byte[]> entries, String name, ClassWriter writer) {
        writer.visitEnd();
        entries.put(name + ".class", writer.toByteArray());
    }

    private static ClassWriter writer(String name, String parent) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V25, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, name, null, parent, null);
        return writer;
    }
}

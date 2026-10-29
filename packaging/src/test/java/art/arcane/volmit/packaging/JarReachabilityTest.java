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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JarReachabilityTest {
    private static final String VOLMLIB = "art/arcane/volmlib/";
    private static final List<String> NATIVE_LIBRARIES = List.of(
            "linux-aarch64/libvolmlib_noise.so", "linux-x86_64/libvolmlib_noise.so",
            "macos-aarch64/libvolmlib_noise.dylib", "macos-x86_64/libvolmlib_noise.dylib",
            "windows-x86_64/volmlib_noise.dll");

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
    void retainsExternalRuntimeRootsAndTheirDependencies() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("META-INF/volmit/runtime-roots.list", "lib/ExternalApi\n".getBytes(StandardCharsets.UTF_8));
        add(entries, "lib/ExternalApi", writer("lib/ExternalApi", "lib/Parent"));
        addEmpty(entries, "lib/Parent", "lib/Dead");
        assertEquals(Set.of("lib/Dead.class"), unused(entries, List.of()));
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

    @Test
    void retainsRelocatedVersionProvidersOnlyForReachableCapabilities() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        ClassWriter consumer = writer("plugin/Main", "java/lang/Object");
        consumer.visitField(Opcodes.ACC_PUBLIC, "capability", "Llib/nativelib/terrain/Access;", null, null).visitEnd();
        add(entries, "plugin/Main", consumer);
        for (String capability : List.of("Access", "Unused")) {
            ClassWriter contract = writer("lib/nativelib/terrain/" + capability, "java/lang/Object");
            AnnotationVisitor binding = contract.visitAnnotation("Llib/nativelib/NativeBinding;", true);
            binding.visit("value", "terrain.Native" + capability);
            binding.visitEnd();
            add(entries, "lib/nativelib/terrain/" + capability, contract);
        }
        add(entries, "lib/nativelib/v26_2_R1/terrain/NativeAccess",
                writer("lib/nativelib/v26_2_R1/terrain/NativeAccess", "lib/SharedAccess"));
        addEmpty(entries, "lib/nativelib/NativeBinding", "lib/SharedAccess",
                "lib/nativelib/v26_3_R1/terrain/NativeAccess",
                "lib/nativelib/v26_2_R1/terrain/NativeUnused",
                "lib/nativelib/common/terrain/NativeAccess", "lib/nativelib/terrain/NativeAccess");

        assertEquals(Set.of("lib/nativelib/terrain/Unused.class",
                "lib/nativelib/v26_2_R1/terrain/NativeUnused.class",
                "lib/nativelib/common/terrain/NativeAccess.class", "lib/nativelib/terrain/NativeAccess.class"), unused(entries, List.of()));
    }

    @Test
    void dropsNativeNoiseLibrariesWhenTheLoaderIsPruned() throws IOException {
        Map<String, byte[]> entries = nativeNoise("lib/");
        add(entries, "plugin/Main", writer("plugin/Main", "java/lang/Object"));
        Path artifact = write(entries);

        Set<String> unusedClasses = JarReachability.unusedClasses(artifact.toFile(), List.of("lib/"), List.of());
        assertEquals(Set.of("lib/util/noise/NativeNoiseLibrary.class"), unusedClasses);
        assertEquals(nativeLibraries("lib/"), JarReachability.unusedNativeNoise(artifact.toFile(), unusedClasses));
    }

    @Test
    void keepsNativeNoiseLibrariesWhileTheLoaderIsReachable() throws IOException {
        Map<String, byte[]> entries = nativeNoise("lib/");
        ClassWriter consumer = writer("plugin/Main", "java/lang/Object");
        consumer.visitField(Opcodes.ACC_PUBLIC, "noise", "Llib/util/noise/NativeNoiseLibrary;", null, null).visitEnd();
        add(entries, "plugin/Main", consumer);
        Path artifact = write(entries);

        Set<String> unusedClasses = JarReachability.unusedClasses(artifact.toFile(), List.of("lib/"), List.of());
        assertEquals(Set.of(), unusedClasses);
        assertEquals(Set.of(), JarReachability.unusedNativeNoise(artifact.toFile(), unusedClasses));
    }

    @Test
    void matchesNativeNoiseLibrariesToTheirRelocatedLoader() throws IOException {
        Map<String, byte[]> entries = nativeNoise("plugin/libs/volmlib/");
        entries.putAll(nativeNoise("lib/"));
        entries.remove("lib/util/noise/NativeNoiseLibrary.class");
        Path artifact = write(entries);

        assertEquals(nativeLibraries("lib/"), JarReachability.unusedNativeNoise(artifact.toFile(), Set.of()));
    }

    @Test
    void removesOrphanedNativeNoiseLibrariesWithoutClassPruning() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        addEmpty(entries, "plugin/Main");
        addNativeLibraries(entries, VOLMLIB);
        entries.put(VOLMLIB + "native/LICENSE", new byte[]{1, 2, 3});
        entries.put(VOLMLIB + "native/linux-x86_64/other.so", new byte[]{4, 5, 6});
        entries.put("other/native/linux-x86_64/libvolmlib_noise.so", new byte[]{7, 8, 9});
        entries.put("plugin.yml", "name: Example\nmain: plugin.Main\n".getBytes(StandardCharsets.UTF_8));
        Path artifact = write(entries);

        Set<String> removed = unusedEntries(artifact, List.of(), List.of());

        Set<String> expected = nativeLibraries(VOLMLIB);
        expected.add("other/native/linux-x86_64/libvolmlib_noise.so");
        assertEquals(expected, removed);
        assertCompactedEntries(artifact, entries, removed);
    }

    @Test
    void removesNativeNoiseLibrariesWhenTheirLoaderIsPruned() throws IOException {
        for (String prefix : List.of(VOLMLIB, "plugin/internal/volmlib/")) {
            Map<String, byte[]> entries = new LinkedHashMap<>();
            addEmpty(entries, "plugin/Main", prefix + "util/noise/NativeNoiseLibrary");
            addNativeLibraries(entries, prefix);
            Path artifact = write(entries);

            Set<String> removed = unusedEntries(artifact, List.of(prefix), List.of());

            Set<String> expected = nativeLibraries(prefix);
            expected.add(prefix + "util/noise/NativeNoiseLibrary.class");
            assertEquals(expected, removed);
            assertCompactedEntries(artifact, entries, removed);
        }
    }

    @Test
    void retainsNativeNoiseBytesWithReachableRelocatedAndMultiReleaseLoaders() throws IOException {
        for (String prefix : List.of(VOLMLIB, "plugin/internal/volmlib/")) {
            for (String version : List.of("", "META-INF/versions/25/")) {
                Map<String, byte[]> entries = new LinkedHashMap<>();
                String owner = prefix + "util/noise/NativeNoiseLibrary";
                add(entries, "plugin/Main", writer("plugin/Main", owner));
                ClassWriter loader = writer(owner, "java/lang/Object");
                loader.visitEnd();
                entries.put(version + owner + ".class", loader.toByteArray());
                addNativeLibraries(entries, VOLMLIB);
                addNativeLibraries(entries, prefix);
                Path artifact = write(entries);

                Set<String> removed = unusedEntries(artifact, List.of(prefix), List.of());

                Set<String> expected = prefix.equals(VOLMLIB) ? Set.of() : nativeLibraries(VOLMLIB);
                assertEquals(expected, removed);
                assertCompactedEntries(artifact, entries, removed);
            }
        }
    }

    @Test
    void retainsNativeNoiseLibrariesWithExplicitlyKeptLoaders() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        String owner = VOLMLIB + "util/noise/NativeNoiseLibrary";
        addEmpty(entries, "plugin/Main", owner);
        addNativeLibraries(entries, VOLMLIB);
        Path artifact = write(entries);

        Set<String> removed = unusedEntries(artifact, List.of(VOLMLIB), List.of(owner));

        assertEquals(Set.of(), removed);
        assertCompactedEntries(artifact, entries, removed);
    }

    @Test
    void retainsNativeNoiseLibrariesWithoutClassPruning() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        addEmpty(entries, "plugin/Main", VOLMLIB + "util/noise/NativeNoiseLibrary", VOLMLIB + "Unused");
        addNativeLibraries(entries, VOLMLIB);
        Path artifact = write(entries);

        Set<String> removed = unusedEntries(artifact, List.of(), List.of());

        assertEquals(Set.of(), removed);
        assertCompactedEntries(artifact, entries, removed);
    }

    private Set<String> unused(Map<String, byte[]> entries, List<String> keepPrefixes) throws IOException {
        return JarReachability.unusedClasses(write(entries).toFile(), List.of("lib/"), keepPrefixes);
    }

    private static Set<String> unusedEntries(Path artifact, List<String> prunePrefixes, List<String> keepPrefixes)
            throws IOException {
        Set<String> unusedClasses = JarReachability.unusedClasses(artifact.toFile(), prunePrefixes, keepPrefixes);
        Set<String> removed = new HashSet<>(unusedClasses);
        removed.addAll(JarReachability.unusedNativeNoise(artifact.toFile(), unusedClasses));
        return removed;
    }

    private Path write(Map<String, byte[]> entries) throws IOException {
        Path artifact = temporary.resolve("plugin.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(artifact))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return artifact;
    }

    private void assertCompactedEntries(Path artifact, Map<String, byte[]> expected, Set<String> removed)
            throws IOException {
        JarCompactor.compact(artifact.toFile(), new JarCompactor.CompactionOptions(
                removed, true, false, false, temporary.resolve("compression-cache")));
        try (JarFile jar = new JarFile(artifact.toFile())) {
            for (Map.Entry<String, byte[]> entry : expected.entrySet()) {
                if (removed.contains(entry.getKey())) {
                    assertNull(jar.getJarEntry(entry.getKey()), entry.getKey());
                } else {
                    try (InputStream input = jar.getInputStream(jar.getJarEntry(entry.getKey()))) {
                        assertArrayEquals(entry.getValue(), input.readAllBytes(), entry.getKey());
                    }
                }
            }
        }
    }

    private static Map<String, byte[]> nativeNoise(String base) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        addEmpty(entries, base + "util/noise/NativeNoiseLibrary");
        addNativeLibraries(entries, base);
        return entries;
    }

    private static Set<String> nativeLibraries(String base) {
        Set<String> libraries = new HashSet<>();
        for (String name : NATIVE_LIBRARIES) {
            libraries.add(base + "native/" + name);
        }
        return libraries;
    }

    private static void addNativeLibraries(Map<String, byte[]> entries, String base) {
        for (String name : NATIVE_LIBRARIES) {
            entries.put(base + "native/" + name, name.repeat(20).getBytes(StandardCharsets.UTF_8));
        }
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

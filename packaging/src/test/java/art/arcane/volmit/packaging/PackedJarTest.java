package art.arcane.volmit.packaging;

import org.junit.jupiter.api.Test;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.tukaani.xz.XZInputStream;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PackedJarTest {
    @TempDir
    Path directory;

    @Test
    void gradlePluginSelectsPortableRuntimeAndEnforcesFinalBudget() throws Exception {
        Files.writeString(directory.resolve("settings.gradle"), "rootProject.name = 'packed-fixture'\n");
        Files.writeString(directory.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'art.arcane.volmit-packaging'
                }
                pluginPackaging {
                    artifacts {
                        distribution {
                            taskName = 'jar'
                            maximumBytes = 7500000
                            shrink = false
                            releaseCompression = false
                            packed = true
                            requiredEntries = ['plugin.yml', 'test/Main.class']
                        }
                    }
                }
                afterEvaluate {
                    tasks.named('packedJar') {
                        if (providers.gradleProperty('packedRelease').isPresent()) {
                            releaseCompression = providers.gradleProperty('packedRelease').map { it.toBoolean() }
                        }
                    }
                }
                """);
        Path java = directory.resolve("src/main/java/test/Main.java");
        Path descriptor = directory.resolve("src/main/resources/plugin.yml");
        Path guide = directory.resolve("src/main/resources/guides/example.md");
        Files.createDirectories(java.getParent());
        Files.createDirectories(descriptor.getParent());
        Files.createDirectories(guide.getParent());
        StringBuilder sources = new StringBuilder("package test; public class Main {}\n");
        for (int index = 0; index < 200; index++) {
            sources.append("class Deferred").append(index).append(" { static String value = \"")
                    .append(sharedConstant()).append("\"; }\n");
        }
        Files.writeString(java, "package test; public class Main {}\n");
        Files.writeString(descriptor, "name: Fixture\nmain: test.Main\n");
        Files.writeString(guide, "Guide contents\n");
        GradleRunner runner = GradleRunner.create().withProjectDir(directory.toFile()).withPluginClasspath()
                .withArguments("packedJar", "--max-workers=1", "--stacktrace");
        runner.build();
        assertArrayEquals(Files.readAllBytes(directory.resolve("build/tmp/packaging/distribution/packed-fixture.jar")),
                Files.readAllBytes(directory.resolve("build/libs/packed-fixture.jar")));
        try (Stream<Path> libraries = Files.list(directory.resolve("build/libs"))) {
            assertEquals(List.of("packed-fixture.jar"), libraries.map(path -> path.getFileName().toString()).toList());
        }
        Files.writeString(java, sources);
        runner.build();
        Map<String, byte[]> outer = PackedJar.readJar(directory.resolve("build/libs/packed-fixture.jar"));
        assertTrue(outer.containsKey("test/Main/xz/PackedRuntime.class"));
        assertTrue(outer.containsKey("test/Main/xz/decoder/XZInputStream.class"));
        for (Map.Entry<String, byte[]> entry : outer.entrySet()) {
            if (entry.getKey().endsWith(".class")) {
                assertFalse(new String(entry.getValue(), StandardCharsets.ISO_8859_1).contains("org/gradle/"),
                        entry.getKey());
            }
        }
        long ordinaryBytes = Files.size(directory.resolve("build/tmp/packaging/distribution/packed-fixture.jar"));
        long selectedBytes = Files.size(directory.resolve("build/libs/packed-fixture.jar"));
        assertTrue(selectedBytes < ordinaryBytes);
        Path buildScript = directory.resolve("build.gradle");
        String build = Files.readString(buildScript);
        assertTrue(outer.containsKey("guides/example.md"));
        assertEquals(TaskOutcome.UP_TO_DATE, runner.build().task(":packedJar").getOutcome());
        String filteredBuild = build.replace("packed = true",
                "packed = true; packedPayloadOnlyPrefixes = ['guides/']");
        Files.writeString(buildScript, filteredBuild);
        assertEquals(TaskOutcome.SUCCESS, runner.build().task(":packedJar").getOutcome());
        Map<String, byte[]> filtered = PackedJar.readJar(directory.resolve("build/libs/packed-fixture.jar"));
        assertFalse(filtered.containsKey("guides/example.md"));
        assertArrayEquals(outer.get(PackedJar.PAYLOAD), filtered.get(PackedJar.PAYLOAD));
        assertArrayEquals(outer.get(PackedJar.METADATA), filtered.get(PackedJar.METADATA));
        assertArrayEquals(Files.readAllBytes(guide), payload(filtered).get("guides/example.md"));
        assertEquals(TaskOutcome.UP_TO_DATE, runner.build().task(":packedJar").getOutcome());
        byte[] ordinary = Files.readAllBytes(directory.resolve("build/tmp/packaging/distribution/packed-fixture.jar"));
        long filteredBytes = Files.size(directory.resolve("build/libs/packed-fixture.jar"));
        runner.withArguments("packedJar", "-PpackedRelease=true", "--max-workers=1", "--stacktrace");
        BuildResult releaseBuild = runner.build();
        assertEquals(TaskOutcome.UP_TO_DATE, releaseBuild.task(":jar").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, releaseBuild.task(":packedJar").getOutcome());
        assertArrayEquals(ordinary, Files.readAllBytes(directory.resolve("build/tmp/packaging/distribution/packed-fixture.jar")));
        assertTrue(Files.size(directory.resolve("build/libs/packed-fixture.jar")) <= filteredBytes);
        Map<String, byte[]> optimized = PackedJar.readJar(directory.resolve("build/libs/packed-fixture.jar"));
        assertArrayEquals(filtered.get(PackedJar.METADATA), optimized.get(PackedJar.METADATA));
        assertEquals(TaskOutcome.UP_TO_DATE, runner.build().task(":packedJar").getOutcome());
        long intermediateBudget = (ordinaryBytes + selectedBytes) / 2;
        Files.writeString(buildScript, build.replace("maximumBytes = 7500000", "maximumBytes = " + intermediateBudget));
        runner.withArguments("verifyPluginJars", "--max-workers=1", "--stacktrace").build();
        assertTrue(Files.size(directory.resolve("build/libs/packed-fixture.jar")) <= intermediateBudget);
        Files.writeString(buildScript, build.replace("maximumBytes = 7500000", "maximumBytes = " + (selectedBytes - 1)));
        assertTrue(runner.buildAndFail().getOutput().contains("Selected jar exceeds"));
        Files.writeString(buildScript, build.replace("maximumBytes = 7500000", "modded = true"));
        runner.build();
        assertEquals(selectedBytes, Files.size(directory.resolve("build/libs/packed-fixture.jar")));
        Files.writeString(buildScript, build.replace("maximumBytes = 7500000", "modded = true; maximumBytes = 1"));
        runner.build();
        assertEquals(selectedBytes, Files.size(directory.resolve("build/libs/packed-fixture.jar")));
    }

    @Test
    void releaseCompressionPreservesRuntimeAndOuterEntriesWhileSelectingSmallerBytes() throws Exception {
        Map<String, byte[]> contents = simplePlugin("ReleaseCompression");
        addCompressibleClasses(contents);
        contents.put("guides/reference.md", sharedConstant().repeat(20).getBytes(StandardCharsets.UTF_8));
        Path source = directory.resolve("source.jar");
        byte[] sourceBytes = PackedJar.writeJar(contents, false);
        Files.write(source, sourceBytes);
        Map<String, byte[]> bootstrap = PackedJar.bootstrapClasses();
        Path standard = directory.resolve("standard.jar");
        Path optimized = directory.resolve("optimized.jar");
        Path cached = directory.resolve("cached.jar");
        Path cache = directory.resolve("compression-cache");
        PackedJar.PackOptions standardOptions = new PackedJar.PackOptions(List.of(), List.of(),
                PackedJar.NativeOptions.empty(), PackagingArtifact.SPIGOT_CAP_BYTES, cache,
                List.of(), List.of(), false);
        PackedJar.PackOptions releaseOptions = new PackedJar.PackOptions(List.of(), List.of(),
                PackedJar.NativeOptions.empty(), PackagingArtifact.SPIGOT_CAP_BYTES, cache,
                List.of(), List.of(), true);

        assertTrue(PackedJar.writePackedJar(source, standard, bootstrap, standardOptions).compressed());
        assertFalse(Files.exists(cache));
        assertTrue(PackedJar.writePackedJar(source, optimized, bootstrap, releaseOptions).compressed());
        assertTrue(Files.size(optimized) < Files.size(standard));
        Map<String, byte[]> standardEntries = PackedJar.readJar(standard);
        Map<String, byte[]> optimizedEntries = PackedJar.readJar(optimized);
        assertEquals(standardEntries.keySet(), optimizedEntries.keySet());
        for (String name : standardEntries.keySet()) {
            if (!name.equals(PackedJar.PAYLOAD)) {
                assertArrayEquals(standardEntries.get(name), optimizedEntries.get(name), name);
            }
        }
        assertTrue(optimizedEntries.get(PackedJar.PAYLOAD).length <= standardEntries.get(PackedJar.PAYLOAD).length);
        try (XZInputStream original = new XZInputStream(
                new ByteArrayInputStream(standardEntries.get(PackedJar.PAYLOAD)), 128 * 1024);
             XZInputStream released = new XZInputStream(
                     new ByteArrayInputStream(optimizedEntries.get(PackedJar.PAYLOAD)), 128 * 1024)) {
            assertArrayEquals(original.readAllBytes(), released.readAllBytes());
        }
        try (ZipFile archive = new ZipFile(optimized.toFile())) {
            assertEquals(ZipEntry.STORED, archive.getEntry(PackedJar.PAYLOAD).getMethod());
            assertEquals(ZipEntry.DEFLATED, archive.getEntry("guides/reference.md").getMethod());
            assertEquals(0L, archive.getEntry(PackedJar.PAYLOAD).getTime());
            assertEquals(0L, archive.getEntry("guides/reference.md").getTime());
        }
        try (Stream<Path> files = Files.walk(cache)) {
            assertTrue(files.anyMatch(path -> path.getFileName().toString().endsWith(".deflate")));
        }
        PackedJar.writePackedJar(source, cached, bootstrap, releaseOptions);
        assertArrayEquals(Files.readAllBytes(optimized), Files.readAllBytes(cached));
        assertArrayEquals(sourceBytes, Files.readAllBytes(source));
    }

    @Test
    void packsUniversalEntrypointsAndSuperclassBeforeInitialization() throws Exception {
        Map<String, byte[]> contents = new TreeMap<>();
        contents.put("plugin.yml", "name: TestPlugin\nmain: test.bukkit.Main\n".getBytes(StandardCharsets.UTF_8));
        contents.put("velocity-plugin.json", "{\"name\":\"testplugin\",\"main\":\"test.proxy.Main\"}".getBytes(StandardCharsets.UTF_8));
        contents.put("paper-plugin.yml", "name: TestPlugin\nmain: test.bukkit.Main\nbootstrapper: test.Bootstrap\nloader: test.Loader\n".getBytes(StandardCharsets.UTF_8));
        contents.put("test/bukkit/Main.class", emptyClass("test/bukkit/Main", "test/Base"));
        contents.put("test/Base.class", emptyClass("test/Base", "java/lang/Object"));
        contents.put("test/proxy/Main.class", emptyClass("test/proxy/Main", "java/lang/Object"));
        contents.put("test/Bootstrap.class", emptyClass("test/Bootstrap", "java/lang/Object"));
        contents.put("test/Loader.class", emptyClass("test/Loader", "java/lang/Object"));
        contents.put("test/Deferred.class", emptyClass("test/Deferred", "java/lang/Object"));
        contents.put("config.yml", "value: 1\n".getBytes(StandardCharsets.UTF_8));
        addCompressibleClasses(contents);
        contents.put("unrelated/slimjar/Unused.class", emptyClass("unrelated/slimjar/Unused", "java/lang/Object"));
        Path original = directory.resolve("original.jar");
        Path packed = directory.resolve("packed.jar");
        byte[] originalBytes = PackedJar.writeJar(contents, false);
        Files.write(original, originalBytes);

        PackedJar.writePackedJar(original, packed, PackedJar.bootstrapClasses(),
                new PackedJar.PackOptions(List.of(), List.of(), PackedJar.NativeOptions.empty(),
                        PackagingArtifact.SPIGOT_CAP_BYTES, directory.resolve("compression-cache"), List.of(), List.of(), false));

        assertArrayEquals(originalBytes, Files.readAllBytes(original));
        Map<String, byte[]> outer = PackedJar.readJar(packed);
        Map<String, byte[]> payload = payload(outer);
        Properties metadata = new Properties();
        metadata.load(new ByteArrayInputStream(outer.get(PackedJar.METADATA)));
        assertEquals("TestPlugin", metadata.getProperty("pluginName"));
        try (XZInputStream input = new XZInputStream(new ByteArrayInputStream(outer.get(PackedJar.PAYLOAD)))) {
            assertEquals(metadata.getProperty("sha256"),
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())));
        }
        assertFalse(outer.containsKey("test/Deferred.class"));
        assertTrue(payload.containsKey("test/Deferred.class"));
        assertArrayEquals(contents.get("config.yml"), outer.get("config.yml"));
        for (String root : List.of("test/bukkit/Main", "test/Base", "test/proxy/Main", "test/Bootstrap", "test/Loader")) {
            assertTrue(calls(outer.get(root + ".class")).contains("test/bukkit/Main/xz/PackedRuntime#install"));
        }
        for (Map.Entry<String, byte[]> entry : outer.entrySet()) {
            if (entry.getKey().startsWith("test/bukkit/Main/xz/")) {
                assertArrayEquals(entry.getValue(), payload.get(entry.getKey()));
            }
            assertFalse(entry.getKey().startsWith(PackedJar.TEMPLATE_PACKAGE));
            assertFalse(entry.getKey().startsWith("org/tukaani/xz/"));
        }
        assertTrue(outer.containsKey("test/bukkit/Main/xz/PackedRuntime$RuntimeArchive.class"));
        assertTrue(outer.containsKey("test/bukkit/Main/xz/decoder/XZInputStream.class"));
        assertFalse(outer.containsKey("test/bukkit/Main/xz/decoder/XZOutputStream.class"));
        assertFalse(outer.containsKey("unrelated/slimjar/Unused.class"));
        assertTrue(payload.containsKey("unrelated/slimjar/Unused.class"));
        assertTrue(Files.size(packed) < Files.size(original));
    }

    @Test
    void xzDistributionIsIdenticalAcrossIntermediateZipCompressionModes() throws Exception {
        Map<String, byte[]> contents = simplePlugin("Compression");
        addCompressibleClasses(contents);
        Path standard = directory.resolve("standard.jar");
        Path compact = directory.resolve("compact.jar");
        byte[] original = PackedJar.writeJar(contents, false);
        Files.write(standard, original);
        Files.write(compact, original);
        JarCompactor.compact(standard.toFile(), new JarCompactor.CompactionOptions(
                Set.of(), true, true, false, directory.resolve("compression-cache")));
        JarCompactor.compact(compact.toFile(), new JarCompactor.CompactionOptions(
                Set.of(), true, true, true, directory.resolve("compression-cache")));
        Map<String, byte[]> standardContents = PackedJar.readJar(standard);
        Map<String, byte[]> compactContents = PackedJar.readJar(compact);
        assertEquals(standardContents.keySet(), compactContents.keySet());
        for (String name : standardContents.keySet()) {
            assertArrayEquals(standardContents.get(name), compactContents.get(name), name);
        }

        Map<String, byte[]> bootstrap = PackedJar.bootstrapClasses();
        PackedJar.PackOptions options = new PackedJar.PackOptions(List.of(), List.of(),
                PackedJar.NativeOptions.empty(), PackagingArtifact.SPIGOT_CAP_BYTES, directory.resolve("fallback-cache"),
                List.of(), List.of(), false);
        Path standardPacked = directory.resolve("standard-packed.jar");
        Path compactPacked = directory.resolve("compact-packed.jar");
        assertTrue(PackedJar.writePackedJar(standard, standardPacked, bootstrap, options).compressed());
        assertTrue(PackedJar.writePackedJar(compact, compactPacked, bootstrap, options).compressed());
        assertArrayEquals(Files.readAllBytes(standardPacked), Files.readAllBytes(compactPacked));
        assertFalse(Files.exists(directory.resolve("fallback-cache")));
        Map<String, byte[]> outer = PackedJar.readJar(standardPacked);
        Map<String, byte[]> payload = payload(outer);
        for (String name : contents.keySet()) {
            if (!name.equals("test/Main.class")) {
                assertArrayEquals(contents.get(name), payload.get(name), name);
            }
        }
        try (ZipInputStream archive = new ZipInputStream(new XZInputStream(
                new ByteArrayInputStream(outer.get(PackedJar.PAYLOAD))))) {
            for (ZipEntry entry = archive.getNextEntry(); entry != null; entry = archive.getNextEntry()) {
                assertEquals(ZipEntry.STORED, entry.getMethod(), entry.getName());
            }
        }

        long rejectedBudget = Files.size(standardPacked) - 1;
        for (Path source : List.of(standard, compact)) {
            Path rejected = directory.resolve(source.getFileName() + "-rejected.jar");
            IOException failure = assertThrows(IOException.class, () -> PackedJar.writePackedJar(source, rejected,
                    bootstrap, new PackedJar.PackOptions(List.of(), List.of(),
                            PackedJar.NativeOptions.empty(), rejectedBudget, directory.resolve("compression-cache"),
                            List.of(), List.of(), false)));
            assertTrue(failure.getMessage().contains("Selected jar exceeds " + rejectedBudget));
            assertFalse(Files.exists(rejected));
        }
    }

    @Test
    void nativeProvidersRemainInsideVerifiedPayload() throws Exception {
        Map<String, byte[]> contents = simplePlugin("Native");
        addCompressibleClasses(contents);
        contents.put("META-INF/iris/native-runtime.properties", "modules=native-common,native-v26_2_R1\n"
                .getBytes(StandardCharsets.UTF_8));
        Path common = directory.resolve("common.jar");
        Path version = directory.resolve("version.jar");
        Files.write(common, PackedJar.writeJar(Map.of("common.txt", new byte[]{1}), false));
        Files.write(version, PackedJar.writeJar(Map.of("version.txt", new byte[]{2}), false));
        Path original = directory.resolve("original.jar");
        Path packed = directory.resolve("packed.jar");
        Files.write(original, PackedJar.writeJar(contents, false));

        PackedJar.writePackedJar(original, packed, PackedJar.bootstrapClasses(), new PackedJar.PackOptions(
                List.of(), List.of(), new PackedJar.NativeOptions(
                        Map.of("native-common", common.toFile(), "native-v26_2_R1", version.toFile()),
                        "META-INF/iris/native-runtime.properties", "META-INF/iris/native/"),
                PackagingArtifact.SPIGOT_CAP_BYTES, directory.resolve("compression-cache"), List.of(), List.of(), false));

        Map<String, byte[]> outer = PackedJar.readJar(packed);
        Map<String, byte[]> payload = payload(outer);
        assertFalse(outer.containsKey("META-INF/iris/native/native-common.jar"));
        assertArrayEquals(Files.readAllBytes(common), payload.get("META-INF/iris/native/native-common.jar"));
        Properties manifest = new Properties();
        manifest.load(new ByteArrayInputStream(payload.get("META-INF/iris/native-runtime.properties")));
        assertEquals("embedded", manifest.getProperty("storage"));
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(version))),
                manifest.getProperty("native-v26_2_R1.sha256"));
    }

    @Test
    void sharedNoiseLibrariesShipOnlyInsideThePayload() throws Exception {
        Map<String, byte[]> contents = simplePlugin("NoiseLibraries");
        addCompressibleClasses(contents);
        byte[] library = new byte[4096];
        new Random(7L).nextBytes(library);
        contents.put("art/arcane/volmlib/native/linux-x86_64/libvolmlib_noise.so", library);
        Path original = directory.resolve("original.jar");
        Path packed = directory.resolve("packed.jar");
        Files.write(original, PackedJar.writeJar(contents, false));

        assertTrue(PackedJar.writePackedJar(original, packed, PackedJar.bootstrapClasses(), new PackedJar.PackOptions(
                List.of(), List.of(), PackedJar.NativeOptions.empty(), PackagingArtifact.SPIGOT_CAP_BYTES,
                directory.resolve("compression-cache"), List.of(), List.of(), false)).compressed());

        Map<String, byte[]> outer = PackedJar.readJar(packed);
        assertFalse(outer.containsKey("art/arcane/volmlib/native/linux-x86_64/libvolmlib_noise.so"));
        assertTrue(outer.containsKey("plugin.yml"));
        assertArrayEquals(library, payload(outer).get("art/arcane/volmlib/native/linux-x86_64/libvolmlib_noise.so"));
    }

    @Test
    void payloadOnlyResourcesKeepTheirBytesAndLoadAfterBootstrap() throws Exception {
        Map<String, byte[]> contents = simplePlugin("PayloadResources");
        addCompressibleClasses(contents);
        byte[] library = new byte[4096];
        new Random(71L).nextBytes(library);
        Map<String, byte[]> resources = Map.of(
                "linux/x64/org/lwjgl/libfixture.so", library,
                "iris-language-guides/reference.md", "Language reference\n".getBytes(StandardCharsets.UTF_8));
        contents.putAll(resources);
        contents.put("META-INF/services/test.Service", "test.Main\n".getBytes(StandardCharsets.UTF_8));
        contents.put("META-INF/iris/native-runtime.properties", "modules=\n".getBytes(StandardCharsets.UTF_8));
        contents.put("META-INF/iris/generation-kernels/catalog.tsv", "kernel\tabi\n".getBytes(StandardCharsets.UTF_8));
        contents.put("META-INF/LICENSE", "License contents\n".getBytes(StandardCharsets.UTF_8));
        contents.put("iris-language-source.properties", "revision=example\n".getBytes(StandardCharsets.UTF_8));
        Path original = directory.resolve("original.jar");
        Path packed = directory.resolve("packed.jar");
        Files.write(original, PackedJar.writeJar(contents, false));
        assertTrue(PackedJar.writePackedJar(original, packed, PackedJar.bootstrapClasses(), new PackedJar.PackOptions(
                List.of(), List.of(), PackedJar.NativeOptions.empty(), PackagingArtifact.SPIGOT_CAP_BYTES,
                directory.resolve("compression-cache"), List.of("linux/x64/org/lwjgl/", "iris-language-guides/"),
                List.of("iris-language-source.properties"), true)).compressed());

        Map<String, byte[]> outer = PackedJar.readJar(packed);
        Map<String, byte[]> payload = payload(outer);
        for (String name : contents.keySet()) {
            if (!name.endsWith(".class")) {
                assertArrayEquals(contents.get(name), payload.get(name), name);
                if (resources.containsKey(name)) {
                    assertFalse(outer.containsKey(name), name);
                } else {
                    assertArrayEquals(contents.get(name), outer.get(name), name);
                }
            }
        }
        for (int start = 0; start < 2; start++) {
            try (URLClassLoader loader = new URLClassLoader(new URL[]{packed.toUri().toURL()},
                    ClassLoader.getPlatformClassLoader())) {
                for (String name : resources.keySet()) {
                    assertNull(loader.getResource(name), name);
                }
                Class.forName("test.Main", true, loader);
                assertSame(loader, loader.loadClass("test.data.Deferred0").getClassLoader());
                for (Map.Entry<String, byte[]> entry : resources.entrySet()) {
                    try (InputStream input = loader.getResourceAsStream(entry.getKey())) {
                        assertNotNull(input, entry.getKey());
                        assertArrayEquals(entry.getValue(), input.readAllBytes(), entry.getKey());
                    }
                }
            }
        }
    }

    @Test
    void rejectsPrefixesMatchingClassesAndProtectedResources() throws Exception {
        Map<String, byte[]> contents = simplePlugin("ProtectedResources");
        contents.put("licenses/LICENSE.txt", new byte[]{1});
        contents.put("notices/NOTICE.txt", new byte[]{2});
        contents.put("required/bootstrap.bin", new byte[]{3});
        contents.put("providers/native.properties", new byte[]{4});
        Path original = directory.resolve("original.jar");
        Files.write(original, PackedJar.writeJar(contents, false));
        for (String prefix : List.of("", "/", "../", "guides/../", "guides//", "/guides/", "guides",
                "META-INF/", "META-INF/services/", "META-INF/iris/generation-kernels/", "test/", "test/Main/xz/",
                "licenses/", "notices/", "required/", "providers/")) {
            Path rejected = directory.resolve("rejected.jar");
            IOException failure = assertThrows(IOException.class, () -> PackedJar.writePackedJar(original, rejected,
                    PackedJar.bootstrapClasses(), new PackedJar.PackOptions(List.of(), List.of(),
                            new PackedJar.NativeOptions(Map.of(), "providers/native.properties", "providers/jars/"),
                            PackagingArtifact.SPIGOT_CAP_BYTES, directory.resolve("compression-cache"),
                            List.of(prefix), List.of("required/bootstrap.bin"), false)), prefix);
            assertTrue(failure.getMessage().contains("prefix"), prefix);
            assertFalse(Files.exists(rejected), prefix);
        }
    }

    @Test
    void rejectsMissingEntrypointAndAlreadyPackedArchive() throws Exception {
        Map<String, byte[]> contents = simplePlugin("Invalid");
        contents.remove("test/Main.class");
        assertThrows(IOException.class, () -> PackedJar.descriptor(contents, List.of()));
        Map<String, byte[]> repeated = simplePlugin("Repeated");
        repeated.put(PackedJar.PAYLOAD, new byte[]{0});
        Path original = directory.resolve("original.jar");
        Files.write(original, PackedJar.writeJar(repeated, false));
        assertThrows(IOException.class, () -> PackedJar.writePackedJar(original, directory.resolve("out.jar"),
                Map.of(), new PackedJar.PackOptions(List.of(), List.of(), PackedJar.NativeOptions.empty(),
                        PackagingArtifact.SPIGOT_CAP_BYTES, directory.resolve("compression-cache"), List.of(), List.of(), false)));
    }

    @Test
    void keepsOrdinaryJarByteIdenticalWhenCompressionIsLarger() throws Exception {
        Path original = directory.resolve("original.jar");
        Path output = directory.resolve("selected.jar");
        Map<String, byte[]> contents = simplePlugin("Small");
        contents.put("guides/reference.md", "Complete ordinary guide\n".getBytes(StandardCharsets.UTF_8));
        byte[] bytes = PackedJar.writeJar(contents, false);
        Files.write(original, bytes);
        PackedJar.PackResult result = PackedJar.writePackedJar(original, output, PackedJar.bootstrapClasses(),
                new PackedJar.PackOptions(List.of(), List.of(), PackedJar.NativeOptions.empty(),
                        PackagingArtifact.SPIGOT_CAP_BYTES, directory.resolve("compression-cache"), List.of("guides/"), List.of(), false));
        assertFalse(result.compressed());
        assertArrayEquals(bytes, Files.readAllBytes(output));
        assertArrayEquals(contents.get("guides/reference.md"), PackedJar.readJar(output).get("guides/reference.md"));
        assertFalse(Files.exists(directory.resolve("compression-cache")));
    }

    @Test
    void overBudgetOrdinaryJarUsesVerifiedReleaseCompressionWithoutChangingSource() throws Exception {
        Map<String, byte[]> contents = simplePlugin("CompactBudget");
        contents.put("guides/reference.txt", sharedConstant().repeat(4).getBytes(StandardCharsets.UTF_8));
        Path original = directory.resolve("original.jar");
        Files.write(original, PackedJar.writeJar(contents, false));
        JarCompactor.compact(original.toFile(), new JarCompactor.CompactionOptions(
                Set.of(), false, false, false, directory.resolve("ordinary-cache")));
        byte[] sourceBytes = Files.readAllBytes(original);
        Path expected = directory.resolve("compact.jar");
        Files.copy(original, expected);
        JarCompactor.compact(expected.toFile(), new JarCompactor.CompactionOptions(
                Set.of(), false, false, true, directory.resolve("expected-cache")));
        long budget = Files.size(expected);
        assertTrue(budget < sourceBytes.length);
        Path output = directory.resolve("selected.jar");

        PackedJar.PackResult result = PackedJar.writePackedJar(original, output, PackedJar.bootstrapClasses(),
                new PackedJar.PackOptions(List.of(), List.of(), PackedJar.NativeOptions.empty(),
                        budget, directory.resolve("compression-cache"), List.of("guides/"), List.of(), false));

        assertFalse(result.compressed());
        assertEquals(sourceBytes.length, result.ordinaryBytes());
        assertEquals(budget, Files.size(output));
        assertArrayEquals(Files.readAllBytes(expected), Files.readAllBytes(output));
        assertArrayEquals(sourceBytes, Files.readAllBytes(original));
        Map<String, byte[]> selected = PackedJar.readJar(output);
        assertEquals(contents.keySet(), selected.keySet());
        for (String name : contents.keySet()) {
            assertArrayEquals(contents.get(name), selected.get(name), name);
        }
        assertTrue(Files.isDirectory(directory.resolve("compression-cache")));
    }

    @Test
    void ordinarySelectionKeepsEmbeddedNativeProvidersPortable() throws Exception {
        Map<String, byte[]> contents = simplePlugin("SmallNative");
        contents.put("guides/reference.txt", sharedConstant().repeat(4).getBytes(StandardCharsets.UTF_8));
        contents.put("META-INF/volmit/native-runtime.properties", "modules=native-common\n".getBytes(StandardCharsets.UTF_8));
        Path nativeJar = directory.resolve("native.jar");
        Files.write(nativeJar, PackedJar.writeJar(Map.of("native.txt", new byte[]{1}), false));
        Path original = directory.resolve("original.jar");
        Path output = directory.resolve("selected.jar");
        Files.write(original, PackedJar.writeJar(contents, false));
        PackedJar.PackResult result = PackedJar.writePackedJar(original, output, PackedJar.bootstrapClasses(),
                new PackedJar.PackOptions(List.of(), List.of(), new PackedJar.NativeOptions(
                        Map.of("native-common", nativeJar.toFile()), "META-INF/volmit/native-runtime.properties",
                        "META-INF/volmit/native/"),
                        PackagingArtifact.SPIGOT_CAP_BYTES, directory.resolve("compression-cache"), List.of(), List.of(), false));
        assertFalse(result.compressed());
        Map<String, byte[]> selected = PackedJar.readJar(output);
        assertArrayEquals(Files.readAllBytes(nativeJar), selected.get("META-INF/volmit/native/native-common.jar"));
        assertFalse(selected.containsKey(PackedJar.PAYLOAD));
        assertFalse(calls(selected.get("test/Main.class")).stream().anyMatch(call -> call.endsWith("#install")));
        assertTrue(new String(selected.get("META-INF/volmit/native-runtime.properties"), StandardCharsets.UTF_8)
                .contains("storage=embedded"));

        Path releasedOutput = directory.resolve("released-selected.jar");
        PackedJar.PackResult released = PackedJar.writePackedJar(original, releasedOutput, PackedJar.bootstrapClasses(),
                new PackedJar.PackOptions(List.of(), List.of(), new PackedJar.NativeOptions(
                        Map.of("native-common", nativeJar.toFile()), "META-INF/volmit/native-runtime.properties",
                        "META-INF/volmit/native/"), PackagingArtifact.SPIGOT_CAP_BYTES,
                        directory.resolve("compression-cache"), List.of("guides/"), List.of(), true));
        assertFalse(released.compressed());
        assertTrue(Files.size(releasedOutput) < Files.size(output));
        Map<String, byte[]> releasedContents = PackedJar.readJar(releasedOutput);
        assertEquals(selected.keySet(), releasedContents.keySet());
        for (String name : selected.keySet()) {
            assertArrayEquals(selected.get(name), releasedContents.get(name), name);
        }

        Path compactOutput = directory.resolve("compact-selected.jar");
        byte[] originalBytes = Files.readAllBytes(original);
        long budget = Files.size(output) - 1;
        PackedJar.PackResult compact = PackedJar.writePackedJar(original, compactOutput, PackedJar.bootstrapClasses(),
                new PackedJar.PackOptions(List.of(), List.of(), new PackedJar.NativeOptions(
                        Map.of("native-common", nativeJar.toFile()), "META-INF/volmit/native-runtime.properties",
                        "META-INF/volmit/native/"), budget, directory.resolve("compression-cache"), List.of(), List.of(), false));
        assertFalse(compact.compressed());
        assertTrue(Files.size(compactOutput) <= budget);
        Map<String, byte[]> compactContents = PackedJar.readJar(compactOutput);
        assertEquals(selected.keySet(), compactContents.keySet());
        for (String name : selected.keySet()) {
            assertArrayEquals(selected.get(name), compactContents.get(name), name);
        }
        assertArrayEquals(originalBytes, Files.readAllBytes(original));
    }

    @Test
    void rejectsRuntimeIncompatibleMetadataAndOversizeDistribution() throws Exception {
        assertThrows(IOException.class, () -> PackedJar.descriptor(simplePlugin(".Invalid"), List.of()));
        Path original = directory.resolve("original.jar");
        Path output = directory.resolve("oversize.jar");
        byte[] sourceBytes = PackedJar.writeJar(simplePlugin("Budget"), false);
        Files.write(original, sourceBytes);
        IOException failure = assertThrows(IOException.class, () -> PackedJar.writePackedJar(original, output,
                PackedJar.bootstrapClasses(), new PackedJar.PackOptions(List.of(), List.of(),
                        PackedJar.NativeOptions.empty(), 1L, directory.resolve("compression-cache"), List.of(), List.of(), false)));
        assertTrue(failure.getMessage().contains("byte budget"));
        assertFalse(Files.exists(output));
        assertArrayEquals(sourceBytes, Files.readAllBytes(original));
        try (Stream<Path> files = Files.list(directory)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith("ordinary-")));
        }
    }

    @Test
    void rewrittenAccessorUsesPayloadWithoutChangingOtherFileAccess() throws Exception {
        String fixture = AccessorFixture.class.getName().replace('.', '/');
        String runtime = RuntimeRecorder.class.getName().replace('.', '/');
        byte[] transformed = PackedJar.instrument(classBytes(AccessorFixture.class), runtime, true,
                Set.of(fixture + "#runtimeArchive"));
        RuntimeRecorder.installs = 0;
        FixtureLoader loader = new FixtureLoader(Map.of(fixture.replace('/', '.'), transformed));
        Object instance = loader.loadClass(fixture.replace('/', '.')).getConstructor().newInstance();
        assertEquals(new File("payload.jar"), instance.getClass().getMethod("runtimeArchive").invoke(instance));
        assertEquals(new File("installed.jar"), instance.getClass().getMethod("sourceArchive").invoke(instance));
        assertEquals(1, RuntimeRecorder.installs);
    }

    @Test
    void temporaryPaperArchiveClosesOnNormalAndExceptionalReturns() throws Exception {
        String builder = "io/papermc/paper/plugin/loader/PluginClasspathBuilder";
        String fixture = "test/PaperLoader";
        byte[] transformed = PackedJar.instrument(paperLoader(fixture, builder),
                RuntimeRecorder.class.getName().replace('.', '/'), true, Set.of());
        FixtureLoader loader = new FixtureLoader(Map.of(fixture.replace('/', '.'), transformed,
                builder.replace('/', '.'), emptyClass(builder, "java/lang/Object")));
        Class<?> builderType = loader.loadClass(builder.replace('/', '.'));
        Object instance = loader.loadClass(fixture.replace('/', '.')).getConstructor().newInstance();
        Method method = instance.getClass().getMethod("classloader", builderType);
        RuntimeRecorder.releases = new ArrayList<>();
        method.invoke(instance, builderType.getConstructor().newInstance());
        assertEquals(1, RuntimeRecorder.releases.size());
        assertEquals(null, RuntimeRecorder.releases.get(0));
        InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                () -> method.invoke(instance, new Object[]{null}));
        assertEquals(2, RuntimeRecorder.releases.size());
        assertSame(failure.getCause(), RuntimeRecorder.releases.get(1));
    }

    private Map<String, byte[]> payload(Map<String, byte[]> outer) throws IOException {
        Path archive = directory.resolve("payload.jar");
        try (XZInputStream input = new XZInputStream(new ByteArrayInputStream(outer.get(PackedJar.PAYLOAD)))) {
            Files.write(archive, input.readAllBytes());
        }
        return PackedJar.readJar(archive);
    }

    private static void addCompressibleClasses(Map<String, byte[]> contents) {
        for (int index = 0; index < 200; index++) {
            String name = "test/data/Deferred" + index;
            ClassWriter writer = new ClassWriter(0);
            writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
            writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "VALUE",
                    "Ljava/lang/String;", null, sharedConstant()).visitEnd();
            constructor(writer, "java/lang/Object");
            writer.visitEnd();
            contents.put(name + ".class", writer.toByteArray());
        }
    }

    private static String sharedConstant() {
        Random random = new Random(41);
        StringBuilder value = new StringBuilder(2048);
        for (int index = 0; index < 2048; index++) {
            value.append((char) ('a' + random.nextInt(26)));
        }
        return value.toString();
    }

    private static Map<String, byte[]> simplePlugin(String name) {
        Map<String, byte[]> contents = new TreeMap<>();
        contents.put("plugin.yml", ("name: " + name + "\nmain: test.Main\n").getBytes(StandardCharsets.UTF_8));
        contents.put("test/Main.class", emptyClass("test/Main", "java/lang/Object"));
        return contents;
    }

    private static List<String> calls(byte[] bytes) {
        List<String> calls = new ArrayList<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                             String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method, String descriptor,
                                                boolean isInterface) {
                        calls.add(owner + '#' + method);
                    }
                };
            }
        }, 0);
        return calls;
    }

    private static byte[] emptyClass(String name, String parent) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, parent, null);
        constructor(writer, parent);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] paperLoader(String name, String builder) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        constructor(writer, "java/lang/Object");
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "classloader", "(L" + builder + ";)V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 1);
        Label completed = new Label();
        method.visitJumpInsn(Opcodes.IFNONNULL, completed);
        method.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException");
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "()V", false);
        method.visitInsn(Opcodes.ATHROW);
        method.visitLabel(completed);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void constructor(ClassWriter writer, String parent) {
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
    }

    private static byte[] classBytes(Class<?> type) throws IOException {
        try (InputStream input = type.getResourceAsStream('/' + type.getName().replace('.', '/') + ".class")) {
            assertNotNull(input);
            return input.readAllBytes();
        }
    }

    public static class AccessorFixture {
        public File runtimeArchive() {
            return new File("installed.jar");
        }

        public File sourceArchive() {
            return new File("installed.jar");
        }
    }

    public static class RuntimeRecorder {
        static int installs;
        static List<Throwable> releases = new ArrayList<>();

        public static void install() {
            installs++;
        }

        public static File runtimeJar() {
            return new File("payload.jar");
        }

        public static void releaseTemporary(Throwable failure) {
            releases.add(failure);
        }
    }

    private static final class FixtureLoader extends ClassLoader {
        private final Map<String, byte[]> definitions;

        private FixtureLoader(Map<String, byte[]> definitions) {
            super(PackedJarTest.class.getClassLoader());
            this.definitions = definitions;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            byte[] bytes = definitions.get(name);
            if (bytes == null) {
                return super.loadClass(name, resolve);
            }
            Class<?> type = findLoadedClass(name);
            if (type == null) {
                type = defineClass(name, bytes, 0, bytes.length);
            }
            if (resolve) {
                resolveClass(type);
            }
            return type;
        }
    }
}

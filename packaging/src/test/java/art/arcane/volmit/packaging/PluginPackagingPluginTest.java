package art.arcane.volmit.packaging;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Execution(ExecutionMode.CONCURRENT)
class PluginPackagingPluginTest {
    private static final long SPIGOT_CAP = 7_600_000L;
    private static final String NATIVE_NOISE_LIBRARY = "lib/native/linux-x86_64/libvolmlib_noise.so";

    @TempDir
    Path directory;

    @TempDir
    Path workspace;

    @Test
    void normalArchiveBuildThinsDependenciesAndKeepsRuntimeMetadata() throws IOException {
        fixture(1_000_000, "", false);
        BuildResult result = runner("jar", "stage").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyDistributionPackaging").getOutcome());
        try (ZipFile jar = new ZipFile(directory.resolve("build/libs/example.jar").toFile())) {
            assertNull(jar.getEntry("lib/Unused.class"));
            assertNotNull(jar.getEntry("lib/Kept.class"));
            assertNotNull(jar.getEntry("plugin.yml"));
            assertNotNull(jar.getEntry("META-INF/LICENSE"));
            List<String> metadata = new ArrayList<>();
            ClassReader reader = new ClassReader(jar.getInputStream(jar.getEntry("owned/Main.class")));
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public void visitSource(String source, String debug) {
                    metadata.add(source);
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitParameter(String name, int access) {
                            metadata.add(name);
                        }

                        @Override
                        public void visitLineNumber(int line, Label start) {
                            metadata.add("line");
                        }

                        @Override
                        public void visitLocalVariable(String name, String descriptor, String signature,
                                                       Label start, Label end, int index) {
                            metadata.add("local");
                        }
                    };
                }
            }, 0);
            assertTrue(metadata.containsAll(List.of("Main.java", "count", "line")), metadata.toString());
            assertFalse(metadata.contains("local"));
        }
        assertTrue(Files.readString(directory.resolve("build/reports/packaging/distribution-shrink-usage.txt"))
                .contains("lib.Unused"));
        BuildResult cached = runner("jar", "verifyPluginJars").build();
        assertEquals(TaskOutcome.UP_TO_DATE, cached.task(":jar").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, cached.task(":verifyDistributionPackaging").getOutcome());
        Files.delete(directory.resolve("build/reports/packaging/distribution.json"));
        BuildResult missingReport = runner("jar").build();
        assertEquals(TaskOutcome.SUCCESS, missingReport.task(":jar").getOutcome());
        assertTrue(Files.exists(directory.resolve("build/reports/packaging/distribution.json")));
    }

    @Test
    void authoritativePatchedLibraryWinsInheritedDefinitionsAndPreservesUnrelatedTypes() throws Exception {
        fixture(1_000_000, "", false);
        source("lib/Patched.java", "package lib; public class Patched { public int ownedRegistration() { return 42; } }");
        source("owned/LibraryUse.java", "package owned; public class LibraryUse { public int read() { return new lib.Patched().ownedRegistration() + new lib.Unrelated().value(); } }");
        Path inherited = directory.resolve("src/inherited/java/lib"); Files.createDirectories(inherited);
        Files.writeString(inherited.resolve("Patched.java"), "package lib; public class Patched { public int original() { return 1; } }");
        Files.writeString(inherited.resolve("Unrelated.java"), "package lib; public class Unrelated { public int value() { return 9; } }");
        Files.writeString(directory.resolve("build.gradle"), """

                sourceSets { inherited }
                def inheritedJar = tasks.register('inheritedJar', Jar) {
                    archiveFileName.set('inherited.jar')
                    from(sourceSets.inherited.output)
                }
                dependencies { compileOnly files(inheritedJar) }
                def actualLibrary = tasks.register('actualLibrary', Jar) {
                    archiveFileName.set('actual-library.jar')
                    from(sourceSets.main.output) { include('lib/Patched.class') }
                }
                tasks.named('jar') { exclude('lib/Patched.class') }
                pluginPackaging.artifacts.distribution {
                    authoritativeShrinkLibraries.from(files(actualLibrary))
                    shrinkKeep = ['-keep class owned.LibraryUse { *; }']
                }
                """, StandardOpenOption.APPEND);
        BuildResult result = runner("jar").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyDistributionPackaging").getOutcome());
        Path artifact = directory.resolve("build/libs/example.jar");
        Path actual = directory.resolve("build/libs/actual-library.jar");
        Path raw = directory.resolve("build/libs/inherited.jar");
        try (URLClassLoader loader = new URLClassLoader(new URL[]{artifact.toUri().toURL(), actual.toUri().toURL(), raw.toUri().toURL()}, null)) {
            Class<?> use = loader.loadClass("owned.LibraryUse");
            assertEquals(51, use.getMethod("read").invoke(use.getConstructor().newInstance()));
        }
        assertFalse(Files.readString(directory.resolve("build/reports/packaging/distribution-shrink-warnings.txt")).contains("Warning:"));
    }

    @Test
    void authorityGeneratedInsideTheProducerHasNoSelfDependencyOrInputOutputCycle() throws IOException {
        fixture(1_000_000, "", false);
        source("lib/Actual.java", "package lib; public class Actual { public int value() { return 7; } }");
        Files.writeString(directory.resolve("build.gradle"), """

                def authority = layout.buildDirectory.file('library-authority.jar')
                tasks.named('jar') {
                    exclude('lib/Actual.class')
                    outputs.file(authority)
                    doLast {
                        ant.jar(destfile: authority.get().asFile) {
                            fileset(dir: sourceSets.main.output.classesDirs.singleFile) { include(name: 'lib/Actual.class') }
                        }
                    }
                }
                pluginPackaging.artifacts.distribution.authoritativeShrinkLibraries.from(authority)
                """, StandardOpenOption.APPEND);
        runner("jar").build();
        assertTrue(Files.isRegularFile(directory.resolve("build/library-authority.jar")));
        BuildResult cached = runner("jar").build();
        assertEquals(TaskOutcome.UP_TO_DATE, cached.task(":jar").getOutcome());
    }

    @Test
    void authoritativeLibraryConflictingWithTheProgramRefusesWithoutAWarningWaiver() throws IOException {
        fixture(1_000_000, "", false);
        Files.writeString(directory.resolve("build.gradle"), """

                def conflicting = tasks.register('conflictingLibrary', Jar) {
                    archiveFileName.set('conflicting.jar')
                    from(sourceSets.main.output) { include('owned/Main.class') }
                }
                pluginPackaging.artifacts.distribution.authoritativeShrinkLibraries.from(files(conflicting))
                """, StandardOpenOption.APPEND);
        BuildResult result = runner("jar").buildAndFail();
        assertTrue(result.getOutput().contains("Authoritative shrink library conflicts with program classes"), result.getOutput());
    }

    @Test
    void shrinkRetainsRelocatedNativeProviderConstructionAndInheritedOperations() throws Exception {
        fixture(1_000_000, "", false);
        source("lib/nativelib/NativeBinding.java", "package lib.nativelib; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE) public @interface NativeBinding { String value(); }");
        source("lib/nativelib/terrain/Access.java", "package lib.nativelib.terrain; @lib.nativelib.NativeBinding(\"terrain.NativeAccess\") public interface Access { int read(); }");
        source("lib/nativelib/common/AccessBase.java", "package lib.nativelib.common; public class AccessBase implements lib.nativelib.terrain.Access { public int read() { return helper(); } private int helper() { return 42; } public int unused() { return 5; } }");
        source("lib/nativelib/v26_2_R1/terrain/NativeAccess.java", "package lib.nativelib.v26_2_R1.terrain; public final class NativeAccess extends lib.nativelib.common.AccessBase { public NativeAccess() {} }");
        source("owned/NativeUse.java", "package owned; public class NativeUse { public int read(lib.nativelib.terrain.Access access) { return access.read(); } }");
        Files.writeString(directory.resolve("build.gradle"), "\npluginPackaging.artifacts.distribution.shrinkKeep = ['-keep class owned.NativeUse { *; }']\n", java.nio.file.StandardOpenOption.APPEND);
        runner("jar").build();
        Path artifact = directory.resolve("build/libs/example.jar");
        try (URLClassLoader loader = new URLClassLoader(new URL[]{artifact.toUri().toURL()}, null)) {
            Class<?> implementation = loader.loadClass("lib.nativelib.v26_2_R1.terrain.NativeAccess");
            Object access = implementation.getConstructor().newInstance();
            assertEquals(42, implementation.getMethod("read").invoke(access));
            assertThrows(NoSuchMethodException.class, () -> implementation.getMethod("unused"));
            Class<?> contract = loader.loadClass("lib.nativelib.terrain.Access");
            assertEquals(1, contract.getAnnotations().length);
        }
    }

    @Test
    void shrinkRetainsAnnotationSelectedNestedDirectorHandlers() throws Exception {
        fixture(1_000_000, "", false);
        source("fixture/director/DirectorParameterHandler.java", "package fixture.director; public interface DirectorParameterHandler { String parse(String input); }");
        source("fixture/director/annotations/Param.java", "package fixture.director.annotations; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.PARAMETER) public @interface Param { Class<?> customHandler(); }");
        source("owned/NestedCommand.java", """
                package owned;
                public class NestedCommand {
                    @fixture.director.annotations.Director
                    public void run(@fixture.director.annotations.Param(customHandler = ModeHandler.class) String mode) {}
                    private static String parseMode(String input) { return input.toUpperCase(); }
                    public static final class ModeHandler implements fixture.director.DirectorParameterHandler {
                        public String parse(String input) { return parseMode(input); }
                    }
                }
                """);
        runner("jar").build();
        Path artifact = directory.resolve("build/libs/example.jar");
        try (URLClassLoader loader = new URLClassLoader(new URL[]{artifact.toUri().toURL()}, null)) {
            Class<?> command = loader.loadClass("owned.NestedCommand");
            java.lang.annotation.Annotation parameter = command.getMethod("run", String.class).getParameterAnnotations()[0][0];
            Class<?> handler = (Class<?>) parameter.annotationType().getMethod("customHandler").invoke(parameter);
            assertEquals("SPATIAL", handler.getMethod("parse", String.class).invoke(handler.getConstructor().newInstance(), "spatial"));
        }
    }

    @Test
    void shrinkRemovesDeadCodeAndKeepsReflectiveRoots() throws IOException {
        fixture(1_000_000, "", false);
        BuildResult result = runner("jar").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":jar").getOutcome());
        try (ZipFile jar = new ZipFile(directory.resolve("build/libs/example.jar").toFile())) {
            assertNull(jar.getEntry("owned/Unused.class"));
            assertNotNull(jar.getEntry("owned/Main.class"));
            assertNotNull(jar.getEntry("lib/Prefixed.class"));
            assertNotNull(jar.getEntry("owned/ServiceImpl.class"));
            assertNotNull(jar.getEntry("META-INF/services/owned.Service"));
            List<String> helperMethods = methods(jar, "owned/Helper.class");
            assertTrue(helperMethods.contains("used"), helperMethods.toString());
            assertFalse(helperMethods.contains("unused"), helperMethods.toString());
            List<String> commandMethods = methods(jar, "owned/Commands.class");
            assertTrue(commandMethods.contains("run"), commandMethods.toString());
        }
        String report = Files.readString(directory.resolve("build/reports/packaging/distribution.json"));
        assertTrue(report.contains("\"applied\": true"), report);
        assertTrue(report.contains("\"removedClasses\": 2"), report);
        Path reports = directory.resolve("build/reports/packaging");
        assertTrue(Files.exists(reports.resolve("distribution-shrink-seeds.txt")));
        assertTrue(Files.exists(reports.resolve("distribution-shrink-configuration.txt")));
        assertTrue(Files.exists(reports.resolve("distribution-shrink-rules.txt")));
        String usage = Files.readString(reports.resolve("distribution-shrink-usage.txt"));
        assertTrue(usage.contains("owned.Unused"), usage);
        assertTrue(usage.contains("unused()"), usage);
    }

    @Test
    void thinningDropsNativeNoiseLibrariesWithTheirPrunedLoader() throws IOException {
        fixture(1_000_000, "", false);
        nativeNoise();
        BuildResult result = runner("jar").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":jar").getOutcome());
        try (ZipFile jar = new ZipFile(directory.resolve("build/libs/example.jar").toFile())) {
            assertNull(jar.getEntry("lib/util/noise/NativeNoiseLibrary.class"));
            assertNull(jar.getEntry(NATIVE_NOISE_LIBRARY));
        }
        assertEquals(List.of(NATIVE_NOISE_LIBRARY), strings(report(), "removedResources"));
    }

    @Test
    void thinningKeepsNativeNoiseLibrariesWithTheirRetainedLoader() throws IOException {
        fixture(1_000_000, "keepPrefixes = ['lib/Prefixed', 'lib/util/noise/']", false);
        nativeNoise();
        BuildResult result = runner("jar").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":jar").getOutcome());
        try (ZipFile jar = new ZipFile(directory.resolve("build/libs/example.jar").toFile())) {
            assertNotNull(jar.getEntry("lib/util/noise/NativeNoiseLibrary.class"));
            assertNotNull(jar.getEntry(NATIVE_NOISE_LIBRARY));
        }
        assertEquals(List.of(), strings(report(), "removedResources"));
    }

    @Test
    void shrinkKeepsEventHandlersIntegrationContractsAndPublicApi() throws IOException {
        fixture(1_000_000, "", false);
        BuildResult result = runner("jar").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":jar").getOutcome());
        try (ZipFile jar = new ZipFile(directory.resolve("build/libs/example.jar").toFile())) {
            List<String> eventMethods = methods(jar, "owned/CustomEvent.class");
            assertTrue(eventMethods.contains("getHandlerList"), eventMethods.toString());
            assertTrue(eventMethods.contains("getHandlers"), eventMethods.toString());
            assertNotNull(jar.getEntry("fixture/volmlib/integration/IntegrationServiceContract.class"));
            assertNotNull(jar.getEntry("fixture/volmlib/integration/IntegrationPayload.class"));
            List<String> bridgeMethods = methods(jar, "owned/IntegrationBridge.class");
            assertTrue(bridgeMethods.contains("describe"), bridgeMethods.toString());
            List<String> projectionMethods = methods(jar, "owned/ProjectionViews.class");
            assertTrue(projectionMethods.contains("viewers"), projectionMethods.toString());
            List<String> apiMethods = methods(jar, "art/arcane/example/api/PublicSurface.class");
            assertTrue(apiMethods.contains("describe"), apiMethods.toString());
        }
    }

    @Test
    void shrinkRestoresOriginalAdviceClassBytes() throws IOException {
        fixture(1_000_000, "", false);
        BuildResult result = runner("jar").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":jar").getOutcome());
        byte[] compiled = Files.readAllBytes(directory.resolve("build/classes/java/main/owned/Hooks$EnterAdvice.class"));
        List<Integer> compiledFrames = frames(compiled);
        assertFalse(compiledFrames.isEmpty(), compiledFrames.toString());
        assertFalse(compiledFrames.contains(Opcodes.F_CHOP), compiledFrames.toString());
        try (ZipFile jar = new ZipFile(directory.resolve("build/libs/example.jar").toFile())) {
            ZipEntry entry = jar.getEntry("owned/Hooks$EnterAdvice.class");
            assertNotNull(entry);
            byte[] packaged = jar.getInputStream(entry).readAllBytes();
            List<Integer> packagedFrames = frames(packaged);
            assertFalse(packagedFrames.contains(Opcodes.F_CHOP), packagedFrames.toString());
            assertArrayEquals(compiled, packaged);
            assertNotNull(jar.getEntry("owned/Hooks.class"));
        }
        Path reports = directory.resolve("build/reports/packaging");
        JsonObject report = JsonParser.parseString(Files.readString(reports.resolve("distribution.json"))).getAsJsonObject();
        List<String> restored = new ArrayList<>();
        for (JsonElement element : report.getAsJsonObject("shrink").getAsJsonArray("restoredClasses")) {
            restored.add(element.getAsString());
        }
        assertEquals(List.of("owned.Hooks", "owned.Hooks$EnterAdvice"), restored, report.toString());
        assertTrue(Files.readString(reports.resolve("distribution-shrink-usage.txt")).contains("owned.Hooks$EnterAdvice"));
        assertTrue(Files.readString(reports.resolve("distribution-shrink-warnings.txt"))
                .contains("restored  owned.Hooks$EnterAdvice"));
    }

    @Test
    void shrinkPropertyDisablesThePassAndReportsIt() throws IOException {
        fixture(1_000_000, "", false);
        BuildResult result = runner("jar", "-PvolmitShrink=false").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":jar").getOutcome());
        try (ZipFile jar = new ZipFile(directory.resolve("build/libs/example.jar").toFile())) {
            assertNotNull(jar.getEntry("owned/Unused.class"));
            assertTrue(methods(jar, "owned/Helper.class").contains("unused"));
        }
        String report = Files.readString(directory.resolve("build/reports/packaging/distribution.json"));
        assertTrue(report.contains("\"applied\": false"), report);
        assertTrue(report.contains("\"reason\": \"volmitShrink=false\""), report);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {1, 0, 9_000_000})
    void moddedProfileSkipsShrinkAndIgnoresAllSizeBudgets(Long maximumBytes) throws IOException {
        fixture(maximumBytes == null ? 1 : maximumBytes, "modded = true", true);
        if (maximumBytes == null) {
            omitMaximumBytes();
        }
        for (String mode : List.of("release", "dev")) {
            BuildResult result = runner("jar", "verifyPluginJars", "-PvolmitPackaging=" + mode).build();
            assertEquals(TaskOutcome.SUCCESS, result.task(":verifyDistributionPackaging").getOutcome());
            assertTrue(Files.size(directory.resolve("build/libs/example.jar")) > SPIGOT_CAP);
            try (ZipFile jar = new ZipFile(directory.resolve("build/libs/example.jar").toFile())) {
                assertNotNull(jar.getEntry("owned/Unused.class"));
            }
            JsonObject report = report();
            assertEquals(mode, report.get("mode").getAsString(), report.toString());
            assertTrue(report.get("maximumBytes").isJsonNull(), report.toString());
            assertTrue(report.get("effectiveMaximumBytes").isJsonNull(), report.toString());
            assertEquals("none", report.get("budgetAppliesTo").getAsString(), report.toString());
            assertTrue(strings(report, "warnings").isEmpty(), report.toString());
            assertTrue(strings(report, "errors").isEmpty(), report.toString());
            assertFalse(result.getOutput().contains("byte budget"), result.getOutput());
            JsonObject shrink = report.getAsJsonObject("shrink");
            assertFalse(shrink.get("applied").getAsBoolean(), report.toString());
            assertEquals("modded profile", shrink.get("reason").getAsString(), report.toString());
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void nonModdedMissingOrNonPositiveBudgetFailsConfiguration(Long maximumBytes) throws IOException {
        fixture(maximumBytes == null ? 1 : maximumBytes, "", false);
        if (maximumBytes == null) {
            omitMaximumBytes();
        }
        BuildResult result = runner("jar").buildAndFail();
        assertTrue(result.getOutput().contains("positive jar budget"), result.getOutput());
        assertFalse(Files.exists(directory.resolve("build/libs/example.jar")));
    }

    @Test
    void nonModdedBudgetAboveTheSpigotCapFailsConfiguration() throws IOException {
        fixture(8_000_000, "", false);
        BuildResult result = runner("jar").buildAndFail();
        assertTrue(result.getOutput().contains("7600000"), result.getOutput());
        assertFalse(Files.exists(directory.resolve("build/libs/example.jar")));
    }

    @Test
    void finalJarAboveTheSpigotCapFailsTheAudit() throws IOException {
        fixture(SPIGOT_CAP, "", true);
        BuildResult result = runner("jar").buildAndFail();
        assertTrue(result.getOutput().contains("byte budget"), result.getOutput());
        assertTrue(result.getOutput().contains("7600000"), result.getOutput());
    }

    @Test
    void intermediateArchiveDefersItsBudgetToTheFinalDistribution() throws IOException {
        fixture(1, "intermediate = true", false);
        BuildResult result = runner("jar", "verifyPluginJars").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyDistributionPackaging").getOutcome());
        JsonObject report = report();
        assertEquals("release", report.get("mode").getAsString());
        assertEquals("final distribution", report.get("budgetAppliesTo").getAsString());
        assertTrue(report.getAsJsonObject("shrink").get("applied").getAsBoolean());
        assertTrue(strings(report, "errors").isEmpty(), report.toString());
        assertEquals(1, strings(report, "warnings").size(), report.toString());
        assertTrue(strings(report, "warnings").get(0).contains("byte budget"), report.toString());
        Files.writeString(directory.resolve("build.gradle"),
                "\npluginPackaging.artifacts.distribution.intermediate = false\n",
                StandardOpenOption.APPEND);
        BuildResult finalArtifact = runner("jar").buildAndFail();
        assertTrue(finalArtifact.getOutput().contains("byte budget"), finalArtifact.getOutput());
        assertEquals("artifact", report().get("budgetAppliesTo").getAsString());
    }

    @Test
    void intermediateArchiveStillRequiresItsEntries() throws IOException {
        fixture(1, "intermediate = true; requiredEntries = ['plugin.yml', 'missing.yml']", false);
        BuildResult result = runner("jar").buildAndFail();
        assertTrue(result.getOutput().contains("Missing required entry: missing.yml"), result.getOutput());
    }

    @Test
    void intermediateArchiveStillRejectsForbiddenEntries() throws IOException {
        fixture(1, "intermediate = true; forbiddenPrefixes = ['owned/']", false);
        BuildResult result = runner("jar").buildAndFail();
        assertTrue(result.getOutput().contains("Forbidden bundled entry: owned/Main.class"), result.getOutput());
    }

    @Test
    void developmentModeKeepsAnOverCapJarAndRecordsTheSizeWarning() throws IOException {
        fixture(SPIGOT_CAP, "", true);
        BuildResult result = runner("jar", "verifyPluginJars", "-PvolmitPackaging=dev").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyDistributionPackaging").getOutcome());
        assertTrue(directory.resolve("build/libs/example.jar").toFile().length() > SPIGOT_CAP);
        try (ZipFile jar = new ZipFile(directory.resolve("build/libs/example.jar").toFile())) {
            assertNotNull(jar.getEntry("owned/Unused.class"));
            assertNotNull(jar.getEntry("plugin.yml"));
        }
        JsonObject report = report();
        assertEquals("dev", report.get("mode").getAsString(), report.toString());
        JsonObject shrink = report.getAsJsonObject("shrink");
        assertFalse(shrink.get("applied").getAsBoolean(), report.toString());
        assertEquals("volmitPackaging=dev", shrink.get("reason").getAsString(), report.toString());
        assertTrue(strings(report, "errors").isEmpty(), report.toString());
        List<String> warnings = strings(report, "warnings");
        assertEquals(1, warnings.size(), report.toString());
        assertTrue(warnings.get(0).contains("byte budget"), report.toString());
        assertTrue(result.getOutput().contains("DEVELOPMENT packaging via volmitPackaging=dev"), result.getOutput());
        assertTrue(result.getOutput().contains("Not a release jar."), result.getOutput());
    }

    @Test
    void developmentModeEnvironmentVariableSkipsTheShrinkAndTheSizeFailure() throws IOException {
        fixture(SPIGOT_CAP, "", true);
        Map<String, String> environment = new HashMap<>(System.getenv());
        environment.put("VOLMIT_PACKAGING", "dev");
        BuildResult result = runner("jar").withEnvironment(environment).build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":jar").getOutcome());
        JsonObject report = report();
        assertEquals("dev", report.get("mode").getAsString(), report.toString());
        assertEquals("VOLMIT_PACKAGING=dev", report.getAsJsonObject("shrink").get("reason").getAsString(), report.toString());
        assertEquals(1, strings(report, "warnings").size(), report.toString());
        assertTrue(strings(report, "errors").isEmpty(), report.toString());
    }

    @Test
    void developmentModeStillFailsAMissingRequiredEntry() throws IOException {
        fixture(1_000_000, "requiredEntries = ['plugin.yml', 'missing.yml']", false);
        BuildResult result = runner("jar", "-PvolmitPackaging=dev").buildAndFail();
        assertTrue(result.getOutput().contains("Missing required entry: missing.yml"), result.getOutput());
    }

    @Test
    void defaultModeFailsTheOverCapJarAndRecordsReleaseMode() throws IOException {
        fixture(SPIGOT_CAP, "", true);
        BuildResult result = runner("jar").buildAndFail();
        assertTrue(result.getOutput().contains("byte budget"), result.getOutput());
        JsonObject report = report();
        assertEquals("release", report.get("mode").getAsString(), report.toString());
        assertTrue(strings(report, "warnings").isEmpty(), report.toString());
        assertEquals(1, strings(report, "errors").size(), report.toString());
    }

    @Test
    void unknownPackagingModeFailsTheBuild() throws IOException {
        fixture(1_000_000, "", false);
        BuildResult result = runner("jar", "-PvolmitPackaging=development").buildAndFail();
        assertTrue(result.getOutput().contains("Unknown packaging mode"), result.getOutput());
    }

    @Test
    void developmentModeCannotBeDeclaredByAnArtifactPolicy() throws IOException {
        fixture(1_000_000, "mode = 'dev'", false);
        BuildResult result = runner("jar").buildAndFail();
        assertTrue(result.getOutput().contains("mode"), result.getOutput());
        assertFalse(Files.exists(directory.resolve("build/libs/example.jar")));
    }

    @Test
    void releaseCompressionIsAnArchiveInput() throws IOException {
        fixture(1_000_000, "", false);
        BuildResult release = runner("jar", "-PcompactRelease=true").build();
        assertEquals(TaskOutcome.SUCCESS, release.task(":jar").getOutcome());
        assertTrue(Files.readString(directory.resolve("build/reports/packaging/distribution.json"))
                .contains("\"releaseCompression\": true"));
        BuildResult cached = runner("jar", "-PcompactRelease=true").build();
        assertEquals(TaskOutcome.UP_TO_DATE, cached.task(":jar").getOutcome());
        BuildResult standard = runner("jar").build();
        assertEquals(TaskOutcome.SUCCESS, standard.task(":jar").getOutcome());
        assertTrue(Files.readString(directory.resolve("build/reports/packaging/distribution.json"))
                .contains("\"releaseCompression\": false"));
    }

    @Test
    void sizeFailurePreventsArtifactStaging() throws IOException {
        fixture(1, "", false);
        BuildResult result = runner("stage").buildAndFail();
        assertTrue(result.getOutput().contains("byte budget"), result.getOutput());
        assertFalse(Files.exists(directory.resolve("build/staged/example.jar")));
    }

    @Test
    void cachedArchiveReportIdentifiesTheArtifactInARelocatedCheckout() throws IOException {
        fixture(1_000_000, "", false);
        Path cache = workspace.resolve("build-cache");
        Files.createDirectories(cache);
        Files.writeString(directory.resolve("settings.gradle"), """
                rootProject.name = 'example'
                buildCache {
                    local {
                        directory = new File('%s')
                    }
                }
                """.formatted(cache.toAbsolutePath().toString().replace("\\", "\\\\").replace("'", "\\'")));
        Files.writeString(directory.resolve("build.gradle"), Files.readString(directory.resolve("build.gradle"))
                + "tasks.named('jar') { outputs.cacheIf { true } }\n");
        BuildResult origin = runner(directory, "jar", "--build-cache").build();
        assertEquals(TaskOutcome.SUCCESS, origin.task(":jar").getOutcome());
        Path checkout = workspace.resolve("checkout");
        copyCheckout(directory, checkout);
        BuildResult relocated = runner(checkout, "jar", "--build-cache").build();
        assertEquals(TaskOutcome.FROM_CACHE, relocated.task(":jar").getOutcome(), relocated.getOutput());
        Path archive = checkout.resolve("build/libs/example.jar");
        JsonObject report = JsonParser.parseString(
                Files.readString(checkout.resolve("build/reports/packaging/distribution.json"))).getAsJsonObject();
        String artifact = report.get("artifact").getAsString();
        assertEquals("example.jar", artifact, report.toString());
        assertTrue(Files.isRegularFile(archive.getParent().resolve(artifact)), report.toString());
        assertEquals(Files.size(archive), report.get("bytes").getAsLong(), report.toString());
        assertFalse(report.toString().contains(directory.toAbsolutePath().toString()), report.toString());
    }

    private JsonObject report() throws IOException {
        return JsonParser.parseString(
                Files.readString(directory.resolve("build/reports/packaging/distribution.json"))).getAsJsonObject();
    }

    private List<String> strings(JsonObject report, String member) {
        List<String> values = new ArrayList<>();
        for (JsonElement element : report.getAsJsonArray(member)) {
            values.add(element.getAsString());
        }
        return values;
    }

    private List<String> methods(ZipFile jar, String entry) throws IOException {
        ZipEntry classEntry = jar.getEntry(entry);
        assertNotNull(classEntry, entry);
        List<String> names = new ArrayList<>();
        new ClassReader(jar.getInputStream(classEntry)).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                names.add(name);
                return null;
            }
        }, ClassReader.SKIP_CODE);
        return names;
    }

    private List<Integer> frames(byte[] bytes) {
        List<Integer> types = new ArrayList<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
                        types.add(type);
                    }
                };
            }
        }, 0);
        return types;
    }

    private GradleRunner runner(String... tasks) {
        return runner(directory, tasks);
    }

    private GradleRunner runner(Path projectDirectory, String... tasks) {
        List<String> arguments = new ArrayList<>(List.of(tasks));
        arguments.add("--stacktrace");
        arguments.add("--no-configuration-cache");
        arguments.add("--max-workers=1");
        return GradleRunner.create().withProjectDir(projectDirectory.toFile()).withPluginClasspath()
                .withArguments(arguments);
    }

    private void copyCheckout(Path source, Path destination) throws IOException {
        try (Stream<Path> files = Files.walk(source)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                Path relative = source.relativize(file);
                if (relative.getNameCount() > 0 && List.of("build", ".gradle").contains(relative.getName(0).toString())) {
                    continue;
                }
                Path target = destination.resolve(relative);
                if (Files.isDirectory(file)) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.copy(file, target);
            }
        }
    }

    private void omitMaximumBytes() throws IOException {
        Path buildScript = directory.resolve("build.gradle");
        Files.writeString(buildScript, Files.readString(buildScript).replace("maximumBytes = 1\n", ""));
    }

    private void fixture(long maximumBytes, String artifactExtras, boolean oversized) throws IOException {
        Files.writeString(directory.resolve("settings.gradle"), "rootProject.name = 'example'\n");
        Files.writeString(directory.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'art.arcane.volmit-packaging'
                }
                pluginPackaging {
                    artifacts {
                        create('distribution') {
                            taskName = 'jar'
                            maximumBytes = %d
                            prunePrefixes = ['lib/']
                            keepPrefixes = ['lib/Prefixed']
                            stripLocalVariables = true
                            releaseCompression = providers.gradleProperty('compactRelease').map { it.toBoolean() }.getOrElse(false)
                            requiredEntries = ['plugin.yml', 'lib/Kept.class']
                            %s
                        }
                    }
                }
                tasks.register('stage', Copy) {
                    from(tasks.named('jar'))
                    into(layout.buildDirectory.dir('staged'))
                }
                """.formatted(maximumBytes, artifactExtras));
        source("owned/Main.java", "package owned; public class Main { public int plus(int count) { int result = count + new Helper().used(); return result; } public Object fire() { return new CustomEvent(); } public boolean hook(int count) { return Hooks.EnterAdvice.enter(this, count); } }");
        source("owned/Hooks.java", "package owned; public final class Hooks { private Hooks() {} public static final class EnterAdvice { @fixture.bytebuddy.asm.Advice.OnMethodEnter public static boolean enter(Object target, int value) { if (value > 0) { return true; } return false; } } }");
        source("fixture/bytebuddy/asm/Advice.java", "package fixture.bytebuddy.asm; import java.lang.annotation.*; public final class Advice { private Advice() {} @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) public @interface OnMethodEnter {} }");
        source("owned/Helper.java", "package owned; public class Helper { public int used() { return 1; } public String unused() { return \"unused\"; } }");
        source("owned/Unused.java", "package owned; public class Unused {}");
        source("owned/Service.java", "package owned; public interface Service { void serve(); }");
        source("owned/ServiceImpl.java", "package owned; public class ServiceImpl implements Service { public void serve() {} }");
        source("owned/Commands.java", "package owned; public class Commands { @fixture.director.annotations.Director public void run() {} }");
        source("fixture/director/annotations/Director.java", "package fixture.director.annotations; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.METHOD, ElementType.TYPE}) public @interface Director {}");
        source("org/bukkit/event/HandlerList.java", "package org.bukkit.event; public class HandlerList {}");
        source("org/bukkit/event/Event.java", "package org.bukkit.event; public abstract class Event { public abstract HandlerList getHandlers(); }");
        source("owned/CustomEvent.java", "package owned; import org.bukkit.event.Event; import org.bukkit.event.HandlerList; public class CustomEvent extends Event { private static final HandlerList HANDLERS = new HandlerList(); public static HandlerList getHandlerList() { return HANDLERS; } public HandlerList getHandlers() { return HANDLERS; } }");
        source("fixture/volmlib/integration/IntegrationServiceContract.java", "package fixture.volmlib.integration; public interface IntegrationServiceContract { IntegrationPayload describe(); }");
        source("fixture/volmlib/integration/IntegrationPayload.java", "package fixture.volmlib.integration; public record IntegrationPayload(String label) {}");
        source("owned/IntegrationBridge.java", "package owned; import fixture.volmlib.integration.IntegrationPayload; import fixture.volmlib.integration.IntegrationServiceContract; public class IntegrationBridge implements IntegrationServiceContract { public IntegrationPayload describe() { return new IntegrationPayload(\"bridge\"); } }");
        source("fixture/volmlib/projection/ProjectionViewProvider.java", "package fixture.volmlib.projection; public interface ProjectionViewProvider { java.util.Map<java.util.UUID, Double> viewers(java.util.UUID world, double x, double y, double z, double range); }");
        source("owned/ProjectionViews.java", "package owned; public class ProjectionViews implements fixture.volmlib.projection.ProjectionViewProvider { public java.util.Map<java.util.UUID, Double> viewers(java.util.UUID world, double x, double y, double z, double range) { return java.util.Map.of(); } }");
        source("art/arcane/example/api/PublicSurface.java", "package art.arcane.example.api; public class PublicSurface { public String describe() { return \"surface\"; } }");
        source("lib/Kept.java", "package lib; public class Kept {}");
        source("lib/Prefixed.java", "package lib; public class Prefixed {}");
        source("lib/Unused.java", "package lib; public class Unused {}");
        Path resources = directory.resolve("src/main/resources");
        Files.createDirectories(resources.resolve("META-INF/services"));
        Files.writeString(resources.resolve("plugin.yml"), "name: Example\nmain: owned.Main\n");
        Files.writeString(resources.resolve("META-INF/LICENSE"), "License text\n");
        Files.writeString(resources.resolve("META-INF/services/owned.Service"), "owned.ServiceImpl\n");
        if (oversized) {
            byte[] noise = new byte[8_000_000];
            new Random(7).nextBytes(noise);
            Files.write(resources.resolve("noise.bin"), noise);
        }
    }

    private void nativeNoise() throws IOException {
        source("lib/util/noise/NativeNoiseLibrary.java", "package lib.util.noise; public final class NativeNoiseLibrary {}");
        Path library = directory.resolve("src/main/resources").resolve(NATIVE_NOISE_LIBRARY);
        Files.createDirectories(library.getParent());
        Files.write(library, new byte[]{0x7f, 'E', 'L', 'F'});
    }

    private void source(String path, String contents) throws IOException {
        Path destination = directory.resolve("src/main/java").resolve(path);
        Files.createDirectories(destination.getParent());
        Files.writeString(destination, contents);
    }
}

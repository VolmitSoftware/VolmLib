package art.arcane.volmit.packaging;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginPackagingPluginTest {
    private static final long SPIGOT_CAP = 7_600_000L;

    @TempDir
    Path directory;

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

    @Test
    void moddedProfileSkipsShrinkAndIgnoresTheSpigotCap() throws IOException {
        fixture(9_000_000, "modded = true", true);
        BuildResult result = runner("jar", "verifyPluginJars").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyDistributionPackaging").getOutcome());
        assertTrue(directory.resolve("build/libs/example.jar").toFile().length() > SPIGOT_CAP);
        try (ZipFile jar = new ZipFile(directory.resolve("build/libs/example.jar").toFile())) {
            assertNotNull(jar.getEntry("owned/Unused.class"));
        }
        String report = Files.readString(directory.resolve("build/reports/packaging/distribution.json"));
        assertTrue(report.contains("\"applied\": false"), report);
        assertTrue(report.contains("\"reason\": \"modded profile\""), report);
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
        List<String> arguments = new ArrayList<>(List.of(tasks));
        arguments.add("--stacktrace");
        arguments.add("--no-configuration-cache");
        return GradleRunner.create().withProjectDir(directory.toFile()).withPluginClasspath().withArguments(arguments);
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

    private void source(String path, String contents) throws IOException {
        Path destination = directory.resolve("src/main/java").resolve(path);
        Files.createDirectories(destination.getParent());
        Files.writeString(destination, contents);
    }
}

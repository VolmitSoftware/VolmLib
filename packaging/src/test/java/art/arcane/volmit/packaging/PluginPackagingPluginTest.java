package art.arcane.volmit.packaging;

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
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginPackagingPluginTest {
    @TempDir
    Path directory;

    @Test
    void normalArchiveBuildThinsDependenciesAndKeepsRuntimeMetadata() throws IOException {
        fixture(1_000_000);
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
        assertTrue(Files.readString(directory.resolve("build/reports/packaging/distribution.json"))
                .contains("lib/Unused.class"));
        BuildResult cached = runner("jar", "verifyPluginJars").build();
        assertEquals(TaskOutcome.UP_TO_DATE, cached.task(":jar").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, cached.task(":verifyDistributionPackaging").getOutcome());
        Files.delete(directory.resolve("build/reports/packaging/distribution.json"));
        BuildResult missingReport = runner("jar").build();
        assertEquals(TaskOutcome.SUCCESS, missingReport.task(":jar").getOutcome());
        assertTrue(Files.exists(directory.resolve("build/reports/packaging/distribution.json")));
    }

    @Test
    void releaseCompressionIsAnArchiveInput() throws IOException {
        fixture(1_000_000);
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
        fixture(1);
        BuildResult result = runner("stage").buildAndFail();
        assertTrue(result.getOutput().contains("byte budget"), result.getOutput());
        assertFalse(Files.exists(directory.resolve("build/staged/example.jar")));
    }

    private GradleRunner runner(String... tasks) {
        List<String> arguments = new ArrayList<>(List.of(tasks));
        arguments.add("--stacktrace");
        arguments.add("--no-configuration-cache");
        return GradleRunner.create().withProjectDir(directory.toFile()).withPluginClasspath().withArguments(arguments);
    }

    private void fixture(long maximumBytes) throws IOException {
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
                            stripLocalVariables = true
                            releaseCompression = providers.gradleProperty('compactRelease').map { it.toBoolean() }.getOrElse(false)
                            requiredEntries = ['plugin.yml', 'lib/Kept.class']
                        }
                    }
                }
                tasks.register('stage', Copy) {
                    from(tasks.named('jar'))
                    into(layout.buildDirectory.dir('staged'))
                }
                """.formatted(maximumBytes));
        source("owned/Main.java", "package owned; public class Main { public int plus(int count) { int result = count + 1; return result; } }");
        source("lib/Kept.java", "package lib; public class Kept {}");
        source("lib/Unused.java", "package lib; public class Unused {}");
        Path resources = directory.resolve("src/main/resources");
        Files.createDirectories(resources.resolve("META-INF"));
        Files.writeString(resources.resolve("plugin.yml"), "name: Example\nmain: owned.Main\n");
        Files.writeString(resources.resolve("META-INF/LICENSE"), "License text\n");
    }

    private void source(String path, String contents) throws IOException {
        Path destination = directory.resolve("src/main/java").resolve(path);
        Files.createDirectories(destination.getParent());
        Files.writeString(destination, contents);
    }
}

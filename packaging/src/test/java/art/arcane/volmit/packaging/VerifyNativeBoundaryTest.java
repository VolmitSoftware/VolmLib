package art.arcane.volmit.packaging;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerifyNativeBoundaryTest {
    @TempDir
    Path directory;

    @Test
    void rejectsImportsAndReflectionNamesInPluginSources() throws IOException {
        fixture();
        source("src/main/java/plugin/Native.java", """
                package plugin;
                import net.minecraft.world.level.Level;
                class Native {
                    String type = "org.bukkit.craftbukkit.CraftWorld";
                }
                """);
        BuildResult result = runner().buildAndFail();
        assertEquals(TaskOutcome.FAILED, result.task(":verifyNativeBoundary").getOutcome());
        assertTrue(result.getOutput().contains("Native.java:2"));
        assertTrue(result.getOutput().contains("Native.java:4"));
    }

    @Test
    void acceptsNativeModuleSourcesAndExcludesTestFixtures() throws IOException {
        fixture();
        source("src/main/java/art/arcane/volmlib/nativelib/v26_2_R1/Access.java", """
                package art.arcane.volmlib.nativelib.v26_2_R1;
                import net.minecraft.world.level.Level;
                class Access {}
                """);
        source("src/test/java/Fixture.java", "import org.bukkit.craftbukkit.CraftWorld;");
        assertEquals(TaskOutcome.SUCCESS, runner().build().task(":verifyNativeBoundary").getOutcome());
        assertEquals(TaskOutcome.UP_TO_DATE, runner().build().task(":verifyNativeBoundary").getOutcome());
    }

    @Test
    void coversNestedProjectMainSources() throws IOException {
        fixture();
        Files.writeString(directory.resolve("settings.gradle"), "rootProject.name = 'fixture'\ninclude('child')\n");
        source("child/build.gradle", "plugins { id 'java' }\n");
        source("child/src/main/java/plugin/Config.java", """
                package plugin;
                class Config {
                    String type = "io.papermc.paper.configuration.GlobalConfiguration";
                }
                """);
        BuildResult result = runner().buildAndFail();
        assertTrue(result.getOutput().contains("Config.java:3"));
    }

    @Test
    void acceptsExplicitProjectNativePackagesAndKeepsTheSharedDefault() throws IOException {
        fixture();
        Files.writeString(directory.resolve("build.gradle"),
                "pluginPackaging { nativeImplementationPackages.add('com.example.nativeimpl') }\n", StandardOpenOption.APPEND);
        source("src/main/java/com/example/nativeimpl/v1_21_4/Adapter.java", """
                package com.example.nativeimpl.v1_21_4;
                import net.minecraft.world.level.Level;
                class Adapter {}
                """);
        source("src/main/java/art/arcane/volmlib/nativelib/SharedAdapter.java", """
                package art.arcane.volmlib.nativelib;
                import net.minecraft.world.level.Level;
                class SharedAdapter {}
                """);
        assertEquals(TaskOutcome.SUCCESS, runner().build().task(":verifyNativeBoundary").getOutcome());
        Files.writeString(directory.resolve("build.gradle"),
                "pluginPackaging { nativeImplementationPackages.set(['art.arcane.volmlib.nativelib']) }\n", StandardOpenOption.APPEND);
        BuildResult changedPolicy = runner().buildAndFail();
        assertEquals(TaskOutcome.FAILED, changedPolicy.task(":verifyNativeBoundary").getOutcome());
        assertTrue(changedPolicy.getOutput().contains("Adapter.java:2"));
    }

    @Test
    void declaredNativePackageDoesNotAllowSiblingOrLookalikeSources() throws IOException {
        fixture();
        Files.writeString(directory.resolve("build.gradle"),
                "pluginPackaging { nativeImplementationPackages.add('com.example.nativeimpl') }\n", StandardOpenOption.APPEND);
        source("src/main/java/com/example/nativeimplementation/Lookalike.java", """
                package com.example.nativeimplementation;
                import net.minecraft.world.level.Level;
                class Lookalike {}
                """);
        source("src/main/java/com/example/gameplay/Leaked.java", """
                package com.example.gameplay;
                import net.minecraft.world.level.Level;
                class Leaked {}
                """);
        BuildResult result = runner().buildAndFail();
        assertTrue(result.getOutput().contains("Lookalike.java:2"));
        assertTrue(result.getOutput().contains("Leaked.java:2"));
    }

    private void fixture() throws IOException {
        Files.writeString(directory.resolve("settings.gradle"), "rootProject.name = 'fixture'\n");
        Files.writeString(directory.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'art.arcane.volmit-packaging'
                }
                """);
    }

    private void source(String name, String text) throws IOException {
        Path file = directory.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    private GradleRunner runner() {
        return GradleRunner.create().withProjectDir(directory.toFile()).withPluginClasspath()
                .withArguments("verifyNativeBoundary", "--stacktrace", "--max-workers=1");
    }
}

package art.arcane.volmit.packaging;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerifyLoggingPolicyTest {
    @TempDir
    Path directory;

    @Test
    void checkFailsOnRawConsoleOutputAndNamesTheOffendingLine() throws IOException {
        fixture("");
        source("owned/Noisy.java", """
                package owned;

                public class Noisy {
                    public void report() {
                        System.out.println("hello");
                    }
                }
                """);
        BuildResult result = runner("check").buildAndFail();
        assertEquals(TaskOutcome.FAILED, result.task(":verifyLoggingPolicy").getOutcome());
        assertTrue(result.getOutput().contains("owned/Noisy.java:5"), result.getOutput());
        assertTrue(result.getOutput().contains("System.out.println(\"hello\")"), result.getOutput());
        assertTrue(result.getOutput().contains("logging-policy-allowlist.txt"), result.getOutput());
    }

    @Test
    void fleetDefaultsCoverStackTracesAndConsoleSenders() throws IOException {
        fixture("");
        source("owned/Traces.java", """
                package owned;

                public class Traces {
                    public void report(Throwable failure) {
                        failure.printStackTrace();
                    }
                }
                """);
        source("owned/Console.java", """
                package owned;

                public class Console {
                    public void report(org.bukkit.Server server) {
                        server.getConsoleSender().sendMessage("hello");
                    }
                }
                """);
        BuildResult result = runner("verifyLoggingPolicy").buildAndFail();
        assertTrue(result.getOutput().contains("owned/Traces.java:5"), result.getOutput());
        assertTrue(result.getOutput().contains("owned/Console.java:5"), result.getOutput());
    }

    @Test
    void allowlistExemptsOnePatternInOneFileAndStaysUpToDate() throws IOException {
        fixture("");
        source("owned/Noisy.java", """
                package owned;

                public class Noisy {
                    public void report(Throwable failure) {
                        System.err.println("boom");
                    }
                }
                """);
        Files.writeString(directory.resolve("logging-policy-allowlist.txt"),
                "# Last resort when the plugin logger itself failed.\nowned/Noisy.java System.err\n");
        BuildResult first = runner("verifyLoggingPolicy").build();
        assertEquals(TaskOutcome.SUCCESS, first.task(":verifyLoggingPolicy").getOutcome());
        BuildResult second = runner("verifyLoggingPolicy").build();
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":verifyLoggingPolicy").getOutcome());
        source("owned/Noisy.java", """
                package owned;

                public class Noisy {
                    public void report(Throwable failure) {
                        System.err.println("boom");
                        System.out.println("also boom");
                    }
                }
                """);
        BuildResult third = runner("verifyLoggingPolicy").buildAndFail();
        assertTrue(third.getOutput().contains("owned/Noisy.java:6"), third.getOutput());
    }

    @Test
    void allowlistDirectoryPrefixExemptsAWholeSubtree() throws IOException {
        fixture("");
        source("owned/vendor/Legacy.java", """
                package owned.vendor;

                public class Legacy {
                    public void report() {
                        System.out.println("vendored");
                    }
                }
                """);
        Files.writeString(directory.resolve("logging-policy-allowlist.txt"), "owned/vendor/\n");
        BuildResult result = runner("verifyLoggingPolicy").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyLoggingPolicy").getOutcome());
    }

    @Test
    void staleExemptionsFailBecauseTheAllowlistOnlyShrinks() throws IOException {
        fixture("");
        source("owned/Quiet.java", """
                package owned;

                public class Quiet {
                    public void report() {
                    }
                }
                """);
        Files.writeString(directory.resolve("logging-policy-allowlist.txt"), "owned/Quiet.java System.out\n");
        BuildResult result = runner("verifyLoggingPolicy").buildAndFail();
        assertTrue(result.getOutput().contains("owned/Quiet.java System.out"), result.getOutput());
        assertTrue(result.getOutput().contains("only shrink"), result.getOutput());
    }

    @Test
    void configuredPatternsAndAllowlistFileOverrideTheDefaults() throws IOException {
        fixture("""
                pluginPackaging {
                    loggingPolicy {
                        forbiddenPatterns = ['Bukkit.broadcastMessage(']
                        allowlistFile = file('policy/exempt.txt')
                    }
                }
                """);
        source("owned/Broadcast.java", """
                package owned;

                public class Broadcast {
                    public void report() {
                        System.out.println("tolerated by the override");
                        org.bukkit.Bukkit.broadcastMessage("nope");
                    }
                }
                """);
        BuildResult failure = runner("verifyLoggingPolicy").buildAndFail();
        assertTrue(failure.getOutput().contains("owned/Broadcast.java:6"), failure.getOutput());
        Files.createDirectories(directory.resolve("policy"));
        Files.writeString(directory.resolve("policy/exempt.txt"), "owned/Broadcast.java\n");
        BuildResult success = runner("verifyLoggingPolicy").build();
        assertEquals(TaskOutcome.SUCCESS, success.task(":verifyLoggingPolicy").getOutcome());
    }

    @Test
    void resultsAreRestoredFromTheBuildCache() throws IOException {
        fixture("");
        source("owned/Quiet.java", """
                package owned;

                public class Quiet {
                }
                """);
        BuildResult first = runner("verifyLoggingPolicy", "--build-cache").build();
        assertEquals(TaskOutcome.SUCCESS, first.task(":verifyLoggingPolicy").getOutcome());
        BuildResult cleaned = runner("clean", "--build-cache").build();
        assertEquals(TaskOutcome.SUCCESS, cleaned.task(":clean").getOutcome());
        BuildResult cached = runner("verifyLoggingPolicy", "--build-cache").build();
        assertEquals(TaskOutcome.FROM_CACHE, cached.task(":verifyLoggingPolicy").getOutcome());
    }

    private GradleRunner runner(String... tasks) {
        List<String> arguments = new ArrayList<>(List.of(tasks));
        arguments.add("--stacktrace");
        arguments.add("--no-configuration-cache");
        return GradleRunner.create().withProjectDir(directory.toFile()).withPluginClasspath().withArguments(arguments);
    }

    private void fixture(String extras) throws IOException {
        Files.writeString(directory.resolve("settings.gradle"), """
                rootProject.name = 'example'
                buildCache {
                    local {
                        directory = new File(settingsDir, 'build-cache')
                    }
                }
                """);
        Files.writeString(directory.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'art.arcane.volmit-packaging'
                }
                """ + extras);
    }

    private void source(String path, String contents) throws IOException {
        Path destination = directory.resolve("src/main/java").resolve(path);
        Files.createDirectories(destination.getParent());
        Files.writeString(destination, contents);
    }
}

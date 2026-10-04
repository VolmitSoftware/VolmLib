package art.arcane.volmlib.util.noise;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.io.File;
import org.junit.Assume;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NativeBillowTest {
    @Test
    public void disabledAndUnavailableBackendsRemainExact() throws Exception {
        probe("java-disabled", false, List.of("-Dvolmlib.noise.nativeBillow=false"));
        probe("java-disabled", true, List.of("-Dvolmlib.noise.nativeBillow=false"));
        probe("java-native-access-disabled", false, List.of());
        probe("java-native-access-disabled", false, List.of("-Dvolmlib.noise.nativeBillow=true"));
        probe(supportedHost() ? "java-library-missing" : "java-unsupported-platform", true, List.of("-Dvolmlib.noise.nativeBillow=true", "-Dvolmlib.noise.nativeBillowLibrary=" + Path.of("build/missing-native-billow").toAbsolutePath()));
        probe(supportedHost() ? "java-library-path-not-absolute" : "java-unsupported-platform", true, List.of("-Dvolmlib.noise.nativeBillow=true", "-Dvolmlib.noise.nativeBillowLibrary=relative"));
        probe("java-unsupported-platform", true, List.of("-Dvolmlib.noise.nativeBillow=true", "-Dos.arch=unsupported"));
    }

    @Test
    public void packagedNativeBackendRemainsExact() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("volmlib.test.nativeBillow") && supportedHost());
        probe("native", true, List.of());
        probe("native", true, List.of("-Dvolmlib.noise.nativeBillow=true"));
        probe("native", true, List.of("-Dvolmlib.noise.nativeBillow=true", "-Dvolmlib.test.diagnosticsOff=true"));
        probe("java-library-not-packaged", true, List.of("-Dvolmlib.noise.nativeBillow=true"));
        probe("java-initialization-failed", true, List.of("-Dvolmlib.noise.nativeBillow=true", "-Dvolmlib.noise.nativeBillowLibrary=" + System.getProperty("volmlib.test.missingSymbolLibrary")));
        probe("java-initialization-failed", true, List.of("-Dvolmlib.noise.nativeBillow=true", "-Dvolmlib.noise.nativeBillowLibrary=" + System.getProperty("volmlib.test.incorrectBillowLibrary")));
        probe("java-initialization-failed", true, List.of("-Dvolmlib.noise.nativeBillow=true", "-Dvolmlib.noise.nativeBillowLibrary=" + Path.of("src/main/rust/billow.rs").toAbsolutePath()));
    }

    private static boolean supportedHost() {
        String operatingSystem = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        return (operatingSystem.contains("mac") || operatingSystem.contains("linux"))
                && List.of("aarch64", "arm64", "amd64", "x86_64").contains(System.getProperty("os.arch"));
    }

    private static void probe(String expected, boolean access, List<String> options) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        if (access) {
            command.add("--enable-native-access=ALL-UNNAMED");
        }
        command.addAll(options);
        boolean diagnostics = !options.contains("-Dvolmlib.test.diagnosticsOff=true");
        command.add("-Dvolmlib.noise.nativeBillowDiagnostics=" + diagnostics);
        command.add("-cp");
        String classpath = System.getProperty("volmlib.test.runtimeClasspath", System.getProperty("java.class.path"));
        if (expected.equals("java-library-not-packaged")) {
            List<String> retained = new ArrayList<>();
            for (String entry : classpath.split(File.pathSeparator)) {
                if (!entry.endsWith("/resources/main")) {
                    retained.add(entry);
                }
            }
            classpath = String.join(File.pathSeparator, retained);
        }
        command.add(classpath);
        command.add(NativeBillowProbe.class.getName());
        command.add(expected);
        command.add(Boolean.toString(diagnostics));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        assertTrue("Probe timed out", process.waitFor(60L, TimeUnit.SECONDS));
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(output, 0, process.exitValue());
        System.out.print(output);
    }
}

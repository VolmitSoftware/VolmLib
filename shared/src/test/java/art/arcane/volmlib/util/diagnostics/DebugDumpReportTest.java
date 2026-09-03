package art.arcane.volmlib.util.diagnostics;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DebugDumpReportTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void preservesCommonDiagnosticsWithoutDisclosingPrivateRuntimeSources() throws IOException {
        Path root = temporary.newFolder().toPath();
        Path artifact = root.resolve("Plugin.jar");
        Files.writeString(artifact, "artifact");
        Files.writeString(root.resolve("config.toml"), "token=private-config-secret");
        BukkitDebugSnapshot snapshot = new BukkitDebugSnapshot(
                Instant.parse("2026-09-03T12:00:00Z"), "Example", "2.0.0", "player",
                serverState(), List.of(new BukkitDebugSnapshot.PluginState(
                        "Example", "2.0.0", true, "example.Plugin", List.of("Volmit Software"),
                        "POSTWORLD", "1.20", List.of(), List.of("PlaceholderAPI")
                )), root, artifact
        );

        String report = DebugDumpReport.create(snapshot, "== Example ==\nManaged portals: 4\n");

        assertTrue(report.contains("Example diagnostic report"));
        assertTrue(report.contains("Version: 2.0.0"));
        assertTrue(report.contains("Implementation: Paper Injected"));
        assertTrue(report.contains("Pending scheduler tasks: 7"));
        assertTrue(report.contains("Managed portals: 4"));
        assertTrue(report.contains("Example 2.0.0 | enabled=true"));
        assertTrue(report.contains("== Java runtime =="));
        assertTrue(report.contains("== Memory =="));
        assertTrue(report.contains("== CPU and operating system =="));
        assertTrue(report.contains("== Garbage collectors =="));
        assertTrue(report.contains("== Buffer pools =="));
        assertTrue(report.contains("== Plugin data filesystem =="));
        assertTrue(report.contains("config.toml: size="));
        assertTrue(report.contains("Filename: Plugin.jar"));
        assertTrue(report.contains("SHA-256: c7c5c1d70c5dec4416ab6158afd0b223ef40c29b1dc1f97ed9428b94d4cadb1c"));
        assertFalse(report.contains("private-config-secret"));
        assertFalse(report.contains(root.toString()));
        assertFalse(report.contains("sun.java.command"));
        assertFalse(report.contains("user.dir"));
        assertFalse(report.contains("== Threads =="));
    }

    @Test
    public void knownFileMetadataStaysInsideItsRootAndSkipsSymbolicLinks() throws IOException {
        Path root = temporary.newFolder("data").toPath();
        Path outside = temporary.newFolder("outside").toPath();
        Files.writeString(outside.resolve("secret.toml"), "secret-file-contents");
        Files.createSymbolicLink(root.resolve("language"), outside);
        Files.createSymbolicLink(root.resolve("config.toml"), outside.resolve("secret.toml"));

        String report = DebugDumpReport.describeFiles(root, List.of(
                Path.of("language", "secret.toml"), Path.of("config.toml"),
                Path.of("..", "outside", "secret.toml"), outside.resolve("secret.toml")
        ));

        assertTrue(report.contains("language/secret.toml: not present"));
        assertTrue(report.contains("config.toml: not present"));
        assertTrue(report.contains("File: invalid relative path"));
        assertFalse(report.contains("sha256="));
        assertFalse(report.contains("secret-file-contents"));
        assertFalse(report.contains(outside.toString()));
    }

    @Test
    public void knownFileHashingHasCountAndSizeBounds() throws IOException {
        Path root = temporary.newFolder().toPath();
        Path large = root.resolve("large.json");
        try (SeekableByteChannel file = Files.newByteChannel(large, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            file.position(17L * 1024L * 1024L);
            file.write(ByteBuffer.wrap(new byte[]{0}));
        }
        List<Path> files = new ArrayList<>(34);
        files.add(Path.of("large.json"));
        for (int index = 1; index < 34; index++) {
            files.add(Path.of("file-" + index + ".yml"));
        }

        String report = DebugDumpReport.describeFiles(root, files);

        assertTrue(report.contains("sha256=not computed (file exceeds"));
        assertTrue(report.contains("Remaining files: 2"));
        assertFalse(report.contains("file-32.yml"));
    }

    private static BukkitDebugSnapshot.ServerState serverState() {
        return new BukkitDebugSnapshot.ServerState(
                "Paper\nInjected", "Paper 1.21.11", "1.21.11-R0.1-SNAPSHOT", "1.21.11",
                true, 2, 100, 10, 10, false, true, true, "SURVIVAL", 10, 0, 7, 3,
                Map.of("NORMAL", 1, "NETHER", 1, "THE_END", 1),
                "Folia region", "20.00, 19.98, 19.95", "12.34"
        );
    }
}

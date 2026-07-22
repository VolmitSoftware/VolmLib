package art.arcane.volmlib.util.hotload;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfigHotloadEngineTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void idleEventWatcherDoesNotRescanKnownFiles() throws IOException {
        File directory = temporaryFolder.newFolder("idle-managed");
        File file = new File(directory, "feature.toml");
        Files.writeString(file.toPath(), "enabled = true\n", StandardCharsets.UTF_8);
        AtomicInteger supplierCalls = new AtomicInteger();
        ConfigHotloadEngine engine = createEngine(() -> {
            supplierCalls.incrementAndGet();
            return knownConfigFiles(directory);
        });

        try {
            engine.configure(500L, List.of(), List.of(directory));
            Assume.assumeTrue(engine.isDirectoryEventWatchActive());
            assertEquals(1, supplierCalls.get());

            for (int i = 0; i < 100; i++) {
                assertTrue(engine.pollTouchedFiles().isEmpty());
            }

            assertEquals(1, supplierCalls.get());
        } finally {
            engine.clear();
        }
    }

    @Test(timeout = 8_000L)
    public void eventWatcherDetectsExternalModification() throws Exception {
        File directory = temporaryFolder.newFolder("modify-managed");
        File file = new File(directory, "feature.toml");
        Files.writeString(file.toPath(), "enabled = true\n", StandardCharsets.UTF_8);
        ConfigHotloadEngine engine = createEngine(() -> knownConfigFiles(directory));

        try {
            engine.configure(500L, List.of(), List.of(directory));
            Assume.assumeTrue(engine.isDirectoryEventWatchActive());
            Files.writeString(file.toPath(), "enabled = false\nlimit = 42\n", StandardCharsets.UTF_8);

            Set<File> touched = awaitTouchedFile(engine, file, 5_000L);
            assertTrue(touched.contains(file));

            AtomicInteger applyCalls = new AtomicInteger();
            boolean applied = engine.processFileChange(file, changedFile -> {
                applyCalls.incrementAndGet();
                return true;
            }, null);
            assertTrue(applied);
            assertEquals(1, applyCalls.get());
        } finally {
            engine.clear();
        }
    }

    @Test
    public void missingSiblingDirectoryDoesNotRescanHealthyEventDirectory() throws IOException {
        File directory = temporaryFolder.newFolder("partial-managed");
        File missingDirectory = new File(temporaryFolder.getRoot(), "partial-missing");
        File file = new File(directory, "feature.toml");
        Files.writeString(file.toPath(), "enabled = true\n", StandardCharsets.UTF_8);
        AtomicInteger supplierCalls = new AtomicInteger();
        ConfigHotloadEngine engine = createEngine(() -> {
            supplierCalls.incrementAndGet();
            return knownConfigFiles(directory);
        });

        try {
            engine.configure(500L, List.of(), List.of(directory, missingDirectory));
            Assume.assumeTrue(engine.isDirectoryEventWatchActive());
            assertEquals(1, supplierCalls.get());

            for (int i = 0; i < 100; i++) {
                assertTrue(engine.pollTouchedFiles().isEmpty());
            }

            assertEquals(1, supplierCalls.get());
        } finally {
            engine.clear();
        }
    }

    @Test
    public void selfWriteDoesNotReapplyKnownContent() throws IOException {
        File directory = temporaryFolder.newFolder("self-write-managed");
        File file = new File(directory, "feature.toml");
        Files.writeString(file.toPath(), "enabled = true\n", StandardCharsets.UTF_8);
        ConfigHotloadEngine engine = createEngine(() -> knownConfigFiles(directory));

        try {
            engine.configure(500L, List.of(), List.of(directory));
            String updated = "enabled = false\n";
            Files.writeString(file.toPath(), updated, StandardCharsets.UTF_8);
            engine.noteSelfWrite(file, updated);
            AtomicInteger applyCalls = new AtomicInteger();

            boolean applied = engine.processFileChange(file, changedFile -> {
                applyCalls.incrementAndGet();
                return true;
            }, null);

            assertFalse(applied);
            assertEquals(0, applyCalls.get());
        } finally {
            engine.clear();
        }
    }

    @Test
    public void missingDirectoryFallsBackAndReconcilesOnCreation() throws IOException {
        File directory = new File(temporaryFolder.getRoot(), "created-later");
        ConfigHotloadEngine engine = createEngine(() -> knownConfigFiles(directory));

        try {
            engine.configure(500L, List.of(), List.of(directory));
            assertFalse(engine.isDirectoryEventWatchActive());
            Files.createDirectories(directory.toPath());
            File file = new File(directory, "feature.toml");
            Files.writeString(file.toPath(), "enabled = true\n", StandardCharsets.UTF_8);

            Set<File> touched = engine.pollTouchedFiles();

            assertTrue(touched.contains(file));
            assertTrue(engine.isDirectoryEventWatchActive());
        } finally {
            engine.clear();
            engine.clear();
        }
    }

    private ConfigHotloadEngine createEngine(KnownFilesSupplier knownFilesSupplier) {
        return new ConfigHotloadEngine(
                file -> file != null && file.getName().endsWith(".toml"),
                knownFilesSupplier::get,
                this::readFile,
                this::normalize
        );
    }

    private Collection<File> knownConfigFiles(File directory) {
        File[] files = directory.listFiles(file -> file.isFile() && file.getName().endsWith(".toml"));
        if (files == null) {
            return List.of();
        }

        List<File> known = new ArrayList<>(files.length);
        for (File file : files) {
            known.add(file);
        }
        return known;
    }

    private Set<File> awaitTouchedFile(ConfigHotloadEngine engine, File expected, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        Set<File> touched = Set.of();
        while (System.nanoTime() < deadline) {
            touched = engine.pollTouchedFiles();
            if (touched.contains(expected)) {
                return touched;
            }
            Thread.sleep(25L);
        }
        return touched;
    }

    private String readFile(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }

        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String normalize(String text) {
        return text == null ? null : text.replace("\r\n", "\n").stripTrailing();
    }

    @FunctionalInterface
    private interface KnownFilesSupplier {
        Collection<File> get();
    }
}

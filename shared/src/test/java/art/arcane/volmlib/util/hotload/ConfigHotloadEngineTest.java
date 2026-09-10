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
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

public class ConfigHotloadEngineTest {
    private static final int MAX_VIRTUAL_POLLS = 200;
    private static final long VIRTUAL_POLL_STEP_MILLIS = 25L;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final AtomicLong clock = new AtomicLong();

    @Test
    public void idleEventWatcherDoesNotRescanKnownFilesEveryPoll() throws IOException {
        File directory = temporaryFolder.newFolder("idle-managed");
        File file = new File(directory, "feature.toml");
        Files.writeString(file.toPath(), "enabled = true\n", StandardCharsets.UTF_8);
        AtomicInteger supplierCalls = new AtomicInteger();
        ConfigHotloadEngine engine = createEngine(() -> {
            supplierCalls.incrementAndGet();
            return knownConfigFiles(directory);
        });

        try {
            engine.configure(500L, 100L, List.of(), List.of(directory));
            Assume.assumeTrue(engine.isDirectoryEventWatchActive());
            assertEquals(1, supplierCalls.get());

            assertTrue(engine.pollTouchedFiles().isEmpty());
            assertEquals(1, supplierCalls.get());

            int firstRescanPoll = -1;
            for (int i = 1; i <= 20; i++) {
                assertTrue(engine.pollTouchedFiles().isEmpty());
                if (supplierCalls.get() > 1) {
                    firstRescanPoll = i;
                    break;
                }
            }

            assertTrue(firstRescanPoll >= 2);
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
            engine.configure(500L, 100L, List.of(), List.of(directory));
            Assume.assumeTrue(engine.isDirectoryEventWatchActive());
            Files.writeString(file.toPath(), "enabled = false\nlimit = 42\n", StandardCharsets.UTF_8);

            Set<File> touched = awaitTouchedFile(engine, file);
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
            engine.configure(500L, 100L, List.of(), List.of(directory, missingDirectory));
            Assume.assumeTrue(engine.isDirectoryEventWatchActive());
            assertEquals(1, supplierCalls.get());

            assertTrue(engine.pollTouchedFiles().isEmpty());
            assertEquals(1, supplierCalls.get());

            int firstRescanPoll = -1;
            for (int i = 1; i <= 20; i++) {
                assertTrue(engine.pollTouchedFiles().isEmpty());
                if (supplierCalls.get() > 1) {
                    firstRescanPoll = i;
                    break;
                }
            }

            assertTrue(firstRescanPoll >= 2);
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
            engine.configure(500L, 100L, List.of(), List.of(directory));
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
    public void missingDirectoryFallsBackAndReconcilesOnCreation() throws Exception {
        File directory = new File(temporaryFolder.getRoot(), "created-later");
        ConfigHotloadEngine engine = createEngine(() -> knownConfigFiles(directory));

        try {
            engine.configure(500L, 100L, List.of(), List.of(directory));
            assertFalse(engine.isDirectoryEventWatchActive());
            Files.createDirectories(directory.toPath());
            File file = new File(directory, "feature.toml");
            Files.writeString(file.toPath(), "enabled = true\n", StandardCharsets.UTF_8);

            Set<File> touched = awaitTouchedFile(engine, file);

            assertTrue(touched.contains(file));
            assertTrue(engine.isDirectoryEventWatchActive());
        } finally {
            engine.clear();
            engine.clear();
        }
    }

    @Test
    public void acknowledgedSelectionRejectsAlreadyCapturedOldConfig() throws Exception {
        File directory = temporaryFolder.newFolder("selected-language");
        File file = new File(directory, "adapt.toml");
        Files.writeString(file.toPath(), "language = \"en_US\"\n", StandardCharsets.UTF_8);
        ConfigHotloadEngine engine = createEngine(() -> knownConfigFiles(directory));

        try {
            engine.configure(100L, 100L, List.of(file), List.of(directory));
            Files.writeString(file.toPath(), "language = \"en_US\"\nverbose = true\n", StandardCharsets.UTF_8);
            ConfigHotloadEngine.StableContentSnapshot oldConfig = awaitTouchedSnapshot(engine, file);
            String selected = "language = \"fi_FI\"\nverbose = true\n";
            Files.writeString(file.toPath(), selected, StandardCharsets.UTF_8);
            engine.noteSelfWrite(file, selected);
            AtomicReference<String> active = new AtomicReference<>("fi_FI");
            AtomicInteger notifications = new AtomicInteger();

            assertFalse(engine.processSnapshotChange(oldConfig, snapshot -> {
                active.set("en_US");
                return true;
            }, delta -> notifications.incrementAndGet()));
            assertEquals("fi_FI", active.get());
            assertEquals(0, notifications.get());

            Files.writeString(file.toPath(), "language = \"de_DE\"\nverbose = true\n", StandardCharsets.UTF_8);
            ConfigHotloadEngine.StableContentSnapshot external = awaitTouchedSnapshot(engine, file);
            assertTrue(engine.processSnapshotChange(external, snapshot -> {
                active.set("de_DE");
                return true;
            }, delta -> notifications.incrementAndGet()));
            assertEquals("de_DE", active.get());
            assertEquals(1, notifications.get());
        } finally {
            engine.clear();
        }
    }

    @Test
    public void acknowledgedLanguageInstallDoesNotAnnounceEveryNewEntry() throws Exception {
        File directory = temporaryFolder.newFolder("installed-language");
        File file = new File(directory, "fi_FI.toml");
        ConfigHotloadEngine engine = createEngine(() -> knownConfigFiles(directory));

        try {
            engine.configure(100L, 100L, List.of(), List.of(directory));
            String installed = "[runtime]\nno_description_provided = \"Ei kuvausta\"\n";
            Files.writeString(file.toPath(), installed, StandardCharsets.UTF_8);
            ConfigHotloadEngine.StableContentSnapshot downloaded = awaitTouchedSnapshot(engine, file);
            engine.noteSelfWrite(file, installed);
            AtomicInteger notifications = new AtomicInteger();

            assertFalse(engine.processSnapshotChange(downloaded, snapshot -> true,
                    delta -> notifications.incrementAndGet()));
            assertEquals(0, notifications.get());
        } finally {
            engine.clear();
        }
    }

    @Test(timeout = 8_000L)
    public void trackedWriteFinishesBeforeCapturedSnapshotCanApply() throws Exception {
        File directory = temporaryFolder.newFolder("concurrent-selection");
        File file = new File(directory, "adapt.toml");
        Files.writeString(file.toPath(), "language = \"en_US\"\n", StandardCharsets.UTF_8);
        ConfigHotloadEngine engine = createEngine(() -> knownConfigFiles(directory));
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch written = new CountDownLatch(1);
        CountDownLatch applyStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            engine.configure(100L, 100L, List.of(file), List.of());
            Files.writeString(file.toPath(), "language = \"en_US\"\nverbose = true\n", StandardCharsets.UTF_8);
            ConfigHotloadEngine.StableContentSnapshot captured = awaitTouchedSnapshot(engine, file);
            Future<String> selection = workers.submit(() -> engine.write(file, () -> {
                String raw = "language = \"fi_FI\"\n";
                Files.writeString(file.toPath(), raw, StandardCharsets.UTF_8);
                written.countDown();
                assertTrue(release.await(5L, TimeUnit.SECONDS));
                return new ConfigHotloadEngine.Written<>(raw, "fi_FI");
            }));
            assertTrue(written.await(5L, TimeUnit.SECONDS));
            AtomicInteger applies = new AtomicInteger();
            Future<Boolean> hotload = workers.submit(() -> {
                applyStarted.countDown();
                return engine.processSnapshotChange(captured, snapshot -> {
                    applies.incrementAndGet();
                    return true;
                }, null);
            });
            assertTrue(applyStarted.await(5L, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> hotload.get(100L, TimeUnit.MILLISECONDS));
            release.countDown();

            assertEquals("fi_FI", selection.get(5L, TimeUnit.SECONDS));
            assertFalse(hotload.get(5L, TimeUnit.SECONDS));
            assertEquals(0, applies.get());
        } finally {
            release.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(2L, TimeUnit.SECONDS));
            engine.clear();
        }
    }

    @Test(timeout = 8_000L)
    public void silentDirectoryWatcherStillDetectsModification() throws Exception {
        File directory = temporaryFolder.newFolder("silent-managed");
        File file = new File(directory, "feature.toml");
        Files.writeString(file.toPath(), "enabled = true\n", StandardCharsets.UTF_8);
        ConfigHotloadEngine engine = new ConfigHotloadEngine(
                watched -> watched != null && watched.getName().endsWith(".toml"),
                () -> knownConfigFiles(directory),
                this::readFile,
                this::normalize,
                200L,
                100L,
                clock::get
        );

        try {
            engine.configure(100L, 100L, List.of(), List.of(directory));
            Assume.assumeTrue(engine.isDirectoryEventWatchActive());
            engine.suppressDirectoryEventDelivery(true);
            Files.writeString(file.toPath(), "enabled = false\nlimit = 7\n", StandardCharsets.UTF_8);

            Set<File> touched = awaitTouchedFile(engine, file);
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
    public void failedApplyKeepsPreviousContentForLaterSave() throws IOException {
        File directory = temporaryFolder.newFolder("failed-apply-managed");
        File file = new File(directory, "feature.toml");
        Files.writeString(file.toPath(), "enabled = true\n", StandardCharsets.UTF_8);
        ConfigHotloadEngine engine = createEngine(() -> knownConfigFiles(directory));

        try {
            engine.configure(500L, 100L, List.of(), List.of(directory));
            Files.writeString(file.toPath(), "enabled = false\n", StandardCharsets.UTF_8);
            AtomicInteger applyCalls = new AtomicInteger();

            boolean first = engine.processFileChange(file, changedFile -> {
                applyCalls.incrementAndGet();
                return false;
            }, null);
            assertFalse(first);
            assertEquals(1, applyCalls.get());

            Files.writeString(file.toPath(), "enabled = false\nlimit = 3\n", StandardCharsets.UTF_8);
            boolean second = engine.processFileChange(file, changedFile -> {
                applyCalls.incrementAndGet();
                return true;
            }, null);
            assertTrue(second);
            assertEquals(2, applyCalls.get());
        } finally {
            engine.clear();
        }
    }

    @Test
    public void nullContentWithReconciledSignatureReachesApplyOnce() throws Exception {
        File file = new File(temporaryFolder.getRoot(), "unreadable.toml");
        Files.writeString(file.toPath(), "readable", StandardCharsets.UTF_8);
        AtomicBoolean readerUnavailable = new AtomicBoolean();
        ConfigHotloadEngine engine = new ConfigHotloadEngine(
                watched -> watched != null && watched.getName().endsWith(".toml"),
                () -> List.of(file),
                watched -> readerUnavailable.get() ? null : readFile(watched),
                this::normalize,
                100L,
                100L,
                clock::get
        );

        try {
            engine.configure(100L, 100L, List.of(), List.of());
            readerUnavailable.set(true);
            Files.writeString(file.toPath(), "unreadable", StandardCharsets.UTF_8);
            ConfigHotloadEngine.StableContentSnapshot unreadable = awaitTouchedSnapshot(engine, file);
            AtomicInteger applyCalls = new AtomicInteger();

            assertFalse(engine.processSnapshotChange(unreadable, snapshot -> {
                applyCalls.incrementAndGet();
                return false;
            }, null));
            assertFalse(engine.processSnapshotChange(unreadable, snapshot -> {
                applyCalls.incrementAndGet();
                return false;
            }, null));

            assertEquals(1, applyCalls.get());
        } finally {
            engine.clear();
        }
    }

    @Test
    public void nullContentForNewReconciledFileReachesApplyOnce() throws Exception {
        File directory = temporaryFolder.newFolder("new-unreadable-managed");
        File file = new File(directory, "unreadable.toml");
        ConfigHotloadEngine engine = new ConfigHotloadEngine(
                watched -> watched != null && watched.getName().endsWith(".toml"),
                () -> knownConfigFiles(directory),
                ignored -> null,
                this::normalize,
                100L,
                100L,
                clock::get
        );

        try {
            engine.configure(100L, 100L, List.of(), List.of());
            Files.writeString(file.toPath(), "unreadable", StandardCharsets.UTF_8);
            ConfigHotloadEngine.StableContentSnapshot unreadable = awaitTouchedSnapshot(engine, file);
            AtomicInteger applyCalls = new AtomicInteger();

            assertFalse(engine.processSnapshotChange(unreadable, snapshot -> {
                applyCalls.incrementAndGet();
                return false;
            }, null));
            assertFalse(engine.processSnapshotChange(unreadable, snapshot -> {
                applyCalls.incrementAndGet();
                return false;
            }, null));

            assertEquals(1, applyCalls.get());
        } finally {
            engine.clear();
        }
    }

    @Test(timeout = 8_000L)
    public void contentReconciliationDetectsSameMetadataEdit() throws Exception {
        File directory = temporaryFolder.newFolder("same-metadata-managed");
        File file = new File(directory, "feature.toml");
        Files.writeString(file.toPath(), "value = 1\n", StandardCharsets.UTF_8);
        FileTime originalModified = Files.getLastModifiedTime(file.toPath());
        ConfigHotloadEngine engine = new ConfigHotloadEngine(
                watched -> watched != null && watched.getName().endsWith(".toml"),
                () -> knownConfigFiles(directory),
                this::readFile,
                this::normalize,
                200L,
                100L,
                clock::get
        );

        try {
            engine.configure(100L, 100L, List.of(), List.of(directory));
            engine.suppressDirectoryEventDelivery(true);
            Files.writeString(file.toPath(), "value = 2\n", StandardCharsets.UTF_8);
            Files.setLastModifiedTime(file.toPath(), originalModified);

            assertTrue(awaitTouchedFile(engine, file).contains(file));
        } finally {
            engine.clear();
        }
    }

    @Test(timeout = 8_000L)
    public void contentReconciliationSlicesAndFindsALateSilentEditWithoutWatchers() throws Exception {
        File directory = temporaryFolder.newFolder("sliced-reconciliation-managed");
        List<File> files = new ArrayList<>();
        for (int i = 0; i < 96; i++) {
            File file = new File(directory, String.format("feature-%03d.toml", i));
            Files.writeString(file.toPath(), "value = " + i + "\n", StandardCharsets.UTF_8);
            files.add(file);
        }
        AtomicInteger readerCalls = new AtomicInteger();
        ConfigHotloadEngine engine = new ConfigHotloadEngine(
                watched -> watched != null && watched.getName().endsWith(".toml"),
                () -> files,
                file -> {
                    readerCalls.incrementAndGet();
                    return readFile(file);
                },
                this::normalize,
                200L,
                100L,
                clock::get
        );

        try {
            engine.configure(100L, 100L, List.of(), List.of());
            assertFalse(engine.isDirectoryEventWatchActive());
            readerCalls.set(0);
            File changedFile = files.get(files.size() - 1);
            FileTime originalModified = Files.getLastModifiedTime(changedFile.toPath());
            Files.writeString(changedFile.toPath(), "value = 96\n", StandardCharsets.UTF_8);
            Files.setLastModifiedTime(changedFile.toPath(), originalModified);

            assertTrue(engine.pollTouchedFiles().isEmpty());
            int firstSliceReads = readerCalls.get();
            assertTrue(firstSliceReads > 0);
            assertTrue(firstSliceReads <= ConfigHotloadEngine.RECONCILIATION_FILE_BUDGET);

            Set<File> touched = Set.of();
            int polls = 1;
            while (!touched.contains(changedFile) && polls < 12) {
                touched = engine.pollTouchedFiles();
                polls++;
            }

            assertTrue(touched.contains(changedFile));
            assertTrue(readerCalls.get() > ConfigHotloadEngine.RECONCILIATION_FILE_BUDGET);
            assertTrue(polls > 2);
        } finally {
            engine.clear();
        }
    }

    @Test(timeout = 8_000L)
    public void changeDuringApplyRemainsQueuedBehindCooldown() throws Exception {
        File directory = temporaryFolder.newFolder("apply-race-managed");
        File file = new File(directory, "feature.toml");
        Files.writeString(file.toPath(), "value = 1\n", StandardCharsets.UTF_8);
        ConfigHotloadEngine engine = createEngine(() -> knownConfigFiles(directory));

        try {
            engine.configure(100L, 250L, List.of(file), List.of());
            Files.writeString(file.toPath(), "value = 2\n", StandardCharsets.UTF_8);
            assertTrue(awaitTouchedFile(engine, file).contains(file));

            assertTrue(engine.processFileChange(file, changedFile -> {
                try {
                    Files.writeString(changedFile.toPath(), "value = 3\n", StandardCharsets.UTF_8);
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
                return true;
            }, null));

            assertTrue(engine.pollTouchedFiles().isEmpty());
            assertTrue(awaitTouchedFile(engine, file).contains(file));
            AtomicInteger applies = new AtomicInteger();
            assertTrue(engine.processFileChange(file, changedFile -> {
                applies.incrementAndGet();
                return true;
            }, null));
            assertEquals(1, applies.get());
        } finally {
            engine.clear();
        }
    }

    @Test(timeout = 8_000L)
    public void snapshotApplyUsesStableQueuedContentWhenFileChangesBeforeApply() throws Exception {
        File directory = temporaryFolder.newFolder("stable-snapshot-managed");
        File file = new File(directory, "feature.toml");
        Files.writeString(file.toPath(), "value = 1\n", StandardCharsets.UTF_8);
        ConfigHotloadEngine engine = createEngine(() -> knownConfigFiles(directory));

        try {
            engine.configure(100L, 250L, List.of(file), List.of());
            Files.writeString(file.toPath(), "value = 2\n", StandardCharsets.UTF_8);
            ConfigHotloadEngine.StableContentSnapshot snapshot = awaitTouchedSnapshot(engine, file);
            Files.writeString(file.toPath(), "value = 3\n", StandardCharsets.UTF_8);
            AtomicReference<String> appliedContent = new AtomicReference<>();

            assertTrue(engine.processSnapshotChange(snapshot, stable -> {
                appliedContent.set(stable.normalizedContent());
                return true;
            }, null));

            assertEquals("value = 2", appliedContent.get());
            ConfigHotloadEngine.StableContentSnapshot trailing = awaitTouchedSnapshot(engine, file);
            assertEquals("value = 3", trailing.normalizedContent());
        } finally {
            engine.clear();
        }
    }

    @Test(timeout = 8_000L)
    public void failedSnapshotApplyRetriesWithoutAnotherFilesystemEvent() throws Exception {
        File directory = temporaryFolder.newFolder("snapshot-retry-managed");
        File file = new File(directory, "feature.toml");
        Files.writeString(file.toPath(), "value = 1\n", StandardCharsets.UTF_8);
        ConfigHotloadEngine engine = createEngine(() -> knownConfigFiles(directory));

        try {
            engine.configure(100L, 250L, List.of(file), List.of());
            Files.writeString(file.toPath(), "value = 2\n", StandardCharsets.UTF_8);
            ConfigHotloadEngine.StableContentSnapshot snapshot = awaitTouchedSnapshot(engine, file);

            assertFalse(engine.processSnapshotChange(snapshot, ignored -> false, null));
            assertTrue(engine.pollTouchedSnapshots().isEmpty());

            ConfigHotloadEngine.StableContentSnapshot retry = awaitTouchedSnapshot(engine, file);
            assertEquals(snapshot.signature(), retry.signature());
            assertEquals(snapshot.normalizedContent(), retry.normalizedContent());
        } finally {
            engine.clear();
        }
    }

    @Test(timeout = 8_000L)
    public void throwingSnapshotApplyRetriesWithoutAnotherFilesystemEvent() throws Exception {
        File directory = temporaryFolder.newFolder("snapshot-throw-retry-managed");
        File file = new File(directory, "feature.toml");
        Files.writeString(file.toPath(), "value = 1\n", StandardCharsets.UTF_8);
        ConfigHotloadEngine engine = createEngine(() -> knownConfigFiles(directory));

        try {
            engine.configure(100L, 250L, List.of(file), List.of());
            Files.writeString(file.toPath(), "value = 2\n", StandardCharsets.UTF_8);
            ConfigHotloadEngine.StableContentSnapshot snapshot = awaitTouchedSnapshot(engine, file);

            try {
                engine.processSnapshotChange(snapshot, ignored -> {
                    throw new IllegalStateException("apply failed");
                }, null);
                throw new AssertionError("expected apply failure");
            } catch (IllegalStateException expected) {
                assertEquals("apply failed", expected.getMessage());
            }

            ConfigHotloadEngine.StableContentSnapshot retry = awaitTouchedSnapshot(engine, file);
            assertEquals(snapshot.signature(), retry.signature());
            assertEquals(snapshot.normalizedContent(), retry.normalizedContent());
        } finally {
            engine.clear();
        }
    }

    @Test(timeout = 8_000L)
    public void cooldownStartsWhenApplyCompletes() throws Exception {
        File directory = temporaryFolder.newFolder("completion-cooldown-managed");
        File file = new File(directory, "feature.toml");
        Files.writeString(file.toPath(), "value = 1\n", StandardCharsets.UTF_8);
        ConfigHotloadEngine engine = createEngine(() -> knownConfigFiles(directory));

        try {
            engine.configure(50L, 300L, List.of(file), List.of());
            Files.writeString(file.toPath(), "value = 2\n", StandardCharsets.UTF_8);
            ConfigHotloadEngine.StableContentSnapshot first = awaitTouchedSnapshot(engine, file);
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(250L));
            assertTrue(engine.processSnapshotChange(first, ignored -> true, null));

            Files.writeString(file.toPath(), "value = 3\n", StandardCharsets.UTF_8);
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(100L));
            assertTrue(engine.pollTouchedSnapshots().isEmpty());
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(100L));
            assertTrue(engine.pollTouchedSnapshots().isEmpty());

            ConfigHotloadEngine.StableContentSnapshot trailing = awaitTouchedSnapshot(engine, file);
            assertEquals("value = 3", trailing.normalizedContent());
        } finally {
            engine.clear();
        }
    }

    private ConfigHotloadEngine createEngine(KnownFilesSupplier knownFilesSupplier) {
        return new ConfigHotloadEngine(
                file -> file != null && file.getName().endsWith(".toml"),
                knownFilesSupplier::get,
                this::readFile,
                this::normalize,
                ConfigHotloadEngine.DEFAULT_FULL_WATCH_SCAN_WINDOW_MS,
                ConfigHotloadEngine.DEFAULT_SIGNATURE_SCAN_WINDOW_MS,
                clock::get
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

    private Set<File> awaitTouchedFile(ConfigHotloadEngine engine, File expected) {
        Set<File> touched = Set.of();
        for (int poll = 0; poll < MAX_VIRTUAL_POLLS; poll++) {
            touched = engine.pollTouchedFiles();
            if (touched.contains(expected)) {
                return touched;
            }
            advanceVirtualClock();
        }
        return touched;
    }

    private ConfigHotloadEngine.StableContentSnapshot awaitTouchedSnapshot(ConfigHotloadEngine engine, File expected) {
        for (int poll = 0; poll < MAX_VIRTUAL_POLLS; poll++) {
            Set<ConfigHotloadEngine.StableContentSnapshot> snapshots = engine.pollTouchedSnapshots();
            for (ConfigHotloadEngine.StableContentSnapshot snapshot : snapshots) {
                if (snapshot.file().equals(expected.getAbsoluteFile())) {
                    return snapshot;
                }
            }
            advanceVirtualClock();
        }
        throw new AssertionError("Timed out waiting for stable snapshot of " + expected);
    }

    private void advanceVirtualClock() {
        clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(VIRTUAL_POLL_STEP_MILLIS));
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

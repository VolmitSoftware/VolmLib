package art.arcane.volmlib.util.io;

import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReactiveFolderTest {
    @Test
    public void productionCooldownIsThreeSeconds() {
        assertEquals(3_000L, ReactiveFolder.HOTLOAD_COOLDOWN_MILLIS);
        assertEquals(5_000L, ReactiveFolder.FULL_SCAN_INTERVAL_MILLIS);
        assertEquals(2_500L, ReactiveFolder.CONTENT_RECONCILIATION_INTERVAL_MILLIS);
        assertEquals(32, ReactiveFolder.RECONCILIATION_FILE_BUDGET);
    }

    @Test
    public void initialReconciliationDoesNotHotloadExistingFiles() throws Exception {
        Path directory = Files.createTempDirectory("reactive-folder-initial-test");
        Files.writeString(directory.resolve("dimension.json"), "{\"v\":1}", StandardCharsets.UTF_8);
        AtomicInteger hotloads = new AtomicInteger();
        ReactiveFolder folder = null;
        try {
            folder = new ReactiveFolder(
                    directory.toFile(),
                    (created, changed, deleted) -> hotloads.incrementAndGet(),
                    new KList<>(".json"),
                    new KList<>(),
                    new KList<>()
            );

            for (int index = 0; index < 6; index++) {
                assertFalse(folder.check());
            }
            assertEquals(0, hotloads.get());
        } finally {
            if (folder != null) {
                folder.clear();
            }
            Files.walk(directory)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Test
    public void checkReturnsDetectedWatchedChanges() throws Exception {
        Path directory = Files.createTempDirectory("reactive-folder-test");
        Path watchedFile = directory.resolve("dimension.json");
        Files.writeString(watchedFile, "{\"v\":1}", StandardCharsets.UTF_8);
        AtomicInteger hotloads = new AtomicInteger();

        ReactiveFolder folder = null;
        try {
            folder = new ReactiveFolder(
                    directory.toFile(),
                    (created, changed, deleted) -> hotloads.incrementAndGet(),
                    new KList<>(".json"),
                    new KList<>(),
                    new KList<>()
            );

            Thread.sleep(20L);
            Files.writeString(watchedFile, "{\"v\":2}", StandardCharsets.UTF_8);

            assertFalse(folder.check());
            Thread.sleep(ReactiveFolder.STABILITY_WINDOW_MILLIS + 50L);
            boolean detected = folder.check();

            assertTrue(detected);
            assertEquals(1, hotloads.get());
        } finally {
            if (folder != null) {
                folder.clear();
            }
            Files.walk(directory)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Test(timeout = 8_000L)
    public void burstDuringCooldownAppliesLatestContentOnce() throws Exception {
        Path directory = Files.createTempDirectory("reactive-folder-burst-test");
        Path watchedFile = directory.resolve("dimension.json");
        Files.writeString(watchedFile, "{\"v\":1}", StandardCharsets.UTF_8);
        AtomicLong clock = new AtomicLong();
        AtomicInteger hotloads = new AtomicInteger();
        AtomicReference<String> applied = new AtomicReference<>();

        ReactiveFolder folder = null;
        try {
            folder = new ReactiveFolder(
                    directory.toFile(),
                    (created, changed, deleted) -> {
                        hotloads.incrementAndGet();
                        try {
                            applied.set(Files.readString(watchedFile, StandardCharsets.UTF_8));
                        } catch (Exception failure) {
                            throw new IllegalStateException(failure);
                        }
                    },
                    new KList<>(".json"),
                    new KList<>(),
                    new KList<>(),
                    clock::get
            );
            completeReconciliation(folder);
            Field watcherField = ReactiveFolder.class.getDeclaredField("fw");
            watcherField.setAccessible(true);
            ((FolderWatcher) watcherField.get(folder)).clear();

            Files.writeString(watchedFile, "{\"v\":2}", StandardCharsets.UTF_8);
            prioritize(folder, watchedFile);
            assertFalse(folder.check());
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(ReactiveFolder.STABILITY_WINDOW_MILLIS + 1L));
            assertTrue(folder.check());

            Files.writeString(watchedFile, "{\"v\":3}", StandardCharsets.UTF_8);
            prioritize(folder, watchedFile);
            clock.incrementAndGet();
            assertFalse(folder.check());
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(ReactiveFolder.STABILITY_WINDOW_MILLIS + 1L));
            assertFalse(folder.check());
            Files.writeString(watchedFile, "{\"v\":4}", StandardCharsets.UTF_8);
            prioritize(folder, watchedFile);
            clock.incrementAndGet();
            assertFalse(folder.check());
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(ReactiveFolder.HOTLOAD_COOLDOWN_MILLIS + 1L));

            assertTrue(folder.check());
            assertEquals(2, hotloads.get());
            assertEquals("{\"v\":4}", applied.get());
            assertFalse(folder.check());
        } finally {
            if (folder != null) {
                folder.clear();
            }
            Files.walk(directory)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Test
    public void editorTemporaryFilesDoNotTriggerHotload() throws Exception {
        Path directory = Files.createTempDirectory("reactive-folder-temp-test");
        AtomicInteger hotloads = new AtomicInteger();
        ReactiveFolder folder = null;
        try {
            folder = new ReactiveFolder(
                    directory.toFile(),
                    (created, changed, deleted) -> hotloads.incrementAndGet(),
                    new KList<>(".json"),
                    new KList<>(),
                    new KList<>()
            );

            Files.writeString(directory.resolve("dimension.tmp.json"), "{}", StandardCharsets.UTF_8);
            assertFalse(folder.check());
            Thread.sleep(ReactiveFolder.STABILITY_WINDOW_MILLIS + 50L);
            assertFalse(folder.check());
            assertEquals(0, hotloads.get());
        } finally {
            if (folder != null) {
                folder.clear();
            }
            Files.walk(directory)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Test
    public void reconciliationFindsSilentSameMetadataChange() throws Exception {
        Path directory = Files.createTempDirectory("reactive-folder-reconciliation-test");
        Path watchedFile = directory.resolve("dimension.json");
        Files.writeString(watchedFile, "{\"v\":1}", StandardCharsets.UTF_8);
        FileTime originalTime = Files.getLastModifiedTime(watchedFile);
        AtomicLong clock = new AtomicLong();
        AtomicInteger hotloads = new AtomicInteger();
        ReactiveFolder folder = null;
        try {
            folder = new ReactiveFolder(
                    directory.toFile(),
                    (created, changed, deleted) -> hotloads.incrementAndGet(),
                    new KList<>(".json"),
                    new KList<>(),
                    new KList<>(),
                    clock::get
            );

            folder.check();
            Field watcherField = ReactiveFolder.class.getDeclaredField("fw");
            watcherField.setAccessible(true);
            FolderWatcher watcher = (FolderWatcher) watcherField.get(folder);
            watcher.clear();

            Files.writeString(watchedFile, "{\"v\":2}", StandardCharsets.UTF_8);
            Files.setLastModifiedTime(watchedFile, originalTime);
            clock.set(TimeUnit.MILLISECONDS.toNanos(ReactiveFolder.CONTENT_RECONCILIATION_INTERVAL_MILLIS));
            assertFalse(folder.check());
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(ReactiveFolder.STABILITY_WINDOW_MILLIS + 1L));

            assertTrue(folder.check());
            assertEquals(1, hotloads.get());
        } finally {
            if (folder != null) {
                folder.clear();
            }
            Files.walk(directory)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Test(timeout = 8_000L)
    public void largeBurstWaitsForOneCompleteVerifiedBatch() throws Exception {
        Path directory = Files.createTempDirectory("reactive-folder-large-burst-test");
        AtomicLong clock = new AtomicLong();
        AtomicInteger hotloads = new AtomicInteger();
        AtomicBoolean completeBatch = new AtomicBoolean();
        ReactiveFolder folder = null;
        try {
            for (int index = 0; index < 300; index++) {
                Files.writeString(directory.resolve("entry-" + index + ".json"), "0", StandardCharsets.UTF_8);
            }
            folder = new ReactiveFolder(
                    directory.toFile(),
                    (created, changed, deleted) -> {
                        hotloads.incrementAndGet();
                        completeBatch.set(created.size() + changed.size() >= 300);
                    },
                    new KList<>(".json"),
                    new KList<>(),
                    new KList<>(),
                    clock::get
            );
            for (int index = 0; index < 10; index++) {
                assertFalse(folder.check());
            }
            Field watcherField = ReactiveFolder.class.getDeclaredField("fw");
            watcherField.setAccessible(true);
            FolderWatcher watcher = (FolderWatcher) watcherField.get(folder);
            watcher.clear();

            for (int index = 0; index < 300; index++) {
                Files.writeString(directory.resolve("entry-" + index + ".json"), "1", StandardCharsets.UTF_8);
            }
            clock.set(TimeUnit.MILLISECONDS.toNanos(ReactiveFolder.CONTENT_RECONCILIATION_INTERVAL_MILLIS));
            for (int index = 0; index < 10; index++) {
                assertFalse(folder.check());
            }
            assertEquals(0, hotloads.get());
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(ReactiveFolder.STABILITY_WINDOW_MILLIS + 1L));

            assertTrue(folder.check());
            assertEquals(1, hotloads.get());
            assertTrue(completeBatch.get());
        } finally {
            if (folder != null) {
                folder.clear();
            }
            Files.walk(directory)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Test
    public void slicedReconciliationDoesNotPublishASeparatedPartialBatch() throws Exception {
        Path directory = Files.createTempDirectory("reactive-folder-separated-burst-test");
        AtomicLong clock = new AtomicLong();
        AtomicInteger hotloads = new AtomicInteger();
        AtomicInteger batchSize = new AtomicInteger();
        ReactiveFolder folder = null;
        try {
            Path early = directory.resolve("early.json");
            Files.writeString(early, "0", StandardCharsets.UTF_8);
            for (int index = 0; index < 96; index++) {
                Files.writeString(directory.resolve("unchanged-" + index + ".json"), "0", StandardCharsets.UTF_8);
            }
            Path nested = Files.createDirectories(directory.resolve("late"));
            Path late = nested.resolve("late.json");
            Files.writeString(late, "0", StandardCharsets.UTF_8);
            folder = new ReactiveFolder(
                    directory.toFile(),
                    (created, changed, deleted) -> {
                        hotloads.incrementAndGet();
                        batchSize.set(created.size() + changed.size() + deleted.size());
                    },
                    new KList<>(".json"),
                    new KList<>(),
                    new KList<>(),
                    clock::get
            );
            completeReconciliation(folder);
            Field watcherField = ReactiveFolder.class.getDeclaredField("fw");
            watcherField.setAccessible(true);
            ((FolderWatcher) watcherField.get(folder)).clear();

            Files.writeString(early, "1", StandardCharsets.UTF_8);
            Files.writeString(late, "1", StandardCharsets.UTF_8);
            prioritize(folder, early);
            clock.set(TimeUnit.MILLISECONDS.toNanos(ReactiveFolder.CONTENT_RECONCILIATION_INTERVAL_MILLIS));
            assertFalse(folder.check());
            assertTrue(reconciliationActive(folder));

            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(ReactiveFolder.STABILITY_WINDOW_MILLIS + 1L));
            assertFalse(folder.check());
            assertEquals(0, hotloads.get());
            completeReconciliation(folder);
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(ReactiveFolder.STABILITY_WINDOW_MILLIS + 1L));

            assertTrue(folder.check());
            assertEquals(1, hotloads.get());
            assertEquals(2, batchSize.get());
        } finally {
            if (folder != null) {
                folder.clear();
            }
            Files.walk(directory)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Test
    public void longReconciliationSchedulesTheNextWindowFromCompletion() throws Exception {
        Path directory = Files.createTempDirectory("reactive-folder-completion-window-test");
        AtomicLong clock = new AtomicLong();
        ReactiveFolder folder = null;
        try {
            for (int index = 0; index < 96; index++) {
                Files.writeString(directory.resolve("entry-" + index + ".json"), "0", StandardCharsets.UTF_8);
            }
            folder = new ReactiveFolder(
                    directory.toFile(),
                    (created, changed, deleted) -> {
                    },
                    new KList<>(".json"),
                    new KList<>(),
                    new KList<>(),
                    clock::get
            );

            assertFalse(folder.check());
            assertTrue(reconciliationActive(folder));
            clock.set(TimeUnit.SECONDS.toNanos(10L));
            completeReconciliation(folder);

            Field deadlineField = ReactiveFolder.class.getDeclaredField("nextReconciliationAtNanos");
            deadlineField.setAccessible(true);
            assertEquals(
                    TimeUnit.SECONDS.toNanos(10L)
                            + TimeUnit.MILLISECONDS.toNanos(ReactiveFolder.CONTENT_RECONCILIATION_INTERVAL_MILLIS),
                    deadlineField.getLong(folder)
            );
        } finally {
            if (folder != null) {
                folder.clear();
            }
            Files.walk(directory)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Test(timeout = 8_000L)
    public void failedHotloadRetainsTheQueuedBatchForRetry() throws Exception {
        Path directory = Files.createTempDirectory("reactive-folder-retry-test");
        Path watchedFile = directory.resolve("dimension.json");
        Files.writeString(watchedFile, "{\"v\":1}", StandardCharsets.UTF_8);
        AtomicInteger attempts = new AtomicInteger();
        ReactiveFolder folder = null;
        try {
            folder = new ReactiveFolder(
                    directory.toFile(),
                    (created, changed, deleted) -> {
                        if (attempts.incrementAndGet() == 1) {
                            throw new IllegalStateException("retry");
                        }
                    },
                    new KList<>(".json"),
                    new KList<>(),
                    new KList<>()
            );

            Files.writeString(watchedFile, "{\"v\":2}", StandardCharsets.UTF_8);
            assertFalse(folder.check());
            Thread.sleep(ReactiveFolder.STABILITY_WINDOW_MILLIS + 50L);
            try {
                folder.check();
                throw new AssertionError("expected hotload failure");
            } catch (IllegalStateException expected) {
                assertEquals("retry", expected.getMessage());
            }

            assertFalse(folder.check());
            Thread.sleep(ReactiveFolder.HOTLOAD_COOLDOWN_MILLIS + 100L);
            assertTrue(folder.check());
            assertEquals(2, attempts.get());
        } finally {
            if (folder != null) {
                folder.clear();
            }
            Files.walk(directory)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    private boolean awaitHotload(ReactiveFolder folder, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        do {
            if (folder.check()) {
                return true;
            }
            Thread.sleep(25L);
        } while (System.nanoTime() < deadline);
        return false;
    }

    private void completeReconciliation(ReactiveFolder folder) throws Exception {
        for (int pass = 0; pass < 1_000; pass++) {
            folder.check();
            if (!reconciliationActive(folder)) {
                return;
            }
        }
        throw new AssertionError("Reactive folder reconciliation did not complete");
    }

    private boolean reconciliationActive(ReactiveFolder folder) throws Exception {
        Field cycleField = ReactiveFolder.class.getDeclaredField("reconciliationCycleActive");
        cycleField.setAccessible(true);
        Field digestField = ReactiveFolder.class.getDeclaredField("reconciliationDigest");
        digestField.setAccessible(true);
        return cycleField.getBoolean(folder) || digestField.get(folder) != null;
    }

    private void prioritize(ReactiveFolder folder, Path file) throws Exception {
        Field priorityField = ReactiveFolder.class.getDeclaredField("reconciliationPriority");
        priorityField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<File> priority = (Set<File>) priorityField.get(folder);
        priority.add(file.toFile().getAbsoluteFile());
    }
}

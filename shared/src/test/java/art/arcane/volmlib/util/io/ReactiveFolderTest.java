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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReactiveFolderTest {
    @Test
    public void productionCooldownIsThreeSeconds() {
        assertEquals(3_000L, ReactiveFolder.HOTLOAD_COOLDOWN_MILLIS);
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
                    new KList<>()
            );

            Files.writeString(watchedFile, "{\"v\":2}", StandardCharsets.UTF_8);
            assertFalse(folder.check());
            Thread.sleep(ReactiveFolder.STABILITY_WINDOW_MILLIS + 50L);
            assertTrue(folder.check());

            Files.writeString(watchedFile, "{\"v\":3}", StandardCharsets.UTF_8);
            assertFalse(folder.check());
            Thread.sleep(ReactiveFolder.STABILITY_WINDOW_MILLIS + 50L);
            assertFalse(folder.check());
            Files.writeString(watchedFile, "{\"v\":4}", StandardCharsets.UTF_8);
            Thread.sleep(100L);
            assertFalse(folder.check());
            Thread.sleep(ReactiveFolder.HOTLOAD_COOLDOWN_MILLIS + 100L);

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

            folder.check();
            folder.check();
            folder.check();
            Field watcherField = ReactiveFolder.class.getDeclaredField("fw");
            watcherField.setAccessible(true);
            FolderWatcher watcher = (FolderWatcher) watcherField.get(folder);
            watcher.clear();

            Files.writeString(watchedFile, "{\"v\":2}", StandardCharsets.UTF_8);
            Files.setLastModifiedTime(watchedFile, originalTime);
            folder.check();
            folder.check();
            assertFalse(folder.check());
            Thread.sleep(ReactiveFolder.STABILITY_WINDOW_MILLIS + 50L);

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
                    new KList<>()
            );
            assertFalse(folder.check());
            assertFalse(folder.check());

            for (int index = 0; index < 300; index++) {
                Files.writeString(directory.resolve("entry-" + index + ".json"), "1", StandardCharsets.UTF_8);
            }
            assertFalse(folder.check());
            assertEquals(0, hotloads.get());
            Thread.sleep(ReactiveFolder.STABILITY_WINDOW_MILLIS + 50L);

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
}

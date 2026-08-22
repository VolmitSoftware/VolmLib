package art.arcane.volmlib.util.io;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Equivalence pins for the watcher rework: {@code FileWatcher} now takes one stat per poll instead
 * of four, and {@code FolderWatcher} reports its membership delta from the pass that produces it
 * instead of copying the whole watcher map every poll to diff against.
 */
public class FolderWatcherDeltaTest {
    private Path root;

    @Before
    public void setUp() throws Exception {
        root = Files.createTempDirectory("folder-watcher-delta-test");
    }

    @After
    public void tearDown() throws Exception {
        if (root == null || !Files.exists(root)) {
            return;
        }
        Files.walk(root)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    private static void write(Path file, String content) throws Exception {
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static List<String> names(List<File> files) {
        return files.stream().map(File::getName).sorted().toList();
    }

    @Test
    public void aFreshWatcherReportsNothingForTheDirectoryItAlreadySaw() throws Exception {
        write(root.resolve("a.json"), "1");
        write(root.resolve("b.json"), "1");
        FolderWatcher watcher = new FolderWatcher(root.toFile());

        assertFalse(watcher.checkModified());
        assertTrue(watcher.getCreated().isEmpty());
        assertTrue(watcher.getChanged().isEmpty());
        assertTrue(watcher.getDeleted().isEmpty());
    }

    @Test
    public void createdFilesAreReportedOnceAndThenGoQuiet() throws Exception {
        FolderWatcher watcher = new FolderWatcher(root.toFile());
        write(root.resolve("new.json"), "1");

        assertTrue(watcher.checkModified());
        assertEquals(List.of("new.json"), names(watcher.getCreated()));
        assertTrue(watcher.getChanged().isEmpty());

        assertFalse(watcher.checkModified());
        assertTrue(watcher.getCreated().isEmpty());
    }

    @Test
    public void deletedFilesAreReportedOnceAndThenGoQuiet() throws Exception {
        Path victim = root.resolve("gone.json");
        write(victim, "1");
        FolderWatcher watcher = new FolderWatcher(root.toFile());

        Files.delete(victim);

        assertTrue(watcher.checkModified());
        assertEquals(List.of("gone.json"), names(watcher.getDeleted()));

        assertFalse(watcher.checkModified());
        assertTrue(watcher.getDeleted().isEmpty());
    }

    @Test
    public void contentChangesAreReportedAsChanged() throws Exception {
        Path file = root.resolve("edited.json");
        write(file, "{\"v\":1}");
        FolderWatcher watcher = new FolderWatcher(root.toFile());

        write(file, "{\"v\":22}");

        assertTrue(watcher.checkModified());
        assertEquals(List.of("edited.json"), names(watcher.getChanged()));
        assertTrue(watcher.getCreated().isEmpty());
        assertTrue(watcher.getDeleted().isEmpty());
    }

    @Test
    public void nestedTreesReportTheirOwnDeltas() throws Exception {
        Path nested = root.resolve("nested");
        Files.createDirectory(nested);
        write(nested.resolve("inner.json"), "1");
        FolderWatcher watcher = new FolderWatcher(root.toFile());
        assertFalse(watcher.checkModified());

        write(nested.resolve("added.json"), "1");

        assertTrue(watcher.checkModified());
        assertEquals(List.of("added.json"), names(watcher.getCreated()));
        // The directory itself is reported as changed because its own listing moved.
        assertTrue(names(watcher.getChanged()).contains("nested"));
    }

    @Test
    public void aNewlyCreatedDirectoryReportsItsCompletedContentsInTheSamePass() throws Exception {
        FolderWatcher watcher = new FolderWatcher(root.toFile());
        Path nested = root.resolve("burst");
        Files.createDirectory(nested);
        write(nested.resolve("one.json"), "1");
        write(nested.resolve("two.json"), "1");

        assertTrue(watcher.checkModified());

        assertEquals(List.of("burst", "one.json", "two.json"), names(watcher.getCreated()));
    }

    @Test
    public void theFastPassStillSeesContentChanges() throws Exception {
        Path file = root.resolve("edited.json");
        write(file, "1");
        FolderWatcher watcher = new FolderWatcher(root.toFile());
        assertFalse(watcher.checkModified());

        write(file, "22");

        assertTrue(watcher.checkModifiedFast());
        assertEquals(List.of("edited.json"), names(watcher.getChanged()));
    }

    @Test(timeout = 8_000L)
    public void eventOnlyPassReportsNestedCreateModifyAndDelete() throws Exception {
        Path nested = Files.createDirectory(root.resolve("nested-events"));
        Path existing = nested.resolve("existing.json");
        write(existing, "1");
        FolderWatcher watcher = new FolderWatcher(root.toFile());

        try {
            Assume.assumeTrue(watcher.isEventWatchActive());
            Path created = nested.resolve("created.json");
            write(created, "1");
            assertTrue(awaitEventChange(
                    watcher,
                    candidate -> names(candidate.getCreated()).contains("created.json"),
                    5_000L
            ));

            write(existing, "22");
            assertTrue(awaitEventChange(
                    watcher,
                    candidate -> names(candidate.getChanged()).contains("existing.json"),
                    5_000L
            ));

            Files.delete(existing);
            assertTrue(awaitEventChange(
                    watcher,
                    candidate -> names(candidate.getDeleted()).contains("existing.json"),
                    5_000L
            ));
        } finally {
            watcher.close();
        }
    }

    @Test(timeout = 8_000L)
    public void eventOnlyPassRegistersWhenMissingRootAppears() throws Exception {
        Path watchedRoot = root.resolve("late-root");
        FolderWatcher watcher = new FolderWatcher(watchedRoot.toFile());

        try {
            assertFalse(watcher.isEventWatchActive());
            Path nested = Files.createDirectories(watchedRoot.resolve("nested"));
            Path created = nested.resolve("created.json");
            write(created, "1");

            assertTrue(awaitEventChange(
                    watcher,
                    candidate -> names(candidate.getCreated()).contains("created.json"),
                    5_000L
            ));
            Assume.assumeTrue(watcher.isEventWatchActive());

            write(created, "22");
            assertTrue(awaitEventChange(
                    watcher,
                    candidate -> names(candidate.getChanged()).contains("created.json"),
                    5_000L
            ));
        } finally {
            watcher.close();
        }
    }

    @Test(timeout = 8_000L)
    public void eventOnlyPassRegistersWhenMissingRootAppearsEmpty() throws Exception {
        Path watchedRoot = root.resolve("late-empty-root");
        FolderWatcher watcher = new FolderWatcher(watchedRoot.toFile());

        try {
            assertFalse(watcher.isEventWatchActive());
            Files.createDirectories(watchedRoot);

            assertFalse(watcher.checkModifiedEvents());
            Assume.assumeTrue(watcher.isEventWatchActive());

            Path created = watchedRoot.resolve("created.json");
            write(created, "1");
            assertTrue(awaitEventChange(
                    watcher,
                    candidate -> names(candidate.getCreated()).contains("created.json"),
                    5_000L
            ));
        } finally {
            watcher.close();
        }
    }

    @Test
    public void eventOnlyFilePassDoesNotRestatAnIdleFile() throws Exception {
        Path file = root.resolve("event-file.json");
        write(file, "1");
        CountingFileWatcher watcher = new CountingFileWatcher(file.toFile());

        try {
            Assume.assumeTrue(watcher.isEventWatchActive());
            assertFalse(watcher.checkModifiedEvents());
            assertFalse(watcher.checkModifiedEvents());
            assertEquals(0, watcher.propertiesReadCount());

            write(file, "22");
            assertTrue(awaitFileEventChange(watcher, 5_000L));
            assertTrue(watcher.propertiesReadCount() > 0);
            assertFalse(watcher.checkModified());
        } finally {
            watcher.close();
        }
    }

    @Test
    public void eventOnlyFilePassFallsBackWhenNativeWatchingIsDisabled() throws Exception {
        Path file = root.resolve("fallback-file.json");
        write(file, "1");
        FileWatcher watcher = new FileWatcher(file.toFile(), false);

        try {
            assertFalse(watcher.checkModifiedEvents());
            write(file, "22");
            assertTrue(watcher.checkModifiedEvents());
        } finally {
            watcher.close();
        }
    }

    @Test
    public void eventOnlyFilePassReconcilesWhenMissingParentAppears() throws Exception {
        Path parent = root.resolve("late-parent");
        Path file = parent.resolve("config.json");
        FileWatcher watcher = new FileWatcher(file.toFile());

        try {
            assertFalse(watcher.isEventWatchActive());
            Files.createDirectories(parent);
            write(file, "1");

            assertTrue(watcher.checkModifiedEvents());
            assertTrue(watcher.isEventWatchActive());
        } finally {
            watcher.close();
        }
    }

    @Test
    public void fileWatcherTracksSizeAndAbsence() throws Exception {
        Path file = root.resolve("plain.txt");
        write(file, "one");
        FileWatcher watcher = new FileWatcher(file.toFile());

        assertFalse(watcher.checkModified());
        assertFalse(watcher.wasDeleted());

        write(file, "one-longer");
        assertTrue(watcher.checkModified());
        assertFalse(watcher.checkModified());

        Files.delete(file);
        assertTrue(watcher.checkModified());
        assertTrue(watcher.wasDeleted());
        assertFalse(watcher.checkModified());
    }

    @Test
    public void fileWatcherOnAMissingPathIsStableUntilTheFileAppears() throws Exception {
        Path file = root.resolve("later.txt");
        FileWatcher watcher = new FileWatcher(file.toFile());

        assertFalse(watcher.checkModified());
        assertTrue(watcher.wasDeleted());

        write(file, "here");

        assertTrue(watcher.checkModified());
        assertFalse(watcher.wasDeleted());
    }

    @Test
    public void sameMetadataAtomicReplacementIsDetected() throws Exception {
        Path file = root.resolve("atomic.txt");
        write(file, "one");
        FileTime originalModified = Files.getLastModifiedTime(file);
        FileWatcher watcher = new FileWatcher(file.toFile());
        Path replacement = root.resolve("atomic.tmp");
        write(replacement, "two");
        Files.setLastModifiedTime(replacement, originalModified);

        try {
            Files.move(replacement, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(replacement, file, StandardCopyOption.REPLACE_EXISTING);
        }

        assertTrue(watcher.checkModified());
        watcher.close();
    }

    @Test
    public void atomicReplacementIsNotReportedAsADeletion() throws Exception {
        Path file = root.resolve("atomic.json");
        write(file, "one");
        FileTime originalModified = Files.getLastModifiedTime(file);
        FolderWatcher watcher = new FolderWatcher(root.toFile());
        Path replacement = root.resolve("atomic.tmp");
        write(replacement, "two");
        Files.setLastModifiedTime(replacement, originalModified);

        try {
            Files.move(replacement, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(replacement, file, StandardCopyOption.REPLACE_EXISTING);
        }

        assertTrue(watcher.checkModified());
        assertFalse(watcher.getDeleted().contains(file.toFile()));
        watcher.close();
    }

    @Test
    public void deletingRootReportsItsPreviouslyKnownContents() throws Exception {
        Path file = root.resolve("inside.json");
        write(file, "1");
        FolderWatcher watcher = new FolderWatcher(root.toFile());

        Files.delete(file);
        Files.delete(root);

        assertTrue(watcher.checkModified());
        assertEquals(List.of("inside.json"), names(watcher.getDeleted()));
        watcher.close();
    }

    @Test
    public void directorySymlinkCycleDoesNotRecurse() throws Exception {
        Path nested = Files.createDirectory(root.resolve("nested"));
        try {
            Files.createSymbolicLink(nested.resolve("back"), root);
        } catch (UnsupportedOperationException | IOException unsupported) {
            org.junit.Assume.assumeNoException(unsupported);
        }

        FolderWatcher watcher = new FolderWatcher(root.toFile());
        assertFalse(watcher.checkModified());
        watcher.close();
    }

    private boolean awaitEventChange(FolderWatcher watcher,
                                     Predicate<FolderWatcher> expected,
                                     long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            watcher.checkModifiedEvents();
            if (expected.test(watcher)) {
                return true;
            }
            Thread.sleep(25L);
        }
        return false;
    }

    private boolean awaitFileEventChange(FileWatcher watcher, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (watcher.checkModifiedEvents()) {
                return true;
            }
            Thread.sleep(25L);
        }
        return false;
    }

    private static final class CountingFileWatcher extends FileWatcher {
        private int propertiesReadCount;

        private CountingFileWatcher(File file) {
            super(file, true, false);
            checkModified();
            propertiesReadCount = 0;
        }

        @Override
        protected void readProperties() {
            propertiesReadCount++;
            super.readProperties();
        }

        private int propertiesReadCount() {
            return propertiesReadCount;
        }
    }
}

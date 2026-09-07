package art.arcane.volmlib.util.io;

import org.junit.Assume;
import org.junit.Test;


import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Equivalence pins for the watcher rework: {@code FileWatcher} now takes one stat per poll instead
 * of four, and {@code FolderWatcher} reports its membership delta from the pass that produces it
 * instead of copying the whole watcher map every poll to diff against.
 */
public class FolderWatcherDeltaTest extends WatcherTestRoot {
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

    @Test(timeout = 8_000L)
    public void createdFilesAreReportedOnceAndThenGoQuiet() throws Exception {
        Path file = root.resolve("new.json");
        try (FolderWatcher watcher = new FolderWatcher(root.toFile())) {
            write(file, "1");

            assertTrue(watcher.checkModified());
            assertEquals(List.of("new.json"), names(watcher.getCreated()));
            assertTrue(watcher.getChanged().isEmpty());

            assertTrue(awaitFullScanQuiet(watcher, file.toFile(), 5_000L));
            assertFalse(watcher.checkModified());
            assertTrue(watcher.getCreated().isEmpty());
            assertTrue(watcher.getChanged().isEmpty());
            assertTrue(watcher.getDeleted().isEmpty());
        }
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
            Assume.assumeNoException(unsupported);
        }

        FolderWatcher watcher = new FolderWatcher(root.toFile());
        assertFalse(watcher.checkModified());
        watcher.close();
    }

    private boolean awaitFullScanQuiet(FolderWatcher watcher, File writtenFile, long timeoutMs)
            throws InterruptedException {
        long quietSince = System.nanoTime();
        long deadline = quietSince + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        long quietPeriod = TimeUnit.MILLISECONDS.toNanos(250L);
        while (System.nanoTime() < deadline) {
            boolean modified = watcher.checkModified();
            assertTrue(watcher.getCreated().isEmpty());
            assertTrue(watcher.getDeleted().isEmpty());
            for (File changedFile : watcher.getChanged()) {
                assertEquals(writtenFile, changedFile);
            }
            if (modified) {
                quietSince = System.nanoTime();
            } else if (System.nanoTime() - quietSince >= quietPeriod) {
                return true;
            }
            Thread.sleep(10L);
        }
        return false;
    }
}

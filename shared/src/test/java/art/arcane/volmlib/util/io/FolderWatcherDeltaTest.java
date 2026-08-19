package art.arcane.volmlib.util.io;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

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
    public void aNewlyCreatedDirectoryIsNotAlsoDescendedIntoOnTheSamePass() throws Exception {
        FolderWatcher watcher = new FolderWatcher(root.toFile());
        Path nested = root.resolve("burst");
        Files.createDirectory(nested);
        write(nested.resolve("one.json"), "1");
        write(nested.resolve("two.json"), "1");

        assertTrue(watcher.checkModified());

        // Only the directory is new to this watcher; its contents were never seen before, so they
        // are not double-reported as creations.
        assertEquals(List.of("burst"), names(watcher.getCreated()));
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
}

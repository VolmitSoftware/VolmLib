package art.arcane.volmlib.util.io;

import com.sun.nio.file.ExtendedOpenOption;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InterruptedIOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class AtomicFileIOTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void writesUtf8AndReplacesExistingContent() throws Exception {
        Path target = temporaryFolder.getRoot().toPath().resolve("nested/config.toml");

        AtomicFileIO.writeString(target, "first=\"á\"\n");
        AtomicFileIO.writeString(target, "second=\"世界\"\n");

        assertEquals("second=\"世界\"\n", Files.readString(target, StandardCharsets.UTF_8));
        try (java.util.stream.Stream<Path> entries = Files.list(target.getParent())) {
            assertFalse(entries.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    public void replacesAStagedFile() throws Exception {
        Path directory = temporaryFolder.newFolder("replace").toPath();
        Path source = directory.resolve("source.tmp");
        Path target = directory.resolve("target.txt");
        Files.writeString(source, "new", StandardCharsets.UTF_8);
        Files.writeString(target, "old", StandardCharsets.UTF_8);

        AtomicFileIO.replace(source, target);

        assertEquals("new", Files.readString(target, StandardCharsets.UTF_8));
        assertFalse(Files.exists(source));
    }

    @Test
    public void waitsForATemporaryWindowsReaderLock() throws Exception {
        assumeTrue(File.separatorChar == '\\');
        Path target = temporaryFolder.newFile("reader-lock.txt").toPath();
        Files.writeString(target, "old", StandardCharsets.UTF_8);
        ExecutorService writer = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        try {
            Future<?> replacement;
            try (FileChannel reader = FileChannel.open(target, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE)) {
                replacement = writer.submit(() -> {
                    started.countDown();
                    AtomicFileIO.writeString(target, "new");
                    return null;
                });
                assertTrue(started.await(2L, TimeUnit.SECONDS));
                Thread.sleep(100L);
                assertFalse(replacement.isDone());
                assertEquals("old", Files.readString(target, StandardCharsets.UTF_8));
            }
            replacement.get(2L, TimeUnit.SECONDS);
            assertEquals("new", Files.readString(target, StandardCharsets.UTF_8));
        } finally {
            writer.shutdownNow();
            assertTrue(writer.awaitTermination(2L, TimeUnit.SECONDS));
        }
    }

    @Test
    public void persistentWindowsReaderLockPreservesStagedAndCurrentFiles() throws Exception {
        assumeTrue(File.separatorChar == '\\');
        Path target = temporaryFolder.newFile("locked-target.txt").toPath();
        Path source = temporaryFolder.newFile("staged.tmp").toPath();
        Files.writeString(target, "old", StandardCharsets.UTF_8);
        Files.writeString(source, "new", StandardCharsets.UTF_8);
        try (FileChannel reader = FileChannel.open(target, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE)) {
            AccessDeniedException failure = assertThrows(AccessDeniedException.class, () -> AtomicFileIO.replace(source, target));
            assertEquals(target.toString(), failure.getOtherFile());
            assertEquals("old", Files.readString(target, StandardCharsets.UTF_8));
            assertEquals("new", Files.readString(source, StandardCharsets.UTF_8));
        }
    }

    @Test
    public void interruptedWindowsReplacementPreservesFailureAndInterrupt() throws Exception {
        assumeTrue(File.separatorChar == '\\');
        Path target = temporaryFolder.newFile("interrupted-target.txt").toPath();
        Path source = temporaryFolder.newFile("interrupted-staged.tmp").toPath();
        Files.writeString(target, "old", StandardCharsets.UTF_8);
        Files.writeString(source, "new", StandardCharsets.UTF_8);
        try (FileChannel reader = FileChannel.open(target, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE)) {
            Thread.currentThread().interrupt();
            try {
                InterruptedIOException failure = assertThrows(InterruptedIOException.class, () -> AtomicFileIO.replace(source, target));
                assertTrue(Thread.currentThread().isInterrupted());
                assertTrue(failure.getCause() instanceof InterruptedException);
                assertEquals(1, failure.getSuppressed().length);
                assertTrue(failure.getSuppressed()[0] instanceof AccessDeniedException);
            } finally {
                Thread.interrupted();
            }
            assertEquals("old", Files.readString(target, StandardCharsets.UTF_8));
            assertEquals("new", Files.readString(source, StandardCharsets.UTF_8));
        }
    }

}

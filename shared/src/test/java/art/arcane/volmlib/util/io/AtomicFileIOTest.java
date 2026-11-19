package art.arcane.volmlib.util.io;

import com.sun.nio.file.ExtendedOpenOption;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;

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
    public void flushesTheDirectoryAfterTheRename() throws Exception {
        assumeFalse(File.separatorChar == '\\');
        Path directory = temporaryFolder.newFolder("flushed").toPath();
        Path target = directory.resolve("config.toml");
        Files.writeString(target, "old", StandardCharsets.UTF_8);

        List<Path> flushed = recordDirectoryFlushes(() -> AtomicFileIO.writeString(target, "new"));

        assertEquals(List.of(directory.toRealPath()), flushed);
        assertEquals("new", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    public void flushesEachNewDirectoryIntoItsParent() throws Exception {
        assumeFalse(File.separatorChar == '\\');
        Path root = temporaryFolder.newFolder("durable").toPath().toRealPath();
        Path target = root.resolve("a/b/config.toml");

        List<Path> flushed = recordDirectoryFlushes(() -> AtomicFileIO.writeString(target, "value"));

        assertEquals(List.of(root, root.resolve("a"), root.resolve("a/b")), flushed);
        assertEquals("value", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    public void refusesAParentThatIsAFile() throws Exception {
        Path blocker = temporaryFolder.newFile("blocker").toPath();

        assertThrows(IOException.class, () -> AtomicFileIO.writeString(blocker.resolve("config.toml"), "value"));
        assertEquals("", Files.readString(blocker, StandardCharsets.UTF_8));
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

    /** The directories a write forced, in order: each is opened read-only and forced, which is how a directory entry is flushed. */
    private static List<Path> recordDirectoryFlushes(IORunnable write) throws Exception {
        List<Path> flushed = new ArrayList<>();
        try (MockedStatic<FileChannel> channels = mockStatic(FileChannel.class, CALLS_REAL_METHODS)) {
            channels.when(() -> FileChannel.open(any(Path.class), any(OpenOption[].class))).thenAnswer(invocation -> {
                Path path = invocation.getArgument(0);
                FileChannel channel = (FileChannel) invocation.callRealMethod();
                if (!Files.isDirectory(path)) {
                    return channel;
                }
                FileChannel recorded = spy(channel);
                doAnswer(force -> {
                    flushed.add(path.toRealPath());
                    return force.callRealMethod();
                }).when(recorded).force(true);
                return recorded;
            });
            write.run();
        }
        return flushed;
    }
}

package art.arcane.volmlib.util.io;

import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FileWatcherEventWatchTest extends WatcherTestRoot {
    @Test(timeout = 30_000L)
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
            assertTrue(awaitFileEventChange(watcher, 20_000L));
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

    private boolean awaitFileEventChange(FileWatcher watcher, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (watcher.checkModifiedEvents()) {
                return true;
            }
            Thread.sleep(10L);
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

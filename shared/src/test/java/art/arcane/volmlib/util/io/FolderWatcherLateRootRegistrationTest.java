package art.arcane.volmlib.util.io;

import org.junit.Assume;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FolderWatcherLateRootRegistrationTest extends WatcherTestRoot {
    @Test(timeout = 30_000L)
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
                    20_000L
            ));
            Assume.assumeTrue(watcher.isEventWatchActive());

            write(created, "22");
            assertTrue(awaitEventChange(
                    watcher,
                    candidate -> names(candidate.getChanged()).contains("created.json"),
                    20_000L
            ));
        } finally {
            watcher.close();
        }
    }

    @Test(timeout = 30_000L)
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
                    20_000L
            ));
        } finally {
            watcher.close();
        }
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
            Thread.sleep(10L);
        }
        return false;
    }
}

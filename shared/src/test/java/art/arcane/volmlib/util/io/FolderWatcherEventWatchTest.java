package art.arcane.volmlib.util.io;

import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

public class FolderWatcherEventWatchTest extends WatcherTestRoot {
    @Test(timeout = 30_000L)
    public void eventOnlyPassReportsNestedCreateModifyAndDelete() throws Exception {
        Path nested = Files.createDirectory(root.resolve("nested-events"));
        Path edited = nested.resolve("edited.json");
        Path removed = nested.resolve("removed.json");
        write(edited, "1");
        write(removed, "1");
        FolderWatcher watcher = new FolderWatcher(root.toFile());

        try {
            Assume.assumeTrue(watcher.isEventWatchActive());
            write(nested.resolve("created.json"), "1");
            write(edited, "22");
            Files.delete(removed);

            Set<String> created = new HashSet<>();
            Set<String> changed = new HashSet<>();
            Set<String> deleted = new HashSet<>();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20L);
            while (System.nanoTime() < deadline) {
                watcher.checkModifiedEvents();
                collectNames(watcher.getCreated(), created);
                collectNames(watcher.getChanged(), changed);
                collectNames(watcher.getDeleted(), deleted);
                if (created.contains("created.json")
                        && changed.contains("edited.json")
                        && deleted.contains("removed.json")) {
                    return;
                }
                Thread.sleep(10L);
            }

            assertTrue("created=" + created, created.contains("created.json"));
            assertTrue("changed=" + changed, changed.contains("edited.json"));
            assertTrue("deleted=" + deleted, deleted.contains("removed.json"));
        } finally {
            watcher.close();
        }
    }

    private static void collectNames(Iterable<File> files, Set<String> target) {
        for (File file : files) {
            target.add(file.getName());
        }
    }
}

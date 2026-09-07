package art.arcane.volmlib.util.io;

import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public abstract class WatcherTestRoot {
    protected Path root;

    @Before
    public void createWatcherRoot() throws Exception {
        root = Files.createTempDirectory("volmlib-watcher-test");
    }

    @After
    public void deleteWatcherRoot() throws Exception {
        if (root == null || !Files.exists(root)) {
            return;
        }
        Files.walk(root)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    protected static void write(Path file, String content) throws Exception {
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    protected static List<String> names(List<File> files) {
        return files.stream().map(File::getName).sorted().toList();
    }
}

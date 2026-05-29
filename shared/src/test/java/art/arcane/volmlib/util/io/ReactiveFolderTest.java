package art.arcane.volmlib.util.io;

import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ReactiveFolderTest {
    @Test
    public void checkReturnsDetectedWatchedChanges() throws Exception {
        Path directory = Files.createTempDirectory("reactive-folder-test");
        Path watchedFile = directory.resolve("dimension.json");
        Files.writeString(watchedFile, "{\"v\":1}", StandardCharsets.UTF_8);
        AtomicInteger hotloads = new AtomicInteger();

        try {
            ReactiveFolder folder = new ReactiveFolder(
                    directory.toFile(),
                    (created, changed, deleted) -> hotloads.incrementAndGet(),
                    new KList<>(".json"),
                    new KList<>(),
                    new KList<>()
            );

            Thread.sleep(20L);
            Files.writeString(watchedFile, "{\"v\":2}", StandardCharsets.UTF_8);

            boolean detected = folder.check();

            assertTrue(detected);
            assertEquals(1, hotloads.get());
        } finally {
            Files.walk(directory)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }
}

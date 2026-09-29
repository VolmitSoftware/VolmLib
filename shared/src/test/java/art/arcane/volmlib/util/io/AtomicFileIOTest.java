package art.arcane.volmlib.util.io;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

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
}

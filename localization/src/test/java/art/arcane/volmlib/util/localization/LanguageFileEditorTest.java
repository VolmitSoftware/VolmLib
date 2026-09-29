package art.arcane.volmlib.util.localization;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

public class LanguageFileEditorTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void createsNestedFilesAndReturnsOnlyAfterPublishing() throws IOException {
        Path file = temporaryFolder.getRoot().toPath().resolve("languages/custom/en_US.toml");
        String result = LanguageFileEditor.update(file, existing -> {
            assertEquals("", existing);
            return new LanguageFileEditor.Prepared<>("message = \"Hello\"\n", "saved");
        });
        assertEquals("saved", result);
        assertEquals("message = \"Hello\"\n", Files.readString(file));
    }

    @Test
    public void failedValidationLeavesOriginalBytesUntouched() throws IOException {
        Path file = existing("original\n");
        assertThrows(IOException.class, () -> LanguageFileEditor.update(file, content -> {
            throw new IOException("Invalid message");
        }));
        assertEquals("original\n", Files.readString(file));
    }

    @Test
    public void concurrentExternalEditsArePreserved() throws IOException {
        Path file = existing("original");
        assertThrows(IOException.class, () -> LanguageFileEditor.update(file, content -> {
            Files.writeString(file, "external");
            return new LanguageFileEditor.Prepared<>("editor", true);
        }));
        assertEquals("external", Files.readString(file));
    }

    @Test
    public void concurrentCreationAndDeletionAreDetected() throws IOException {
        Path missing = temporaryFolder.getRoot().toPath().resolve("missing.toml");
        assertThrows(IOException.class, () -> LanguageFileEditor.update(missing, content -> {
            Files.writeString(missing, "external");
            return new LanguageFileEditor.Prepared<>("editor", true);
        }));
        assertEquals("external", Files.readString(missing));
        Path file = existing("original");
        assertThrows(IOException.class, () -> LanguageFileEditor.update(file, content -> {
            Files.delete(file);
            return new LanguageFileEditor.Prepared<>("editor", true);
        }));
        assertFalse(Files.exists(file));
    }

    @Test
    public void malformedUtf8NeverReachesTheEditor() throws IOException {
        Path file = existing("valid");
        Files.write(file, new byte[]{(byte) 0xC3, 0x28});
        AtomicInteger calls = new AtomicInteger();
        assertThrows(IOException.class, () -> LanguageFileEditor.update(file, content -> {
            calls.incrementAndGet();
            return new LanguageFileEditor.Prepared<>("new", true);
        }));
        assertEquals(0, calls.get());
        assertEquals(2, Files.size(file));
    }

    @Test
    public void oversizedFilesAndUtf8OutputAreRejected() throws IOException {
        Path file = existing("x".repeat(2 * 1024 * 1024 + 1));
        assertThrows(IOException.class, () -> LanguageFileEditor.update(file,
                content -> new LanguageFileEditor.Prepared<>("new", true)));
        Files.writeString(file, "original");
        assertThrows(IOException.class, () -> LanguageFileEditor.update(file,
                content -> new LanguageFileEditor.Prepared<>("é".repeat(1024 * 1024 + 1), true)));
        assertThrows(IOException.class, () -> LanguageFileEditor.update(file,
                content -> new LanguageFileEditor.Prepared<>("\uD800", true)));
        assertEquals("original", Files.readString(file));
    }

    @Test
    public void directoriesAndSymbolicLinksAreRejected() throws IOException {
        Path directory = temporaryFolder.newFolder("directory").toPath();
        assertThrows(IOException.class, () -> LanguageFileEditor.update(directory,
                content -> new LanguageFileEditor.Prepared<>("new", true)));
        Path target = existing("original");
        Path link = temporaryFolder.getRoot().toPath().resolve("linked.toml");
        Files.createSymbolicLink(link, target);
        assertThrows(IOException.class, () -> LanguageFileEditor.update(link,
                content -> new LanguageFileEditor.Prepared<>("new", true)));
        Path parentLink = temporaryFolder.getRoot().toPath().resolve("linked-directory");
        Files.createSymbolicLink(parentLink, directory);
        assertThrows(IOException.class, () -> LanguageFileEditor.update(parentLink.resolve("new.toml"),
                content -> new LanguageFileEditor.Prepared<>("new", true)));
        assertEquals("original", Files.readString(target));
    }

    @Test
    public void cancellationBeforeCommitLeavesOriginalContent() throws IOException {
        Path file = existing("original");
        try {
            assertThrows(InterruptedIOException.class, () -> LanguageFileEditor.update(file, content -> {
                Thread.currentThread().interrupt();
                return new LanguageFileEditor.Prepared<>("new", true);
            }));
        } finally {
            Thread.interrupted();
        }
        assertEquals("original", Files.readString(file));
    }

    private Path existing(String content) throws IOException {
        Path file = temporaryFolder.getRoot().toPath().resolve("messages.toml");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}

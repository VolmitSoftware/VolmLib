package art.arcane.volmlib.util.localization;

import art.arcane.volmlib.util.io.AtomicFileIO;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

public final class LanguageFileEditor {
    private static final int MAXIMUM_BYTES = 2 * 1024 * 1024;

    private LanguageFileEditor() {
    }

    public static <T> T update(Path path, ContentEditor<T> editor) throws IOException {
        Path target = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        ContentEditor<T> required = Objects.requireNonNull(editor, "editor");
        requireActive();
        prepareParent(target.getParent());
        FileSource source = read(target);
        Prepared<T> prepared = Objects.requireNonNull(required.prepare(decode(source.bytes())), "Prepared edit");
        validateContent(prepared.content());
        requireActive();
        prepareParent(target.getParent());
        FileSource current = read(target);
        if (source.existed() != current.existed() || !Arrays.equals(source.bytes(), current.bytes())) {
            throw new IOException("Language file changed while the editor was saving; try again");
        }
        requireActive();
        AtomicFileIO.writeString(target, prepared.content());
        return prepared.result();
    }

    private static void prepareParent(Path parent) throws IOException {
        if (parent == null) {
            throw new IOException("Language file has no parent directory");
        }
        if (Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(parent)) {
                throw new IOException("Language path is not a regular directory: " + parent);
            }
            return;
        }
        prepareParent(parent.getParent());
        try {
            Files.createDirectory(parent);
        } catch (FileAlreadyExistsException exception) {
            if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(parent)) {
                throw exception;
            }
        }
    }

    private static FileSource read(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return new FileSource(false, new byte[0]);
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IOException("Language path is not a regular file: " + path.getFileName());
        }
        if (Files.size(path) > MAXIMUM_BYTES) {
            throw new IOException("Language file exceeds the 2 MiB safety limit");
        }
        byte[] bytes;
        try (InputStream stream = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            bytes = stream.readNBytes(MAXIMUM_BYTES + 1);
        }
        if (bytes.length > MAXIMUM_BYTES) {
            throw new IOException("Language file exceeds the 2 MiB safety limit");
        }
        return new FileSource(true, bytes);
    }

    private static String decode(byte[] bytes) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new IOException("Language file contains invalid UTF-8", exception);
        }
    }

    private static void validateContent(String content) throws IOException {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(content));
            if (encoded.remaining() > MAXIMUM_BYTES) {
                throw new IOException("Language file exceeds the 2 MiB safety limit");
            }
        } catch (CharacterCodingException exception) {
            throw new IOException("Language edit cannot be encoded as UTF-8", exception);
        }
    }

    private static void requireActive() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("Language edit was cancelled");
        }
    }

    public record Prepared<T>(String content, T result) {
        public Prepared {
            Objects.requireNonNull(content, "content");
        }
    }

    @FunctionalInterface
    public interface ContentEditor<T> {
        Prepared<T> prepare(String existing) throws IOException;
    }

    private record FileSource(boolean existed, byte[] bytes) {
    }
}

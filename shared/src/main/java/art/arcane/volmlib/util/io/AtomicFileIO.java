package art.arcane.volmlib.util.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

public final class AtomicFileIO {
    private AtomicFileIO() {
    }

    public static void writeString(Path target, String content) throws IOException {
        Path requiredTarget = Objects.requireNonNull(target, "target").toAbsolutePath().normalize();
        Path parent = requiredTarget.getParent();
        if (parent == null) {
            throw new IOException("Target has no parent directory: " + requiredTarget);
        }

        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "." + requiredTarget.getFileName() + ".", ".tmp");
        boolean replaced = false;
        try {
            byte[] bytes = Objects.requireNonNullElse(content, "").getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            )) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }

            replace(temporary, requiredTarget);
            replaced = true;
        } finally {
            if (!replaced) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    public static void replace(Path source, Path target) throws IOException {
        Path requiredSource = Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
        Path requiredTarget = Objects.requireNonNull(target, "target").toAbsolutePath().normalize();
        try {
            Files.move(
                    requiredSource,
                    requiredTarget,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(requiredSource, requiredTarget, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

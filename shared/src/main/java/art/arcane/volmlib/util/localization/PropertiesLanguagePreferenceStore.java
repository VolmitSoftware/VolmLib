package art.arcane.volmlib.util.localization;

import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;

public final class PropertiesLanguagePreferenceStore implements LanguagePreferenceStore {
    private static final long MAXIMUM_PREFERENCE_BYTES = 2L * 1024L * 1024L;

    private final Path file;

    public PropertiesLanguagePreferenceStore(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    }

    @Override
    public Map<UUID, String> load() throws IOException {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return Map.of();
        }
        requireSafeDirectory(Objects.requireNonNull(file.getParent(), "Language preferences directory"));
        requireRegularFile();
        Properties data = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            data.load(reader);
        }
        HashMap<UUID, String> loaded = new HashMap<>(data.size());
        for (String key : data.stringPropertyNames()) {
            loaded.put(UUID.fromString(key), data.getProperty(key));
        }
        return Map.copyOf(loaded);
    }

    @Override
    public void save(Map<UUID, String> preferences) throws IOException {
        Map<UUID, String> required = Map.copyOf(Objects.requireNonNull(preferences, "preferences"));
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            requireRegularFile();
        }
        Path directory = Objects.requireNonNull(file.getParent(), "Language preferences directory");
        requireSafeDirectory(directory);
        Files.createDirectories(directory);
        requireSafeDirectory(directory);
        ArrayList<Map.Entry<UUID, String>> entries = new ArrayList<>(required.entrySet());
        entries.sort(Comparator.comparing(entry -> entry.getKey().toString()));
        StringBuilder content = new StringBuilder(entries.size() * 48);
        for (Map.Entry<UUID, String> entry : entries) {
            content.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        byte[] bytes = content.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAXIMUM_PREFERENCE_BYTES) {
            throw new IOException("Language preferences exceed " + MAXIMUM_PREFERENCE_BYTES + " bytes");
        }
        Path temporary = Files.createTempFile(directory, ".language-preferences-", ".tmp");
        try {
            try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    output.write(buffer);
                }
                output.force(true);
            }
            requireRegularTargetOrMissing();
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public String description() {
        return file.toString();
    }

    private void requireRegularFile() throws IOException {
        if (Files.isSymbolicLink(file)
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || Files.size(file) > MAXIMUM_PREFERENCE_BYTES) {
            throw new IOException("Invalid language preferences file: " + file);
        }
    }

    private void requireRegularTargetOrMissing() throws IOException {
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            requireRegularFile();
        }
    }

    private static void requireSafeDirectory(Path directory) throws IOException {
        Path current = directory;
        while (current != null) {
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current)
                        || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Language preferences directory is not a regular directory: " + current);
                }
            }
            current = current.getParent();
        }
    }
}

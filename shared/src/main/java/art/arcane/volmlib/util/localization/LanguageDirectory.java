package art.arcane.volmlib.util.localization;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class LanguageDirectory {
    public static final Pattern LOCALE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{2,32}");
    public static final String EXTENSION = ".toml";
    public static final String UPSTREAM_DIRECTORY = ".upstream";
    public static final long MAXIMUM_BYTES = 3L * 1024L * 1024L;

    private final Path root;

    public LanguageDirectory(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    public LanguageDirectory(File root) {
        this(Objects.requireNonNull(root, "root").toPath());
    }

    public Path root() {
        return root;
    }

    public Path upstreamDirectory() {
        return root.resolve(UPSTREAM_DIRECTORY);
    }

    public Path languageFile(String locale) {
        return root.resolve(requireLocale(locale) + EXTENSION);
    }

    public Path upstreamFile(String locale) {
        return upstreamDirectory().resolve(requireLocale(locale) + EXTENSION);
    }

    public static String requireLocale(String locale) {
        String normalized = locale == null ? "" : locale.trim();
        if (!LOCALE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Language locale must be a safe name without a path or extension");
        }
        return normalized;
    }

    public static boolean sameLocale(String first, String second) {
        return first.replace('-', '_').equalsIgnoreCase(second.replace('-', '_'));
    }

    public boolean isLanguageFile(File candidate) {
        return candidate != null && isLanguageFile(candidate.toPath());
    }

    public boolean isLanguageFile(Path candidate) {
        if (candidate == null) {
            return false;
        }
        Path absolute = candidate.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null || !root.equals(parent)) {
            return false;
        }
        return localeFromName(absolute.getFileName().toString()).isPresent();
    }

    public Optional<String> locale(Path path) {
        if (path == null || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        return localeFromName(path.getFileName().toString());
    }

    public static Optional<String> localeFromName(String fileName) {
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(EXTENSION)) {
            return Optional.empty();
        }
        String locale = fileName.substring(0, fileName.length() - EXTENSION.length());
        return LOCALE_PATTERN.matcher(locale).matches() ? Optional.of(locale) : Optional.empty();
    }

    public void prepare() throws IOException {
        prepareRegularDirectory(root);
    }

    public void prepareUpstream() throws IOException {
        prepareRegularDirectory(root);
        prepareRegularDirectory(upstreamDirectory());
    }

    public List<String> scan() throws IOException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            return List.of();
        }
        ArrayList<String> locales = new ArrayList<>();
        try (Stream<Path> files = Files.list(root)) {
            for (Path path : files.toList()) {
                locale(path).ifPresent(locales::add);
            }
        }
        locales.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(locales);
    }

    public boolean exists(String locale) {
        return Files.isRegularFile(languageFile(locale), LinkOption.NOFOLLOW_LINKS);
    }

    public String read(String locale) throws IOException {
        return readContent(languageFile(locale));
    }

    public LanguageMeta meta(String locale) throws IOException {
        Path file = languageFile(locale);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return LanguageMeta.empty();
        }
        return LanguageMeta.parse(readContent(file)).withoutSelfBase(locale);
    }

    public static String readContent(Path file) throws IOException {
        Path path = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IOException("Language path is not a regular file: " + path.getFileName());
        }
        if (Files.size(path) > MAXIMUM_BYTES) {
            throw new IOException("Language file exceeds the 3 MiB safety limit");
        }
        byte[] bytes;
        try (InputStream stream = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            bytes = stream.readNBytes((int) MAXIMUM_BYTES + 1);
        }
        if (bytes.length > MAXIMUM_BYTES) {
            throw new IOException("Language file exceeds the 3 MiB safety limit");
        }
        return decode(bytes);
    }

    public static String decode(byte[] bytes) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IOException("Language file contains invalid UTF-8", exception);
        }
    }

    public static void prepareRegularDirectory(Path directory) throws IOException {
        Path required = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        if (Files.exists(required, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isDirectory(required, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(required))) {
            throw new IOException("Language path is not a regular directory: " + required);
        }
        Files.createDirectories(required);
    }
}

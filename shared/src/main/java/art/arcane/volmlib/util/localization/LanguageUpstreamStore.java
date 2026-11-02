package art.arcane.volmlib.util.localization;

import art.arcane.volmlib.util.io.AtomicFileIO;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;

public final class LanguageUpstreamStore {
    public static final String STATE_FILE = "upstream.properties";

    private final LanguageDirectory directory;
    private final LanguageSourceManifest manifest;

    public LanguageUpstreamStore(LanguageDirectory directory, LanguageSourceManifest manifest) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.manifest = Objects.requireNonNull(manifest, "manifest");
    }

    public Path stateFile() {
        return directory.upstreamDirectory().resolve(STATE_FILE);
    }

    public synchronized State load() throws IOException {
        Path file = stateFile();
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return State.empty();
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
            throw new IOException("Language upstream state is not a regular file: " + file);
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        if (!manifest.repository().equals(properties.getProperty("repository", ""))
                || !manifest.branch().equals(properties.getProperty("branch", ""))) {
            return State.empty();
        }
        String revision = properties.getProperty("revision", "").trim();
        Optional<String> storedRevision;
        try {
            storedRevision = revision.isEmpty() ? Optional.empty()
                    : Optional.of(GitHubLanguageSource.requireRevision(revision));
        } catch (IllegalArgumentException invalid) {
            return State.empty();
        }
        String etag = properties.getProperty("etag", "").trim();
        long checked;
        try {
            checked = Long.parseLong(properties.getProperty("checked", "0").trim());
        } catch (NumberFormatException invalid) {
            checked = 0L;
        }
        LinkedHashMap<String, String> hashes = new LinkedHashMap<>();
        for (String key : new TreeSet<>(properties.stringPropertyNames())) {
            if (key.startsWith("sha256.")) {
                String locale = key.substring("sha256.".length());
                String hash = properties.getProperty(key, "").trim();
                if (LanguageDirectory.LOCALE_PATTERN.matcher(locale).matches() && hash.matches("[0-9a-f]{64}")) {
                    hashes.put(locale, hash);
                }
            }
        }
        return new State(storedRevision, etag.isEmpty() ? Optional.empty() : Optional.of(etag), checked, hashes);
    }

    public synchronized void save(State state) throws IOException {
        State required = Objects.requireNonNull(state, "state");
        directory.prepareUpstream();
        StringBuilder content = new StringBuilder(256);
        content.append("repository=").append(manifest.repository()).append('\n');
        content.append("branch=").append(manifest.branch()).append('\n');
        content.append("revision=").append(required.revision().orElse("")).append('\n');
        content.append("etag=").append(required.etag().map(LanguageUpstreamStore::escape).orElse("")).append('\n');
        content.append("checked=").append(required.checkedMillis()).append('\n');
        for (Map.Entry<String, String> entry : new TreeMap<>(required.hashes()).entrySet()) {
            content.append("sha256.").append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        AtomicFileIO.writeString(stateFile(), content.toString());
    }

    public synchronized Optional<String> readUpstream(String locale) throws IOException {
        Path file = directory.upstreamFile(locale);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        return Optional.of(LanguageDirectory.readContent(file));
    }

    public synchronized void writeUpstream(String locale, String content) throws IOException {
        directory.prepareUpstream();
        AtomicFileIO.writeString(directory.upstreamFile(locale), Objects.requireNonNull(content, "content"));
    }

    public synchronized void deleteUpstream(String locale) throws IOException {
        Files.deleteIfExists(directory.upstreamFile(locale));
    }

    public static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Objects.requireNonNull(content, "content")
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> escaped.append(current);
            }
        }
        return escaped.toString();
    }

    public record State(Optional<String> revision, Optional<String> etag, long checkedMillis,
                        Map<String, String> hashes) {
        public State {
            Objects.requireNonNull(revision, "revision");
            Objects.requireNonNull(etag, "etag");
            hashes = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(hashes, "hashes")));
        }

        public static State empty() {
            return new State(Optional.empty(), Optional.empty(), 0L, Map.of());
        }

        public State withRevision(String revision, Optional<String> etag, long checkedMillis) {
            return new State(Optional.of(GitHubLanguageSource.requireRevision(revision)), etag, checkedMillis, hashes);
        }

        public State withChecked(Optional<String> etag, long checkedMillis) {
            return new State(revision, etag.isPresent() ? etag : this.etag, checkedMillis, hashes);
        }

        public State withHash(String locale, String hash) {
            LinkedHashMap<String, String> next = new LinkedHashMap<>(hashes);
            next.put(LanguageDirectory.requireLocale(locale), Objects.requireNonNull(hash, "hash"));
            return new State(revision, etag, checkedMillis, next);
        }

        public State withoutHash(String locale) {
            LinkedHashMap<String, String> next = new LinkedHashMap<>(hashes);
            next.remove(locale);
            return new State(revision, etag, checkedMillis, next);
        }
    }
}

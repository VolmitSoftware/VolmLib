package art.arcane.volmlib.util.localization;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class GitHubLanguageSource {
    public static final URI API_ROOT = URI.create("https://api.github.com/");
    public static final URI RAW_ROOT = URI.create("https://raw.githubusercontent.com/");
    public static final int MAXIMUM_FILE_BYTES = 3 * 1024 * 1024;
    private static final int MAXIMUM_API_BYTES = 64 * 1024;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3L);
    private static final Duration API_TIMEOUT = Duration.ofSeconds(10L);
    private static final Duration FILE_TIMEOUT = Duration.ofSeconds(5L);
    private static final Pattern REVISION_PATTERN = Pattern.compile("[0-9a-f]{40}");
    private static final Pattern HEADER_PATTERN = Pattern.compile("[\\x20-\\x7E]{1,200}");

    private final LanguageSourceManifest manifest;
    private final String productName;
    private final URI apiRoot;
    private final URI rawRoot;
    private final HttpClient client;

    public GitHubLanguageSource(LanguageSourceManifest manifest, String productName) {
        this(manifest, productName, API_ROOT, RAW_ROOT);
    }

    public GitHubLanguageSource(LanguageSourceManifest manifest, String productName, URI apiRoot, URI rawRoot) {
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.productName = requireHeader(Objects.requireNonNull(productName, "productName"));
        this.apiRoot = requireRoot(apiRoot);
        this.rawRoot = requireRoot(rawRoot);
        client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public LanguageSourceManifest manifest() {
        return manifest;
    }

    public URI commitsUri() {
        return apiRoot.resolve("repos/" + encode(manifest.owner()) + "/" + encode(manifest.name())
                + "/commits?sha=" + encode(manifest.branch()) + "&path=" + encode(manifest.path()) + "&per_page=1");
    }

    public URI fileUri(String locale, String revision) {
        return rawRoot.resolve(encode(manifest.owner()) + "/" + encode(manifest.name()) + "/"
                + requireRevision(revision) + "/" + manifest.filePath(locale));
    }

    public RevisionCheck latestRevision(String etag) throws IOException {
        HttpRequest.Builder request = HttpRequest.newBuilder(commitsUri())
                .timeout(API_TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", productName + "-Language-Update")
                .GET();
        if (etag != null && !etag.isBlank() && HEADER_PATTERN.matcher(etag).matches()) {
            request.header("If-None-Match", etag);
        }
        HttpResponse<InputStream> response = send(request.build());
        int status = response.statusCode();
        try (InputStream input = response.body()) {
            if (status == 304) {
                return new RevisionCheck(Optional.empty(), Optional.ofNullable(etag), true);
            }
            if (status == 403 || status == 429) {
                throw new RetryLater("GitHub refused the language source request (HTTP " + status + ")",
                        retryDelayMillis(response));
            }
            if (status == 404) {
                throw new IOException("Language source " + manifest.repository() + "@" + manifest.branch()
                        + " was not found on GitHub");
            }
            if (status != 200) {
                throw new IOException("GitHub language source returned HTTP " + status);
            }
            byte[] bytes = input.readNBytes(MAXIMUM_API_BYTES + 1);
            if (bytes.length > MAXIMUM_API_BYTES) {
                throw new IOException("GitHub language source response exceeds " + MAXIMUM_API_BYTES + " bytes");
            }
            String revision = parseRevision(new String(bytes, StandardCharsets.UTF_8));
            Optional<String> responseEtag = response.headers().firstValue("ETag")
                    .filter(value -> HEADER_PATTERN.matcher(value).matches());
            return new RevisionCheck(Optional.of(revision), responseEtag, false);
        }
    }

    public byte[] fetch(String locale, String revision) throws IOException {
        URI uri = fileUri(locale, revision);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(FILE_TIMEOUT)
                .header("Accept", "text/plain")
                .header("User-Agent", productName + "-Language-Update")
                .GET()
                .build();
        HttpResponse<InputStream> response = send(request);
        int status = response.statusCode();
        try (InputStream input = response.body()) {
            if (status == 403 || status == 429) {
                throw new RetryLater("GitHub refused the language file request (HTTP " + status + ")",
                        retryDelayMillis(response));
            }
            if (status != 200) {
                throw new IOException("Unable to fetch language file " + locale + " from " + uri + ": HTTP " + status);
            }
            if (response.headers().firstValueAsLong("Content-Length").orElse(0L) > MAXIMUM_FILE_BYTES) {
                throw new IOException("Downloaded locale exceeds " + MAXIMUM_FILE_BYTES + " bytes");
            }
            byte[] bytes = input.readNBytes(MAXIMUM_FILE_BYTES + 1);
            if (bytes.length > MAXIMUM_FILE_BYTES) {
                throw new IOException("Downloaded locale exceeds " + MAXIMUM_FILE_BYTES + " bytes");
            }
            return bytes;
        }
    }

    private HttpResponse<InputStream> send(HttpRequest request) throws IOException {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Language source request was interrupted", interrupted);
        } catch (IllegalArgumentException | SecurityException failure) {
            throw new IOException("Language source request was rejected: " + failure.getMessage(), failure);
        }
    }

    private static String parseRevision(String json) throws IOException {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(json);
        } catch (JsonSyntaxException exception) {
            throw new IOException("GitHub language source returned invalid JSON", exception);
        }
        if (parsed == null || !parsed.isJsonArray()) {
            throw new IOException("GitHub language source did not return a commit list");
        }
        JsonArray commits = parsed.getAsJsonArray();
        if (commits.isEmpty()) {
            throw new IOException("GitHub language source has no commits for the language path");
        }
        JsonElement first = commits.get(0);
        if (!first.isJsonObject() || !first.getAsJsonObject().has("sha")
                || !first.getAsJsonObject().get("sha").isJsonPrimitive()) {
            throw new IOException("GitHub language source commit has no sha");
        }
        try {
            return requireRevision(first.getAsJsonObject().get("sha").getAsString());
        } catch (IllegalArgumentException invalid) {
            throw new IOException("GitHub language source commit sha is invalid", invalid);
        }
    }

    private static long retryDelayMillis(HttpResponse<?> response) {
        long seconds = 60L;
        Optional<String> retry = response.headers().firstValue("Retry-After");
        if (retry.isPresent()) {
            String value = retry.get().trim();
            try {
                seconds = Math.max(seconds, Long.parseLong(value));
            } catch (NumberFormatException ignored) {
                try {
                    seconds = Math.max(seconds, Duration.between(Instant.now(),
                            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds() + 1L);
                } catch (DateTimeException invalid) {
                    seconds = 60L;
                }
            }
        } else {
            Optional<String> reset = response.headers().firstValue("X-RateLimit-Reset");
            if (reset.isPresent()) {
                try {
                    long resetSeconds = Long.parseLong(reset.get().trim()) - Instant.now().getEpochSecond() + 1L;
                    seconds = Math.max(seconds, resetSeconds);
                } catch (NumberFormatException ignored) {
                    seconds = 60L;
                }
            }
        }
        return Math.min(seconds, TimeUnit.DAYS.toSeconds(7)) * 1000L;
    }

    public static String requireRevision(String revision) {
        String normalized = Objects.requireNonNull(revision, "revision").trim();
        if (!REVISION_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Language source revision must be a 40-character commit hash");
        }
        return normalized;
    }

    private static String requireHeader(String value) {
        String trimmed = value.trim();
        if (!HEADER_PATTERN.matcher(trimmed).matches() || trimmed.contains(" ")) {
            throw new IllegalArgumentException("Product name must be printable ASCII without spaces");
        }
        return trimmed;
    }

    private static URI requireRoot(URI root) {
        URI required = Objects.requireNonNull(root, "root");
        if (!required.toString().endsWith("/")) {
            throw new IllegalArgumentException("Language source root must end with /");
        }
        return required;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20").replace("%2F", "/");
    }

    public record RevisionCheck(Optional<String> revision, Optional<String> etag, boolean unchanged) {
        public RevisionCheck {
            Objects.requireNonNull(revision, "revision");
            Objects.requireNonNull(etag, "etag");
        }
    }

    public static final class RetryLater extends IOException {
        private final long delayMillis;

        public RetryLater(String message, long delayMillis) {
            super(message);
            this.delayMillis = Math.max(0L, delayMillis);
        }

        public long delayMillis() {
            return delayMillis;
        }
    }
}

package art.arcane.volmlib.util.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GitHubReleaseChecker implements AutoCloseable {
    private static final int MAXIMUM_RESPONSE_BYTES = 1024 * 1024;
    private static final int TIMEOUT_MILLIS = 10000;
    private static final Pattern REPOSITORY_PART = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern VERSION = Pattern.compile("[vV]?(\\d+(?:\\.\\d+)*)(?:[-+].*)?");

    private final Options options;
    private final Access access;
    private final ScheduledExecutorService executor;
    private Optional<Release> cached = Optional.empty();
    private CompletableFuture<Optional<Release>> pending;
    private Future<?> request;
    private ScheduledFuture<?> refresh;
    private long generation;
    private boolean enabled;
    private boolean closed;
    private boolean checked;
    private boolean reportedFailure;

    public GitHubReleaseChecker(Options options) {
        this(options, new Access(endpoint(options), Duration.ofHours(1)));
    }

    GitHubReleaseChecker(Options options, Access access) {
        this.options = Objects.requireNonNull(options, "options");
        this.access = Objects.requireNonNull(access, "access");
        this.executor = Executors.newSingleThreadScheduledExecutor(this::newThread);
    }

    public void setEnabled(boolean enabled) {
        CompletableFuture<Optional<Release>> cancelled;
        synchronized (this) {
            if (closed || this.enabled == enabled) {
                return;
            }
            this.enabled = enabled;
            generation++;
            cancelled = clear();
            if (enabled) {
                startCheck();
            }
        }
        completeCancelled(cancelled);
    }

    public synchronized CompletableFuture<Optional<Release>> check() {
        if (!enabled || closed) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        if (pending != null) {
            return pending.copy();
        }
        if (checked) {
            return CompletableFuture.completedFuture(cached);
        }
        return startCheck().copy();
    }

    @Override
    public void close() {
        CompletableFuture<Optional<Release>> cancelled;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            enabled = false;
            generation++;
            cancelled = clear();
            executor.shutdownNow();
        }
        completeCancelled(cancelled);
    }

    static boolean isNewer(String available, String installed) {
        List<BigInteger> availableParts = versionParts(available);
        List<BigInteger> installedParts = versionParts(installed);
        if (availableParts.isEmpty() || installedParts.isEmpty()) {
            return false;
        }
        int count = Math.max(availableParts.size(), installedParts.size());
        for (int index = 0; index < count; index++) {
            BigInteger availablePart = index < availableParts.size() ? availableParts.get(index) : BigInteger.ZERO;
            BigInteger installedPart = index < installedParts.size() ? installedParts.get(index) : BigInteger.ZERO;
            int comparison = availablePart.compareTo(installedPart);
            if (comparison != 0) {
                return comparison > 0;
            }
        }
        return false;
    }

    private static URI endpoint(Options options) {
        Objects.requireNonNull(options, "options");
        return URI.create("https://api.github.com/repos/" + options.owner() + "/" + options.repository() + "/releases/latest");
    }

    private static List<BigInteger> versionParts(String version) {
        if (version == null || version.length() > 256) {
            return List.of();
        }
        Matcher matcher = VERSION.matcher(version.trim());
        if (!matcher.matches()) {
            return List.of();
        }
        String[] parts = matcher.group(1).split("\\.");
        List<BigInteger> numbers = new ArrayList<>(parts.length);
        for (String part : parts) {
            numbers.add(new BigInteger(part));
        }
        return numbers;
    }

    private static void completeCancelled(CompletableFuture<Optional<Release>> cancelled) {
        if (cancelled != null) {
            cancelled.complete(Optional.empty());
        }
    }

    private Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable, options.repository() + "-release-check");
        thread.setDaemon(true);
        return thread;
    }

    private CompletableFuture<Optional<Release>> startCheck() {
        CompletableFuture<Optional<Release>> result = new CompletableFuture<>();
        pending = result;
        long startedGeneration = generation;
        request = executor.submit(() -> fetch(startedGeneration, result));
        return result;
    }

    private CompletableFuture<Optional<Release>> clear() {
        if (request != null) {
            request.cancel(true);
            request = null;
        }
        if (refresh != null) {
            refresh.cancel(false);
            refresh = null;
        }
        CompletableFuture<Optional<Release>> cancelled = pending;
        pending = null;
        cached = Optional.empty();
        checked = false;
        reportedFailure = false;
        return cancelled;
    }

    private void fetch(long startedGeneration, CompletableFuture<Optional<Release>> result) {
        Optional<Release> latest = Optional.empty();
        Exception failure = null;
        try {
            latest = fetchLatest().filter(release -> isNewer(release.tagName(), options.installedVersion()));
        } catch (IOException | RuntimeException exception) {
            failure = exception;
        }
        synchronized (this) {
            if (closed || !enabled || generation != startedGeneration || pending != result) {
                result.complete(Optional.empty());
                return;
            }
            cached = latest;
            checked = true;
            pending = null;
            request = null;
            if (failure == null) {
                reportedFailure = false;
            } else if (!reportedFailure) {
                reportedFailure = true;
                options.logger().log(Level.WARNING, "Could not check GitHub releases for " + options.owner() + "/"
                        + options.repository() + "; retrying in " + access.refreshInterval().toMinutes() + " minutes.", failure);
            }
            refresh = executor.schedule(this::refresh, access.refreshInterval().toMillis(), TimeUnit.MILLISECONDS);
            result.complete(latest);
        }
    }

    private synchronized void refresh() {
        refresh = null;
        if (!closed && enabled && pending == null) {
            checked = false;
            startCheck();
        }
    }

    private Optional<Release> fetchLatest() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) access.endpoint().toURL().openConnection();
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        connection.setRequestProperty("User-Agent", options.repository() + "-release-checker");
        try {
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                return Optional.empty();
            }
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("GitHub release request returned HTTP " + status);
            }
            if (connection.getContentLengthLong() > MAXIMUM_RESPONSE_BYTES) {
                throw new IOException("GitHub release response exceeds " + MAXIMUM_RESPONSE_BYTES + " bytes");
            }
            try (InputStream input = connection.getInputStream()) {
                byte[] bytes = input.readNBytes(MAXIMUM_RESPONSE_BYTES + 1);
                if (bytes.length > MAXIMUM_RESPONSE_BYTES) {
                    throw new IOException("GitHub release response exceeds " + MAXIMUM_RESPONSE_BYTES + " bytes");
                }
                return parseRelease(new String(bytes, StandardCharsets.UTF_8));
            }
        } finally {
            connection.disconnect();
        }
    }

    private Optional<Release> parseRelease(String body) throws IOException {
        JsonElement parsed = JsonParser.parseString(body);
        if (!parsed.isJsonObject()) {
            throw new IOException("GitHub release response must be a JSON object");
        }
        JsonObject release = parsed.getAsJsonObject();
        if (requiredBoolean(release, "draft") || requiredBoolean(release, "prerelease")) {
            return Optional.empty();
        }
        String tag = requiredString(release, "tag_name");
        String url = requiredString(release, "html_url");
        URI releaseUrl = URI.create(url);
        String releasePath = "/" + options.owner() + "/" + options.repository() + "/releases/tag/";
        if (!"https".equalsIgnoreCase(releaseUrl.getScheme()) || !"github.com".equalsIgnoreCase(releaseUrl.getHost())
                || releaseUrl.getUserInfo() != null || (releaseUrl.getPort() != -1 && releaseUrl.getPort() != 443)
                || releaseUrl.getPath() == null || releaseUrl.getPath().length() <= releasePath.length()
                || !releaseUrl.getPath().regionMatches(true, 0, releasePath, 0, releasePath.length())) {
            throw new IOException("GitHub release response has an invalid release page URL");
        }
        return Optional.of(new Release(tag, url));
    }

    private String requiredString(JsonObject object, String name) throws IOException {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank()) {
            throw new IOException("GitHub release response is missing " + name);
        }
        return value.getAsString();
    }

    private boolean requiredBoolean(JsonObject object, String name) throws IOException {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IOException("GitHub release response is missing " + name);
        }
        return value.getAsBoolean();
    }

    public record Options(String owner, String repository, String installedVersion, Logger logger) {
        public Options {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(repository, "repository");
            Objects.requireNonNull(installedVersion, "installedVersion");
            Objects.requireNonNull(logger, "logger");
            if (!REPOSITORY_PART.matcher(owner).matches() || !REPOSITORY_PART.matcher(repository).matches()
                    || owner.equals(".") || owner.equals("..") || repository.equals(".") || repository.equals("..")) {
                throw new IllegalArgumentException("GitHub owner and repository must be repository path components");
            }
        }
    }

    public record Release(String tagName, String url) {
    }

    record Access(URI endpoint, Duration refreshInterval) {
        Access {
            Objects.requireNonNull(endpoint, "endpoint");
            Objects.requireNonNull(refreshInterval, "refreshInterval");
            if (refreshInterval.toMillis() <= 0L) {
                throw new IllegalArgumentException("Release refresh interval must be positive");
            }
        }
    }
}

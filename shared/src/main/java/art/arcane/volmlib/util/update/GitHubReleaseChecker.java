package art.arcane.volmlib.util.update;

import java.io.IOException;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GitHubReleaseChecker implements AutoCloseable {
    private static final int TIMEOUT_MILLIS = 10000;
    private static final Duration MANUAL_CHECK_COOLDOWN = Duration.ofMinutes(5);
    private static final Pattern REPOSITORY_PART = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern VERSION = Pattern.compile("[vV]?(\\d+(?:\\.\\d+)*)(?:[-+].*)?");

    private final Options options;
    private final Access access;
    private final ExecutorService executor;
    private Optional<Release> cached = Optional.empty();
    private CompletableFuture<Optional<Release>> pending;
    private Future<?> request;
    private long generation;
    private boolean enabled;
    private boolean closed;
    private boolean checked;
    private boolean reportedFailure;
    private Report report = new Report(Status.DISABLED, null, "", 0L, 0L);
    private long nextRequestNanos;

    public GitHubReleaseChecker(Options options) {
        this(options, new Access(endpoint(options), MANUAL_CHECK_COOLDOWN));
    }

    GitHubReleaseChecker(Options options, Access access) {
        this.options = Objects.requireNonNull(options, "options");
        this.access = Objects.requireNonNull(access, "access");
        this.executor = Executors.newSingleThreadExecutor(this::newThread);
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

    public synchronized Report snapshot() {
        return report;
    }

    public synchronized CompletableFuture<Report> checkReport(boolean fresh) {
        if (fresh && enabled && !closed && pending == null && System.nanoTime() >= nextRequestNanos) {
            checked = false;
        }
        return check().thenApply(ignored -> snapshot());
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
        return prerelease(installed) && !prerelease(available);
    }

    private static boolean prerelease(String version) {
        return version.trim().matches("(?i)[vV]?\\d+(?:\\.\\d+)*-(?:alpha|beta|rc|snapshot|dev)(?:[.\\-]?[0-9A-Za-z.\\-]*)?(?:\\+.*)?");
    }

    private static boolean comparable(String version) {
        return !versionParts(version).isEmpty() && (version.trim().matches(
                "[vV]?\\d+(?:\\.\\d+)*(?:-\\d+(?:\\.\\d+)*(?:\\.[xX])?(?:-\\d+(?:\\.\\d+)*(?:\\.[xX])?)?)?(?:\\+.*)?") || prerelease(version));
    }

    private static URI endpoint(Options options) {
        Objects.requireNonNull(options, "options");
        return URI.create("https://github.com/" + options.owner() + "/" + options.repository() + "/releases/latest");
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

    private static String reason(Exception failure) {
        if (failure.getClass() == IOException.class || failure instanceof RetryLater) {
            return failure.getMessage();
        }
        return failure.getClass().getSimpleName() + (failure.getMessage() == null ? "" : ": " + failure.getMessage());
    }

    static String describeDelay(long millis) {
        long totalMinutes = Math.max(1L, (millis + 59_999L) / 60_000L);
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        String minuteText = minutes + (minutes == 1L ? " minute" : " minutes");
        if (hours == 0L) {
            return minuteText;
        }
        String hourText = hours + (hours == 1L ? " hour" : " hours");
        return minutes == 0L ? hourText : hourText + " " + minuteText;
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
        CompletableFuture<Optional<Release>> cancelled = pending;
        pending = null;
        cached = Optional.empty();
        checked = false;
        reportedFailure = false;
        report = new Report(Status.DISABLED, null, "", 0L, 0L);
        nextRequestNanos = 0L;
        return cancelled;
    }

    private void fetch(long startedGeneration, CompletableFuture<Optional<Release>> result) {
        Optional<Release> latest = Optional.empty();
        Exception failure = null;
        try {
            latest = fetchLatest();
        } catch (IOException | RuntimeException exception) {
            failure = exception;
        }
        synchronized (this) {
            if (closed || !enabled || generation != startedGeneration || pending != result) {
                result.complete(Optional.empty());
                return;
            }
            long now = System.currentTimeMillis();
            long cooldown = access.cooldown().toMillis();
            if (failure == null) {
                Release release = latest.orElse(null);
                Status status = release == null ? Status.NO_RELEASE
                        : !comparable(release.tagName()) || !comparable(options.installedVersion()) ? Status.UNKNOWN
                        : isNewer(release.tagName(), options.installedVersion()) ? Status.UPDATE : Status.CURRENT;
                report = new Report(status, release, "", now, now);
                cached = status == Status.UPDATE ? latest : Optional.empty();
                reportedFailure = false;
            } else {
                if (failure instanceof RetryLater retry) {
                    cooldown = Math.max(cooldown, retry.delayMillis);
                }
                report = new Report(Status.FAILED, report.release(), reason(failure), now, report.lastSuccessMillis());
                if (!reportedFailure) {
                    reportedFailure = true;
                    logFailure(failure, cooldown);
                }
            }
            nextRequestNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(cooldown);
            checked = true;
            pending = null;
            request = null;
            result.complete(cached);
        }
    }

    private void logFailure(Exception failure, long cooldown) {
        String message = "Could not check GitHub releases for " + options.owner() + "/" + options.repository() + ": "
                + reason(failure) + ". /volmit plugins updates can check again in " + describeDelay(cooldown) + ".";
        if (failure instanceof IOException) {
            options.logger().warning(message);
        } else {
            options.logger().log(Level.WARNING, message, failure);
        }
    }

    private Optional<Release> fetchLatest() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) access.endpoint().toURL().openConnection();
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("HEAD");
        connection.setRequestProperty("User-Agent", options.repository() + "-release-checker");
        try {
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                return Optional.empty();
            }
            if (status == 429) {
                throw new RetryLater("GitHub rate limited the request (HTTP 429)", retryDelay(connection));
            }
            if (status == HttpURLConnection.HTTP_FORBIDDEN) {
                throw new RetryLater("GitHub refused the request (HTTP 403)", retryDelay(connection));
            }
            if (status < 300 || status > 399) {
                throw new IOException("GitHub release page returned HTTP " + status);
            }
            return releaseFromRedirect(connection.getHeaderField("Location"));
        } finally {
            connection.disconnect();
        }
    }

    private Optional<Release> releaseFromRedirect(String location) throws IOException {
        if (location == null || location.isBlank()) {
            throw new IOException("GitHub release page redirect has no location");
        }
        URI target;
        try {
            target = access.endpoint().resolve(location.trim());
        } catch (IllegalArgumentException invalid) {
            throw new IOException("GitHub release page redirect has an invalid location", invalid);
        }
        String releasesPath = "/" + options.owner() + "/" + options.repository() + "/releases";
        String path = target.getPath();
        if (!"https".equalsIgnoreCase(target.getScheme()) || !"github.com".equalsIgnoreCase(target.getHost())
                || target.getUserInfo() != null || (target.getPort() != -1 && target.getPort() != 443)
                || path == null || !path.regionMatches(true, 0, releasesPath, 0, releasesPath.length())) {
            throw new IOException("GitHub release page redirected outside the " + options.owner() + "/"
                    + options.repository() + " releases");
        }
        String remainder = path.substring(releasesPath.length());
        if (remainder.isEmpty() || remainder.equals("/")) {
            return Optional.empty();
        }
        if (!remainder.startsWith("/tag/") || remainder.length() == "/tag/".length()) {
            throw new IOException("GitHub release page redirect does not name a release tag");
        }
        return Optional.of(new Release(remainder.substring("/tag/".length()), target.toString()));
    }

    private static long retryDelay(HttpURLConnection connection) {
        long seconds = 60L;
        String retry = connection.getHeaderField("Retry-After");
        if (retry != null) {
            try {
                seconds = Math.max(seconds, Long.parseLong(retry.trim()));
            } catch (NumberFormatException ignored) {
                try {
                    seconds = Math.max(seconds, Duration.between(Instant.now(),
                            ZonedDateTime.parse(retry.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds() + 1L);
                } catch (DateTimeException invalid) {
                    seconds = 60L;
                }
            }
        }
        return Math.min(seconds, TimeUnit.DAYS.toSeconds(7)) * 1000L;
    }

    private static final class RetryLater extends IOException {
        private final long delayMillis;

        private RetryLater(String message, long delayMillis) {
            super(message);
            this.delayMillis = delayMillis;
        }
    }

    public enum Status {
        DISABLED, NO_RELEASE, CURRENT, UPDATE, UNKNOWN, FAILED
    }

    public record Report(Status status, Release release, String error, long checkedMillis, long lastSuccessMillis) {
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

    record Access(URI endpoint, Duration cooldown) {
        Access {
            Objects.requireNonNull(endpoint, "endpoint");
            Objects.requireNonNull(cooldown, "cooldown");
            if (cooldown.toMillis() <= 0L) {
                throw new IllegalArgumentException("Manual release check cooldown must be positive");
            }
        }
    }
}

package art.arcane.volmlib.util.localization;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class RemoteLanguageCatalog implements AutoCloseable {
    private static final int CONNECT_TIMEOUT_MILLIS = 3_000;
    private static final int READ_TIMEOUT_MILLIS = 5_000;
    private static final int MAXIMUM_DOWNLOAD_BYTES = 2 * 1024 * 1024;
    private static final long FAILURE_RETRY_COOLDOWN_NANOS = TimeUnit.SECONDS.toNanos(30L);
    private static final long SHUTDOWN_TIMEOUT_MILLIS = 2_000L;
    private static final Pattern SOURCE_REFERENCE_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern LOCALE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{2,32}");

    private final Options options;
    private final Source source;
    private final Set<String> verifiedCache = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> failedAtNanos = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<DownloadResult>>> completions = new LinkedHashMap<>();
    private final Map<InstallRequest, List<Consumer<DownloadResult>>> installCompletions = new LinkedHashMap<>();
    private final Object requestLock = new Object();
    private final AtomicLong lifecycle = new AtomicLong(1L);
    private final ExecutorService executor;

    private RemoteLanguageCatalog(Options options, Source source) {
        this.options = options;
        this.source = source;
        executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, options.productName() + "-Language-Download");
            thread.setDaemon(true);
            return thread;
        });
    }

    public static RemoteLanguageCatalog load(Options options) {
        Options requiredOptions = Objects.requireNonNull(options, "options");
        return new RemoteLanguageCatalog(requiredOptions, loadSource(requiredOptions));
    }

    public String revision() {
        return source.revision();
    }

    public Set<String> availableLocales() {
        return source.locales();
    }

    public Path cacheFile(String locale) {
        String requiredLocale = requireSupportedLocale(locale);
        return options.cacheDirectory()
                .resolve(source.revision())
                .resolve(requiredLocale + options.extension())
                .toAbsolutePath()
                .normalize();
    }

    public URI sourceUri(String locale) {
        String requiredLocale = requireSupportedLocale(locale);
        String path = source.revision() + "/" + normalizedSourcePath(options.sourcePath())
                + requiredLocale + options.extension();
        return options.repositoryRoot().resolve(path);
    }

    public CacheResult read(String locale, ContentValidator validator) {
        Objects.requireNonNull(validator, "validator");
        if (!source.locales().contains(locale)) {
            return new CacheResult(CacheState.UNSUPPORTED, locale, null, null, null);
        }
        Path target = cacheFile(locale);
        if (!Files.isRegularFile(target)) {
            verifiedCache.remove(locale);
            return new CacheResult(CacheState.MISSING, locale, target, null, null);
        }
        try {
            byte[] bytes = readBounded(target);
            verifyHash(locale, bytes);
            String content = decode(bytes);
            validator.validate(locale, content);
            verifiedCache.add(locale);
            return new CacheResult(CacheState.VALID, locale, target, content, null);
        } catch (Throwable failure) {
            verifiedCache.remove(locale);
            return new CacheResult(CacheState.INVALID, locale, target, null, failure);
        }
    }

    public RequestState request(String locale, ContentValidator validator, Consumer<DownloadResult> completion) {
        Objects.requireNonNull(validator, "validator");
        Objects.requireNonNull(completion, "completion");
        synchronized (requestLock) {
            if (!source.locales().contains(locale)) {
                return RequestState.UNSUPPORTED;
            }
            if (executor.isShutdown()) {
                return RequestState.CLOSED;
            }
            if (verifiedCache.contains(locale)) {
                return RequestState.CURRENT;
            }
            Long failedAt = failedAtNanos.get(locale);
            long now = System.nanoTime();
            if (failedAt != null && now - failedAt < FAILURE_RETRY_COOLDOWN_NANOS) {
                return RequestState.COOLDOWN;
            }
            if (failedAt != null) {
                failedAtNanos.remove(locale, failedAt);
            }
            List<Consumer<DownloadResult>> listeners = completions.get(locale);
            if (listeners != null) {
                listeners.add(completion);
                return RequestState.IN_FLIGHT;
            }
            completions.put(locale, new ArrayList<>(List.of(completion)));
            long generation = lifecycle.get();
            try {
                executor.execute(() -> download(locale, generation, validator));
                return RequestState.SCHEDULED;
            } catch (RejectedExecutionException exception) {
                completions.remove(locale);
                return RequestState.CLOSED;
            }
        }
    }

    public RequestState requestInstallIfMissing(
            String locale,
            Path destination,
            ContentValidator validator,
            Consumer<DownloadResult> completion
    ) {
        Objects.requireNonNull(validator, "validator");
        Objects.requireNonNull(completion, "completion");
        Path target = Objects.requireNonNull(destination, "destination").toAbsolutePath().normalize();
        synchronized (requestLock) {
            if (!source.locales().contains(locale)) {
                return RequestState.UNSUPPORTED;
            }
            String requiredLocale = locale;
            if (Files.exists(target)) {
                return RequestState.CURRENT;
            }
            if (executor.isShutdown()) {
                return RequestState.CLOSED;
            }
            Long failedAt = failedAtNanos.get(requiredLocale);
            long now = System.nanoTime();
            if (failedAt != null && now - failedAt < FAILURE_RETRY_COOLDOWN_NANOS) {
                return RequestState.COOLDOWN;
            }
            if (failedAt != null) {
                failedAtNanos.remove(requiredLocale, failedAt);
            }
            InstallRequest request = new InstallRequest(requiredLocale, target);
            List<Consumer<DownloadResult>> listeners = installCompletions.get(request);
            if (listeners != null) {
                listeners.add(completion);
                return RequestState.IN_FLIGHT;
            }
            installCompletions.put(request, new ArrayList<>(List.of(completion)));
            long generation = lifecycle.get();
            try {
                executor.execute(() -> downloadInstall(request, generation, validator));
                return RequestState.SCHEDULED;
            } catch (RejectedExecutionException exception) {
                installCompletions.remove(request);
                return RequestState.CLOSED;
            }
        }
    }

    @Override
    public void close() {
        lifecycle.incrementAndGet();
        synchronized (requestLock) {
            completions.clear();
            installCompletions.clear();
        }
        verifiedCache.clear();
        failedAtNanos.clear();
        executor.shutdownNow();
        try {
            executor.awaitTermination(SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void download(
            String locale,
            long generation,
            ContentValidator validator
    ) {
        Path target = cacheFile(locale);
        URI source = sourceUri(locale);
        DownloadResult result;
        try {
            byte[] bytes = fetch(locale, source);
            verifyHash(locale, bytes);
            String content = decode(bytes);
            validator.validate(locale, content);
            publishAtomic(target, bytes);
            verifiedCache.add(locale);
            failedAtNanos.remove(locale);
            result = new DownloadResult(locale, source, target, null);
        } catch (Throwable failure) {
            verifiedCache.remove(locale);
            failedAtNanos.put(locale, System.nanoTime());
            result = new DownloadResult(locale, source, target, failure);
        }
        List<Consumer<DownloadResult>> listeners;
        synchronized (requestLock) {
            listeners = completions.remove(locale);
        }
        if (generation != lifecycle.get() || listeners == null) {
            return;
        }
        RuntimeException completionFailure = null;
        for (Consumer<DownloadResult> listener : listeners) {
            try {
                listener.accept(result);
            } catch (RuntimeException exception) {
                if (completionFailure == null) {
                    completionFailure = exception;
                } else {
                    completionFailure.addSuppressed(exception);
                }
            }
        }
        if (completionFailure != null) {
            throw completionFailure;
        }
    }

    private void downloadInstall(
            InstallRequest request,
            long generation,
            ContentValidator validator
    ) {
        URI sourceUri = sourceUri(request.locale());
        DownloadResult result;
        try {
            byte[] bytes = fetch(request.locale(), sourceUri);
            verifyHash(request.locale(), bytes);
            String content = decode(bytes);
            validator.validate(request.locale(), content);
            publishAtomicIfMissing(request.destination(), bytes);
            failedAtNanos.remove(request.locale());
            result = new DownloadResult(request.locale(), sourceUri, request.destination(), null);
        } catch (Throwable failure) {
            failedAtNanos.put(request.locale(), System.nanoTime());
            result = new DownloadResult(request.locale(), sourceUri, request.destination(), failure);
        }
        List<Consumer<DownloadResult>> listeners;
        synchronized (requestLock) {
            listeners = installCompletions.remove(request);
        }
        deliver(generation, listeners, result);
    }

    private void deliver(
            long generation,
            List<Consumer<DownloadResult>> listeners,
            DownloadResult result
    ) {
        if (generation != lifecycle.get() || listeners == null) {
            return;
        }
        RuntimeException completionFailure = null;
        for (Consumer<DownloadResult> listener : listeners) {
            try {
                listener.accept(result);
            } catch (RuntimeException exception) {
                if (completionFailure == null) {
                    completionFailure = exception;
                } else {
                    completionFailure.addSuppressed(exception);
                }
            }
        }
        if (completionFailure != null) {
            throw completionFailure;
        }
    }

    private byte[] fetch(String locale, URI uri) throws IOException {
        URLConnection connection;
        try {
            connection = uri.toURL().openConnection();
        } catch (IOException failure) {
            throw fetchFailure(locale, uri, failure);
        }
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "text/plain");
        connection.setRequestProperty("User-Agent", options.productName() + "-Language-Download");
        HttpURLConnection http = connection instanceof HttpURLConnection value ? value : null;
        if (http != null) {
            http.setInstanceFollowRedirects(false);
        }
        try {
            if (connection.getContentLengthLong() > MAXIMUM_DOWNLOAD_BYTES) {
                throw new IOException("Downloaded locale exceeds " + MAXIMUM_DOWNLOAD_BYTES + " bytes");
            }
            if (http != null && http.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Unable to fetch language file " + locale + " from " + uri
                        + ": HTTP " + http.getResponseCode());
            }
            try (InputStream input = connection.getInputStream()) {
                byte[] bytes = input.readNBytes(MAXIMUM_DOWNLOAD_BYTES + 1);
                if (bytes.length > MAXIMUM_DOWNLOAD_BYTES) {
                    throw new IOException("Downloaded locale exceeds " + MAXIMUM_DOWNLOAD_BYTES + " bytes");
                }
                return bytes;
            }
        } catch (IOException failure) {
            if (failure.getMessage() != null && failure.getMessage().startsWith("Unable to fetch language file ")) {
                throw failure;
            }
            throw fetchFailure(locale, uri, failure);
        } finally {
            if (http != null) {
                http.disconnect();
            }
        }
    }

    private IOException fetchFailure(String locale, URI uri, IOException cause) {
        String detail = cause.getMessage() == null || cause.getMessage().isBlank()
                ? cause.getClass().getSimpleName()
                : cause.getMessage();
        return new IOException("Unable to fetch language file " + locale + " from " + uri + ": " + detail, cause);
    }

    private byte[] readBounded(Path file) throws IOException {
        long size = Files.size(file);
        if (size > MAXIMUM_DOWNLOAD_BYTES) {
            throw new IOException("Cached locale exceeds " + MAXIMUM_DOWNLOAD_BYTES + " bytes");
        }
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length > MAXIMUM_DOWNLOAD_BYTES) {
            throw new IOException("Cached locale exceeds " + MAXIMUM_DOWNLOAD_BYTES + " bytes");
        }
        return bytes;
    }

    private void verifyHash(String locale, byte[] bytes) throws NoSuchAlgorithmException {
        String expected = source.hashes().get(locale);
        if (expected == null) {
            return;
        }
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII)
        )) {
            throw new IllegalArgumentException("Locale checksum does not match the pinned source");
        }
    }

    private String decode(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private void publishAtomic(Path target, byte[] bytes) throws IOException {
        Path absoluteTarget = target.toAbsolutePath().normalize();
        Path parent = absoluteTarget.getParent();
        if (parent == null) {
            throw new IOException("Language cache target has no parent: " + absoluteTarget);
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "." + absoluteTarget.getFileName() + ".", ".tmp");
        boolean published = false;
        try {
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
            try {
                Files.move(
                        temporary,
                        absoluteTarget,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("Language cache does not support atomic publication", exception);
            }
            published = true;
        } finally {
            if (!published) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private void publishAtomicIfMissing(Path target, byte[] bytes) throws IOException {
        Path absoluteTarget = target.toAbsolutePath().normalize();
        Path parent = absoluteTarget.getParent();
        if (parent == null) {
            throw new IOException("Language target has no parent: " + absoluteTarget);
        }
        if (Files.exists(parent) && (!Files.isDirectory(parent) || Files.isSymbolicLink(parent))) {
            throw new IOException("Language target parent is not a regular directory: " + parent);
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "." + absoluteTarget.getFileName() + ".", ".tmp");
        try {
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
            try {
                Files.createLink(absoluteTarget, temporary);
            } catch (FileAlreadyExistsException exception) {
                if (!Files.isRegularFile(absoluteTarget) || Files.isSymbolicLink(absoluteTarget)) {
                    throw new IOException("Language target is not a regular file: " + absoluteTarget, exception);
                }
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private String requireSupportedLocale(String locale) {
        if (locale == null || !source.locales().contains(locale)) {
            throw new IllegalArgumentException("Unsupported locale: " + locale);
        }
        return locale;
    }

    private static Source loadSource(Options options) {
        Properties properties = new Properties();
        try (InputStream input = options.resourceLoader().getResourceAsStream(options.manifestResource())) {
            if (input == null) {
                throw new IllegalStateException("Missing " + options.manifestResource());
            }
            properties.load(input);
        } catch (IOException failure) {
            throw new IllegalStateException("Failed to read " + options.manifestResource(), failure);
        }
        String revision = properties.getProperty("revision", "").trim();
        if (!SOURCE_REFERENCE_PATTERN.matcher(revision).matches()) {
            throw new IllegalStateException("Invalid language source reference");
        }
        LinkedHashSet<String> availableLocales = new LinkedHashSet<>();
        LinkedHashMap<String, String> hashes = new LinkedHashMap<>();
        String[] locales = properties.getProperty("locales", "").split(",");
        for (String rawLocale : locales) {
            String locale = rawLocale.trim();
            String hash = properties.getProperty("sha256." + locale, "").trim().toLowerCase(Locale.ROOT);
            if (!LOCALE_PATTERN.matcher(locale).matches() || (!hash.isEmpty() && !SHA256_PATTERN.matcher(hash).matches())) {
                throw new IllegalStateException("Invalid language source entry: " + locale);
            }
            if (!availableLocales.add(locale)) {
                throw new IllegalStateException("Duplicate language source entry: " + locale);
            }
            if (!hash.isEmpty()) {
                hashes.put(locale, hash);
            }
        }
        if (availableLocales.isEmpty()) {
            throw new IllegalStateException("Language source manifest contains no locales");
        }
        return new Source(
                revision,
                Collections.unmodifiableSet(availableLocales),
                Collections.unmodifiableMap(hashes)
        );
    }

    private static String normalizedSourcePath(String path) {
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

    @FunctionalInterface
    public interface ContentValidator {
        void validate(String locale, String content) throws Exception;
    }

    public record Options(
            String productName,
            URI repositoryRoot,
            String sourcePath,
            String extension,
            String manifestResource,
            Path cacheDirectory,
            ClassLoader resourceLoader
    ) {
        public Options {
            Objects.requireNonNull(productName, "productName");
            Objects.requireNonNull(repositoryRoot, "repositoryRoot");
            Objects.requireNonNull(sourcePath, "sourcePath");
            Objects.requireNonNull(extension, "extension");
            Objects.requireNonNull(manifestResource, "manifestResource");
            Objects.requireNonNull(cacheDirectory, "cacheDirectory");
            Objects.requireNonNull(resourceLoader, "resourceLoader");
            if (!repositoryRoot.toString().endsWith("/")) {
                throw new IllegalArgumentException("Repository root must end with /");
            }
            if (!extension.startsWith(".")) {
                throw new IllegalArgumentException("Language extension must start with .");
            }
        }
    }

    public enum CacheState {
        VALID,
        MISSING,
        INVALID,
        UNSUPPORTED
    }

    public enum RequestState {
        SCHEDULED,
        CURRENT,
        IN_FLIGHT,
        COOLDOWN,
        UNSUPPORTED,
        CLOSED
    }

    public record CacheResult(
            CacheState state,
            String locale,
            Path file,
            String content,
            Throwable failure
    ) {
    }

    public record DownloadResult(String locale, URI source, Path file, Throwable failure) {
        public boolean successful() {
            return failure == null;
        }
    }

    private record InstallRequest(String locale, Path destination) {
    }

    private record Source(String revision, Set<String> locales, Map<String, String> hashes) {
    }
}

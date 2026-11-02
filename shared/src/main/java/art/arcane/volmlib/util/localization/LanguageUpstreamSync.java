package art.arcane.volmlib.util.localization;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LanguageUpstreamSync implements AutoCloseable {
    public static final Duration MINIMUM_CHECK_INTERVAL = Duration.ofMinutes(5L);
    private static final long FAILURE_COOLDOWN_NANOS = TimeUnit.SECONDS.toNanos(30L);

    private final Options options;
    private final long minimumIntervalMillis;
    private final ExecutorService worker;
    private final Object lock = new Object();
    private volatile CompletableFuture<SyncReport> inFlight;
    private volatile long nextAllowedNanos;
    private volatile String lastError;
    private volatile long lastCheckMillis;
    private volatile boolean closed;

    public LanguageUpstreamSync(Options options) {
        this(options, MINIMUM_CHECK_INTERVAL);
    }

    public LanguageUpstreamSync(Options options, Duration minimumCheckInterval) {
        this.options = Objects.requireNonNull(options, "options");
        minimumIntervalMillis = Math.max(0L, Objects.requireNonNull(minimumCheckInterval, "minimumCheckInterval").toMillis());
        worker = Executors.newSingleThreadExecutor(action -> {
            Thread thread = new Thread(action, options.productName() + "-Language-Sync");
            thread.setDaemon(true);
            return thread;
        });
    }

    public LanguageSourceManifest manifest() {
        return options.manifest();
    }

    public LanguageDirectory directory() {
        return options.directory();
    }

    public Status status() {
        Optional<String> revision;
        long checked;
        try {
            LanguageUpstreamStore.State state = options.store().load();
            revision = state.revision();
            checked = state.checkedMillis();
        } catch (IOException exception) {
            revision = Optional.empty();
            checked = 0L;
        }
        return new Status(revision, checked, lastCheckMillis, Optional.ofNullable(lastError), inFlight != null);
    }

    public CompletableFuture<SyncReport> checkAsync() {
        synchronized (lock) {
            CompletableFuture<SyncReport> pending = inFlight;
            if (pending != null) {
                return pending;
            }
            CompletableFuture<SyncReport> future = new CompletableFuture<>();
            if (closed) {
                future.complete(SyncReport.failed("Language sync is closed", System.currentTimeMillis()));
                return future;
            }
            inFlight = future;
            try {
                worker.execute(() -> {
                    SyncReport report;
                    try {
                        report = check();
                    } catch (Throwable failure) {
                        report = SyncReport.failed(describe(failure), System.currentTimeMillis());
                        options.logger().log(Level.WARNING, options.productName()
                                + " language update failed", failure);
                    }
                    synchronized (lock) {
                        inFlight = null;
                    }
                    future.complete(report);
                });
            } catch (RejectedExecutionException rejected) {
                inFlight = null;
                future.complete(SyncReport.failed("Language sync is closed", System.currentTimeMillis()));
            }
            return future;
        }
    }

    public SyncReport check() {
        long startedMillis = System.currentTimeMillis();
        LinkedHashMap<String, LanguageCatalogMerge.MergeResult> merged = new LinkedHashMap<>();
        LinkedHashMap<String, String> failures = new LinkedHashMap<>();
        try {
            mergeEnglish().ifPresent(result -> merged.put(options.catalog().englishLocale(), result));
        } catch (Exception failure) {
            failures.put(options.catalog().englishLocale(), describe(failure));
            options.logger().log(Level.WARNING, options.productName() + " could not refresh the English language file",
                    failure);
        }
        long backoffRemaining = TimeUnit.NANOSECONDS.toMillis(nextAllowedNanos - System.nanoTime());
        if (backoffRemaining > 0L) {
            lastCheckMillis = startedMillis;
            return new SyncReport(revisionOrEmpty(), true, merged, failures,
                    Optional.ofNullable(lastError), startedMillis, backoffRemaining);
        }
        LanguageUpstreamStore.State state;
        try {
            state = options.store().load();
        } catch (IOException failure) {
            return fail(merged, failures, failure, startedMillis);
        }
        long intervalRemaining = state.checkedMillis() + minimumIntervalMillis - startedMillis;
        if (state.checkedMillis() > 0L && intervalRemaining > 0L) {
            lastCheckMillis = startedMillis;
            return new SyncReport(state.revision(), true, merged, failures, Optional.empty(), startedMillis,
                    intervalRemaining);
        }
        GitHubLanguageSource.RevisionCheck checked;
        try {
            checked = options.source().latestRevision(state.etag().orElse(null));
        } catch (IOException failure) {
            try {
                options.store().save(state.withChecked(Optional.empty(), startedMillis));
            } catch (IOException ignored) {
                failures.put("state", describe(ignored));
            }
            return fail(merged, failures, failure, startedMillis);
        }
        String revision;
        boolean revisionChanged;
        if (checked.unchanged()) {
            if (state.revision().isEmpty()) {
                return fail(merged, failures, new IOException("GitHub reported no change without a stored revision"),
                        startedMillis);
            }
            revision = state.revision().get();
            revisionChanged = false;
            state = state.withChecked(checked.etag(), startedMillis);
        } else {
            revision = checked.revision().orElseThrow();
            revisionChanged = state.revision().map(stored -> !stored.equals(revision)).orElse(true);
            state = state.withRevision(revision, checked.etag(), startedMillis);
        }
        List<String> installed;
        try {
            installed = installedManifestLocales();
        } catch (IOException failure) {
            return fail(merged, failures, failure, startedMillis);
        }
        boolean fetchFailed = false;
        for (String locale : installed) {
            Optional<String> previous;
            try {
                previous = options.store().readUpstream(locale);
            } catch (IOException failure) {
                failures.put(locale, describe(failure));
                continue;
            }
            boolean current = !revisionChanged && previous.isPresent()
                    && state.hashes().getOrDefault(locale, "").equals(LanguageUpstreamStore.sha256(previous.get()));
            if (current) {
                continue;
            }
            String upstream;
            try {
                upstream = LanguageDirectory.decode(options.source().fetch(locale, revision));
            } catch (IOException failure) {
                failures.put(locale, describe(failure));
                fetchFailed = true;
                if (failure instanceof GitHubLanguageSource.RetryLater retryLater) {
                    nextAllowedNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(retryLater.delayMillis());
                    break;
                }
                continue;
            }
            try {
                LanguageCatalogMerge.MergeResult result = apply(locale, previous.orElse(null), upstream);
                merged.put(locale, result);
                state = state.withHash(locale, LanguageUpstreamStore.sha256(upstream));
            } catch (Exception failure) {
                failures.put(locale, describe(failure));
                options.logger().log(Level.WARNING, options.productName() + " could not update language " + locale,
                        failure);
            }
        }
        try {
            options.store().save(state);
        } catch (IOException failure) {
            failures.put("state", describe(failure));
        }
        lastCheckMillis = startedMillis;
        if (fetchFailed) {
            if (nextAllowedNanos < System.nanoTime()) {
                nextAllowedNanos = System.nanoTime() + FAILURE_COOLDOWN_NANOS;
            }
            lastError = failures.values().iterator().next();
        } else {
            lastError = null;
        }
        boolean unchanged = !revisionChanged && merged.values().stream().noneMatch(LanguageCatalogMerge.MergeResult::changed);
        return new SyncReport(Optional.of(revision), unchanged, merged, failures, Optional.empty(), startedMillis, 0L);
    }

    public CompletableFuture<String> installAsync(String locale) {
        CompletableFuture<String> future = new CompletableFuture<>();
        if (closed) {
            future.completeExceptionally(new IOException("Language sync is closed"));
            return future;
        }
        try {
            worker.execute(() -> {
                try {
                    future.complete(install(locale));
                } catch (Throwable failure) {
                    future.completeExceptionally(failure);
                }
            });
        } catch (RejectedExecutionException rejected) {
            future.completeExceptionally(new IOException("Language sync is closed", rejected));
        }
        return future;
    }

    public String install(String locale) throws IOException {
        String canonical = options.manifest().canonical(locale);
        synchronized (lock) {
            if (options.directory().exists(canonical)) {
                return options.directory().read(canonical);
            }
        }
        LanguageUpstreamStore.State state = options.store().load();
        String revision;
        if (state.revision().isPresent()) {
            revision = state.revision().get();
        } else {
            GitHubLanguageSource.RevisionCheck checked = options.source().latestRevision(null);
            revision = checked.revision().orElseThrow(() ->
                    new IOException("GitHub reported no change without a stored revision"));
            state = state.withRevision(revision, checked.etag(), System.currentTimeMillis());
        }
        String upstream = LanguageDirectory.decode(options.source().fetch(canonical, revision));
        LanguageCatalogMerge.MergeResult result = LanguageCatalogMerge.merge(options.catalog(), null, null, upstream);
        try {
            options.validator().validate(canonical, result.content());
        } catch (Exception failure) {
            throw new IOException("Downloaded locale is invalid: " + canonical, failure);
        }
        synchronized (lock) {
            if (options.directory().exists(canonical)) {
                return options.directory().read(canonical);
            }
            options.directory().prepare();
            String written = LanguageFileEditor.update(options.directory().languageFile(canonical), existing ->
                    new LanguageFileEditor.Prepared<>(result.content(), result.content()));
            options.store().writeUpstream(canonical, upstream);
            options.store().save(state.withHash(canonical, LanguageUpstreamStore.sha256(upstream)));
            notifySelfWrite(canonical, written);
            return written;
        }
    }

    public Optional<LanguageCatalogMerge.MergeResult> mergeEnglish() throws Exception {
        String english = options.catalog().englishLocale();
        String reference = Objects.requireNonNull(options.englishReference().get(), "English reference");
        synchronized (lock) {
            options.directory().prepare();
            Optional<String> previous = options.store().readUpstream(english);
            if (!options.directory().exists(english)) {
                LanguageFileEditor.update(options.directory().languageFile(english), existing ->
                        new LanguageFileEditor.Prepared<>(reference, reference));
                options.store().writeUpstream(english, reference);
                notifySelfWrite(english, reference);
                return Optional.empty();
            }
            if (previous.isPresent() && previous.get().equals(reference)) {
                return Optional.empty();
            }
            LanguageCatalogMerge.MergeResult result = apply(english, previous.orElse(null), reference);
            return Optional.of(result);
        }
    }

    private LanguageCatalogMerge.MergeResult apply(String locale, String previous, String upstream) throws Exception {
        synchronized (lock) {
            Path file = options.directory().languageFile(locale);
            LanguageCatalogMerge.MergeResult result = options.committer().commit(() -> {
                if (!options.directory().exists(locale)) {
                    throw new IOException("Language file is no longer installed: " + locale);
                }
                return LanguageFileEditor.update(file, existing -> {
                    LanguageCatalogMerge.MergeResult merge = LanguageCatalogMerge.merge(
                            options.catalog(), existing, previous, upstream);
                    if (!merge.changed()) {
                        return new LanguageFileEditor.Prepared<>(existing, merge);
                    }
                    try {
                        options.validator().validate(locale, merge.content());
                    } catch (Exception failure) {
                        throw new IOException("Merged language file is invalid: " + locale, failure);
                    }
                    return new LanguageFileEditor.Prepared<>(merge.content(), merge);
                });
            });
            options.store().writeUpstream(locale, upstream);
            if (result.changed()) {
                notifySelfWrite(locale, result.content());
                options.applied().applied(locale, result.content(), result);
            }
            return result;
        }
    }

    private List<String> installedManifestLocales() throws IOException {
        ArrayList<String> installed = new ArrayList<>();
        for (String locale : options.directory().scan()) {
            if (LanguageDirectory.sameLocale(locale, options.catalog().englishLocale())) {
                continue;
            }
            if (options.manifest().contains(locale)) {
                installed.add(options.manifest().canonical(locale));
            }
        }
        return installed;
    }

    private void notifySelfWrite(String locale, String content) {
        SelfWriteListener listener = options.selfWrite();
        if (listener == null) {
            return;
        }
        try {
            listener.written(options.directory().languageFile(locale), content);
        } catch (RuntimeException failure) {
            options.logger().log(Level.WARNING, options.productName()
                    + " wrote language " + locale + " but could not register it with hot reload", failure);
        }
    }

    private SyncReport fail(Map<String, LanguageCatalogMerge.MergeResult> merged, Map<String, String> failures,
                            IOException failure, long startedMillis) {
        long delay = failure instanceof GitHubLanguageSource.RetryLater retryLater
                ? TimeUnit.MILLISECONDS.toNanos(retryLater.delayMillis())
                : FAILURE_COOLDOWN_NANOS;
        nextAllowedNanos = System.nanoTime() + delay;
        lastCheckMillis = startedMillis;
        String message = describe(failure);
        if (!message.equals(lastError)) {
            options.logger().log(Level.WARNING, options.productName() + " could not check the language source: "
                    + message);
        }
        lastError = message;
        return new SyncReport(revisionOrEmpty(), false, merged, failures, Optional.of(message), startedMillis, 0L);
    }

    private Optional<String> revisionOrEmpty() {
        try {
            return options.store().load().revision();
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private static String describe(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    @Override
    public void close() {
        closed = true;
        worker.shutdownNow();
    }

    public record Options(String productName, MessageCatalog catalog, LanguageDirectory directory,
                          LanguageSourceManifest manifest, GitHubLanguageSource source, LanguageUpstreamStore store,
                          Supplier<String> englishReference, ContentValidator validator, Committer committer,
                          SelfWriteListener selfWrite, Applied applied, Logger logger) {
        public Options {
            Objects.requireNonNull(productName, "productName");
            Objects.requireNonNull(catalog, "catalog");
            Objects.requireNonNull(directory, "directory");
            Objects.requireNonNull(manifest, "manifest");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(store, "store");
            Objects.requireNonNull(englishReference, "englishReference");
            Objects.requireNonNull(validator, "validator");
            committer = committer == null ? Committer.DIRECT : committer;
            applied = applied == null ? (locale, content, result) -> { } : applied;
            Objects.requireNonNull(logger, "logger");
        }
    }

    @FunctionalInterface
    public interface ContentValidator {
        void validate(String locale, String content) throws Exception;
    }

    @FunctionalInterface
    public interface Committer {
        Committer DIRECT = CommitAction::apply;

        <T> T commit(CommitAction<T> action) throws IOException;
    }

    @FunctionalInterface
    public interface CommitAction<T> {
        T apply() throws IOException;
    }

    @FunctionalInterface
    public interface SelfWriteListener {
        void written(Path file, String content);
    }

    @FunctionalInterface
    public interface Applied {
        void applied(String locale, String content, LanguageCatalogMerge.MergeResult result) throws Exception;
    }

    public record Status(Optional<String> revision, long checkedMillis, long lastCheckMillis,
                         Optional<String> lastError, boolean checking) {
    }

    public record SyncReport(Optional<String> revision, boolean unchanged,
                             Map<String, LanguageCatalogMerge.MergeResult> merged, Map<String, String> failures,
                             Optional<String> failure, long checkedMillis, long cooldownRemainingMillis) {
        public SyncReport {
            Objects.requireNonNull(revision, "revision");
            merged = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(merged, "merged")));
            failures = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(failures, "failures")));
            Objects.requireNonNull(failure, "failure");
            cooldownRemainingMillis = Math.max(0L, cooldownRemainingMillis);
        }

        public static SyncReport failed(String reason, long checkedMillis) {
            return new SyncReport(Optional.empty(), false, Map.of(), Map.of(), Optional.of(reason), checkedMillis, 0L);
        }

        public boolean successful() {
            return failure.isEmpty() && failures.isEmpty();
        }

        public boolean coolingDown() {
            return cooldownRemainingMillis > 0L;
        }

        public String cooldownRemaining() {
            long seconds = (cooldownRemainingMillis + 999L) / 1000L;
            long minutes = seconds / 60L;
            long rest = seconds % 60L;
            if (minutes > 0L) {
                return rest > 0L ? minutes + "m " + rest + "s" : minutes + "m";
            }
            return seconds + "s";
        }

        public List<String> changedLocales() {
            ArrayList<String> changed = new ArrayList<>();
            for (Map.Entry<String, LanguageCatalogMerge.MergeResult> entry : merged.entrySet()) {
                if (entry.getValue().changed()) {
                    changed.add(entry.getKey());
                }
            }
            return List.copyOf(changed);
        }
    }
}

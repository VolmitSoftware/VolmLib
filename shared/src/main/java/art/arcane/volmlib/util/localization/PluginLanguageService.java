package art.arcane.volmlib.util.localization;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public final class PluginLanguageService implements AutoCloseable {
    private static final Pattern LOCALE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{2,32}");
    private static final long RETRY_NANOS = TimeUnit.SECONDS.toNanos(30L);

    private final Options options;
    private final ExecutorService worker;
    private final Map<UUID, String> preferences = new ConcurrentHashMap<>();
    private final Map<String, LocalizationSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<String, Long> failedLoads = new ConcurrentHashMap<>();
    private final Set<String> loading = ConcurrentHashMap.newKeySet();
    private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();
    private final AtomicLong generation = new AtomicLong();
    private final Object commitLock = new Object();
    private volatile boolean closed;

    public PluginLanguageService(Options options) {
        this.options = Objects.requireNonNull(options, "options");
        worker = Executors.newSingleThreadExecutor(action -> {
            Thread thread = new Thread(action, "Volmit-Language-Selection");
            thread.setDaemon(true);
            return thread;
        });
        readPreferences();
    }

    public LocalizationSnapshot snapshot() {
        return snapshot(LanguageAudience.current());
    }

    public LocalizationSnapshot snapshot(UUID playerId) {
        String locale = playerId == null ? null : preferences.get(playerId);
        if (locale == null) {
            return options.defaultSnapshot().get();
        }
        LocalizationSnapshot prepared = snapshots.get(locale);
        if (prepared != null) {
            return prepared;
        }
        requestSnapshot(locale);
        return options.defaultSnapshot().get();
    }

    public List<String> availableLocales() {
        LinkedHashSet<String> locales = new LinkedHashSet<>();
        locales.add(VolmitLocales.ENGLISH);
        for (String locale : options.availableLocales().get()) {
            locales.add(requireLocale(locale));
        }
        ArrayList<String> sorted = new ArrayList<>(locales);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(sorted);
    }

    public String defaultLocale() {
        return options.defaultLocale().get();
    }

    public Optional<String> playerLocale(UUID playerId) {
        return Optional.ofNullable(preferences.get(Objects.requireNonNull(playerId, "playerId")));
    }

    public String effectiveLocale(UUID playerId) {
        return playerId == null ? defaultLocale() : preferences.getOrDefault(playerId, defaultLocale());
    }

    public CompletableFuture<Void> selectDefault(String requestedLocale) {
        return submit(result -> {
            String locale = availableLocale(requestedLocale);
            while (true) {
                long preparedGeneration = generation.get();
                PreparedSelection prepared = prepare(locale, result);
                synchronized (commitLock) {
                    requireActive(result);
                    if (preparedGeneration != generation.get()) {
                        continue;
                    }
                    options.defaultSelection().apply(prepared.locale(), prepared.snapshot());
                    snapshots.put(prepared.locale(), prepared.snapshot());
                    failedLoads.remove(locale);
                    reportFallback(locale, prepared);
                    result.complete(null);
                    return;
                }
            }
        });
    }

    public CompletableFuture<Void> selectPlayer(UUID playerId, String requestedLocale) {
        UUID requiredPlayer = Objects.requireNonNull(playerId, "playerId");
        return submit(result -> {
            String locale = availableLocale(requestedLocale);
            while (true) {
                long preparedGeneration = generation.get();
                PreparedSelection prepared = prepare(locale, result);
                synchronized (commitLock) {
                    requireActive(result);
                    if (preparedGeneration != generation.get()) {
                        continue;
                    }
                    Map<UUID, String> next = new HashMap<>(preferences);
                    next.put(requiredPlayer, prepared.locale());
                    writePreferences(next);
                    snapshots.put(prepared.locale(), prepared.snapshot());
                    failedLoads.remove(locale);
                    preferences.put(requiredPlayer, prepared.locale());
                    reportFallback(locale, prepared);
                    result.complete(null);
                    return;
                }
            }
        });
    }

    public CompletableFuture<Void> clearPlayer(UUID playerId) {
        UUID requiredPlayer = Objects.requireNonNull(playerId, "playerId");
        return submit(result -> {
            synchronized (commitLock) {
                requireOpen();
                Map<UUID, String> next = new HashMap<>(preferences);
                next.remove(requiredPlayer);
                writePreferences(next);
                preferences.remove(requiredPlayer);
                result.complete(null);
            }
        });
    }

    public void invalidate() {
        synchronized (commitLock) {
            generation.incrementAndGet();
            snapshots.clear();
            failedLoads.clear();
        }
    }

    public void cache(String locale, LocalizationSnapshot snapshot) {
        String requiredLocale = requireLocale(locale);
        LocalizationSnapshot requiredSnapshot = Objects.requireNonNull(snapshot, "snapshot");
        if (closed) {
            return;
        }
        snapshots.put(requiredLocale, requiredSnapshot);
        if (closed) {
            snapshots.remove(requiredLocale, requiredSnapshot);
            return;
        }
        failedLoads.remove(requiredLocale);
    }

    @Override
    public void close() {
        closed = true;
        generation.incrementAndGet();
        worker.shutdownNow();
        synchronized (commitLock) {
            for (CompletableFuture<?> future : pending) {
                future.completeExceptionally(new IllegalStateException("Language service is closed"));
            }
            pending.clear();
            snapshots.clear();
            loading.clear();
        }
    }

    private void requestSnapshot(String locale) {
        Long failureTime = failedLoads.get(locale);
        if (closed || failureTime != null && System.nanoTime() - failureTime < RETRY_NANOS
                || !loading.add(locale)) {
            return;
        }
        long requestedGeneration = generation.get();
        CompletableFuture<Void> future = submit(result -> {
            try {
                PreparedSelection prepared = preparePersisted(locale, result);
                synchronized (commitLock) {
                    if (!closed && requestedGeneration == generation.get()) {
                        if (!locale.equals(prepared.locale())) {
                            Map<UUID, String> next = new HashMap<>(preferences);
                            next.replaceAll((player, selected) -> selected.equals(locale) ? prepared.locale() : selected);
                            writePreferences(next);
                            snapshots.put(prepared.locale(), prepared.snapshot());
                            preferences.replaceAll((player, selected) -> selected.equals(locale) ? prepared.locale() : selected);
                        } else {
                            snapshots.put(prepared.locale(), prepared.snapshot());
                        }
                        failedLoads.remove(locale);
                        reportFallback(locale, prepared);
                    }
                }
            } catch (Exception exception) {
                failedLoads.put(locale, System.nanoTime());
                options.logger().log(Level.WARNING, "Unable to prepare player language " + locale, exception);
                throw exception;
            } finally {
                loading.remove(locale);
            }
        });
        future.whenComplete((ignored, failure) -> loading.remove(locale));
    }

    private PreparedSelection preparePersisted(String locale, CompletableFuture<Void> result) throws Exception {
        String available;
        try {
            available = availableLocale(locale);
        } catch (IllegalArgumentException exception) {
            return prepareEnglish(locale, exception, result);
        }
        return prepare(available, result);
    }

    private PreparedSelection prepare(String locale, CompletableFuture<Void> result) throws Exception {
        requireActive(result);
        try {
            return new PreparedSelection(locale, loadSnapshot(locale), null);
        } catch (Exception exception) {
            return prepareEnglish(locale, exception, result);
        }
    }

    private LocalizationSnapshot loadSnapshot(String locale) throws Exception {
        LocalizationSnapshot prepared = Objects.requireNonNull(options.loader().load(locale), "Language snapshot");
        for (MessageKey key : prepared.catalog().keys()) {
            if (!sameLocale(locale, prepared.sourceLocale(key))) {
                throw new IllegalArgumentException("Language " + locale + " is incomplete: " + key.id());
            }
        }
        return prepared;
    }

    private PreparedSelection prepareEnglish(String locale, Exception failure, CompletableFuture<Void> result)
            throws Exception {
        requireRecoverable(failure, result);
        if (!sameLocale(locale, VolmitLocales.ENGLISH)) {
            try {
                return new PreparedSelection(VolmitLocales.ENGLISH, loadSnapshot(VolmitLocales.ENGLISH), failure);
            } catch (Exception exception) {
                requireRecoverable(exception, result);
            }
        }
        LocalizationSnapshot english = LocalizationSnapshot.create(LocalizationCandidate.english(
                options.defaultSnapshot().get().catalog(), PluralSelector.oneOther()));
        return new PreparedSelection(VolmitLocales.ENGLISH, english, failure);
    }

    private void requireRecoverable(Exception failure, CompletableFuture<Void> result) throws Exception {
        if (failure instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            throw failure;
        }
        if (failure instanceof CancellationException || failure instanceof RejectedExecutionException) {
            throw failure;
        }
        requireActive(result);
    }

    private void requireActive(CompletableFuture<Void> result) {
        requireOpen();
        if (result.isCancelled()) {
            throw new CancellationException("Language selection was cancelled");
        }
    }

    private void reportFallback(String locale, PreparedSelection prepared) {
        if (prepared.failure() != null) {
            options.logger().warning("Language " + locale + " is unavailable; using English ("
                    + VolmitLocales.ENGLISH + "): " + prepared.failure().getMessage());
        }
    }

    private static boolean sameLocale(String first, String second) {
        return first.replace('-', '_').equalsIgnoreCase(second.replace('-', '_'));
    }

    private CompletableFuture<Void> submit(LanguageAction action) {
        return submitWork(result -> {
            action.run(result);
            return null;
        });
    }

    <T> CompletableFuture<T> submitWork(Work<T> work) {
        CompletableFuture<T> result = new CompletableFuture<>();
        FutureTask<Void> task = new FutureTask<>(() -> {
            try {
                requireOpen();
                if (!result.isCancelled()) {
                    result.complete(work.run(result));
                }
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
            return null;
        });
        result.whenComplete((value, failure) -> {
            pending.remove(result);
            if (result.isCancelled()) {
                task.cancel(true);
            }
        });
        if (closed) {
            result.completeExceptionally(new IllegalStateException("Language service is closed"));
            return result;
        }
        pending.add(result);
        if (closed) {
            result.completeExceptionally(new IllegalStateException("Language service is closed"));
            return result;
        }
        try {
            worker.execute(task);
        } catch (RejectedExecutionException exception) {
            result.completeExceptionally(exception);
        }
        return result;
    }

    private String availableLocale(String requested) {
        String normalized = requireLocale(requested).replace('-', '_');
        for (String locale : availableLocales()) {
            if (locale.replace('-', '_').equalsIgnoreCase(normalized)) {
                return locale;
            }
        }
        throw new IllegalArgumentException("Language is not available: " + requested);
    }

    private static String requireLocale(String locale) {
        String normalized = Objects.requireNonNull(locale, "locale").trim();
        if (!LOCALE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid locale: " + normalized);
        }
        return normalized;
    }

    private void requireOpen() {
        if (closed || Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("Language service is closed");
        }
    }

    private void readPreferences() {
        try {
            Map<UUID, String> stored = Objects.requireNonNull(
                    options.preferenceStore().load(),
                    "Language preference store result"
            );
            Map<UUID, String> loaded = new HashMap<>();
            for (Map.Entry<UUID, String> entry : stored.entrySet()) {
                loaded.put(Objects.requireNonNull(entry.getKey(), "Language preference player"),
                        requireLocale(entry.getValue()));
            }
            preferences.putAll(loaded);
        } catch (IOException | IllegalArgumentException exception) {
            options.logger().log(Level.SEVERE,
                    "Unable to load language preferences from " + options.preferenceStore().description(), exception);
        }
    }

    public <T> T commitUpdate(CommitUpdate<T> update) throws IOException {
        Objects.requireNonNull(update, "update");
        synchronized (commitLock) {
            requireOpen();
            return update.apply();
        }
    }

    private void writePreferences(Map<UUID, String> next) throws IOException {
        requireOpen();
        options.preferenceStore().save(Map.copyOf(next));
        requireOpen();
    }

    public record Options(
            LanguagePreferenceStore preferenceStore,
            Supplier<? extends Collection<String>> availableLocales,
            Supplier<String> defaultLocale,
            Supplier<LocalizationSnapshot> defaultSnapshot,
            SnapshotLoader loader,
            DefaultSelection defaultSelection,
            Logger logger
    ) {
        public Options(Path preferencesFile,
                       Supplier<? extends Collection<String>> availableLocales,
                       Supplier<String> defaultLocale,
                       Supplier<LocalizationSnapshot> defaultSnapshot,
                       SnapshotLoader loader,
                       DefaultSelection defaultSelection,
                       Logger logger) {
            this(new PropertiesLanguagePreferenceStore(preferencesFile), availableLocales, defaultLocale,
                    defaultSnapshot, loader, defaultSelection, logger);
        }

        public Options {
            Objects.requireNonNull(preferenceStore, "preferenceStore");
            Objects.requireNonNull(availableLocales, "availableLocales");
            Objects.requireNonNull(defaultLocale, "defaultLocale");
            Objects.requireNonNull(defaultSnapshot, "defaultSnapshot");
            Objects.requireNonNull(loader, "loader");
            Objects.requireNonNull(defaultSelection, "defaultSelection");
            Objects.requireNonNull(logger, "logger");
        }
    }

    @FunctionalInterface
    public interface SnapshotLoader {
        LocalizationSnapshot load(String locale) throws Exception;
    }

    @FunctionalInterface
    public interface CommitUpdate<T> {
        T apply() throws IOException;
    }

    @FunctionalInterface
    public interface DefaultSelection {
        void apply(String locale, LocalizationSnapshot prepared) throws Exception;
    }

    @FunctionalInterface
    interface Work<T> {
        T run(CompletableFuture<T> result) throws Exception;
    }

    @FunctionalInterface
    private interface LanguageAction {
        void run(CompletableFuture<Void> result) throws Exception;
    }

    private record PreparedSelection(String locale, LocalizationSnapshot snapshot, Exception failure) {
    }
}

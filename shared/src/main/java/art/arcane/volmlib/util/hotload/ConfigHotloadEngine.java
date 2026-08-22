package art.arcane.volmlib.util.hotload;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import art.arcane.volmlib.util.io.FileWatcher;
import art.arcane.volmlib.util.io.FolderWatcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Shared support for config hotloading workflows used across plugins.
 * The host plugin remains responsible for applying file-specific reload behavior.
 */
public class ConfigHotloadEngine {
    public static final String MISSING = "<missing>";
    public static final String REMOVED = "<removed>";
    public static final long DEFAULT_FULL_WATCH_SCAN_WINDOW_MS = 5_000L;
    public static final long DEFAULT_SIGNATURE_SCAN_WINDOW_MS = 2_500L;
    public static final long DEFAULT_HOTLOAD_COOLDOWN_MS = 3_000L;
    private static final long MAX_RECONCILIATION_FILE_BYTES = 2L * 1024L * 1024L;
    private static final long RECONCILIATION_BYTE_BUDGET = 8L * 1024L * 1024L;
    private static final long RECONCILIATION_TIME_BUDGET_NANOS = TimeUnit.MILLISECONDS.toNanos(10L);
    static final int RECONCILIATION_FILE_BUDGET = 32;

    private final Predicate<File> managedConfigFilePredicate;
    private final Supplier<? extends Collection<File>> knownFilesSupplier;
    private final Function<File, String> fileReader;
    private final UnaryOperator<String> normalizer;
    private final long fullWatchScanWindowMs;
    private final long signatureScanWindowMs;

    private final Object watcherStateLock = new Object();
    private final List<WatchedFile> fileWatchers = new ArrayList<>();
    private final List<WatchedDirectory> directoryWatchers = new ArrayList<>();
    private final Map<WatchKey, WatchedDirectory> directoryWatchKeys = new HashMap<>();
    private final Map<String, WatchKey> directoryWatchKeysByPath = new HashMap<>();
    private final Map<String, String> knownSignatures = new ConcurrentHashMap<>();
    private final Map<String, String> knownContents = new ConcurrentHashMap<>();
    private final Map<String, FileState> pendingStates = new HashMap<>();
    private final Map<String, Long> pendingSinceNanos = new HashMap<>();
    private final Map<String, StableContentSnapshot> queuedTouchedSnapshots = new HashMap<>();
    private final Set<String> signatureReconciliationSeenPaths = new HashSet<>();

    private WatchService directoryWatchService;
    private List<File> signatureReconciliationFiles = List.of();
    private int signatureReconciliationIndex;
    private int fullWatchScanEveryPolls = 1;
    private int fullWatchScanCountdown = 0;
    private int signatureScanEveryPolls = 1;
    private int signatureScanCountdown = 0;
    private boolean suppressDirectoryEventDelivery;
    private boolean signatureReconciliationInProgress;
    private long hotloadCooldownNanos = TimeUnit.MILLISECONDS.toNanos(DEFAULT_HOTLOAD_COOLDOWN_MS);
    private long lastTouchedEmissionNanos;
    private boolean emittedTouchedFiles;

    public ConfigHotloadEngine(Predicate<File> managedConfigFilePredicate,
                               Supplier<? extends Collection<File>> knownFilesSupplier,
                               Function<File, String> fileReader,
                               UnaryOperator<String> normalizer) {
        this(
                managedConfigFilePredicate,
                knownFilesSupplier,
                fileReader,
                normalizer,
                DEFAULT_FULL_WATCH_SCAN_WINDOW_MS,
                DEFAULT_SIGNATURE_SCAN_WINDOW_MS
        );
    }

    public ConfigHotloadEngine(Predicate<File> managedConfigFilePredicate,
                               Supplier<? extends Collection<File>> knownFilesSupplier,
                               Function<File, String> fileReader,
                               UnaryOperator<String> normalizer,
                               long fullWatchScanWindowMs,
                               long signatureScanWindowMs) {
        this.managedConfigFilePredicate = Objects.requireNonNull(managedConfigFilePredicate, "managedConfigFilePredicate");
        this.knownFilesSupplier = Objects.requireNonNull(knownFilesSupplier, "knownFilesSupplier");
        this.fileReader = Objects.requireNonNull(fileReader, "fileReader");
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
        this.fullWatchScanWindowMs = Math.max(100L, fullWatchScanWindowMs);
        this.signatureScanWindowMs = Math.max(100L, signatureScanWindowMs);
    }

    public void configure(long pollIntervalMs,
                          long hotloadCooldownMs,
                          Collection<File> watchedFiles,
                          Collection<File> watchedDirectories) {
        synchronized (watcherStateLock) {
            configureWatcherState(pollIntervalMs, hotloadCooldownMs, watchedFiles, watchedDirectories);
        }
    }

    public void clear() {
        synchronized (watcherStateLock) {
            clearWatcherState();
        }
    }

    public Set<File> pollTouchedFiles() {
        Set<StableContentSnapshot> snapshots = pollTouchedSnapshots();
        Set<File> files = new HashSet<>(snapshots.size());
        for (StableContentSnapshot snapshot : snapshots) {
            files.add(snapshot.file());
        }
        return files;
    }

    public Set<StableContentSnapshot> pollTouchedSnapshots() {
        synchronized (watcherStateLock) {
            return pollTouchedSnapshotsLocked();
        }
    }

    public void noteSelfWrite(File file, String rawContent) {
        if (file == null || !managedConfigFilePredicate.test(file)) {
            return;
        }

        synchronized (watcherStateLock) {
            updateKnownSnapshot(file, normalize(rawContent));
        }
    }

    public boolean processFileChange(File file,
                                     Function<File, Boolean> applyChange,
                                     Consumer<ContentDelta> onApplied) {
        if (file == null || !managedConfigFilePredicate.test(file)) {
            return false;
        }

        String now = readNormalizedContent(file);
        String appliedSignature = signature(file);
        StableContentSnapshot snapshot = new StableContentSnapshot(file, appliedSignature, now);
        return processSnapshotChange(snapshot, ignored -> applyChange.apply(file), onApplied);
    }

    public boolean processSnapshotChange(StableContentSnapshot snapshot,
                                         Function<StableContentSnapshot, Boolean> applyChange,
                                         Consumer<ContentDelta> onApplied) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(applyChange, "applyChange");
        File file = snapshot.file();
        if (!managedConfigFilePredicate.test(file)) {
            return false;
        }

        String path = file.getAbsolutePath();
        String before = knownContents.get(path);
        String now = snapshot.normalizedContent();
        if (Objects.equals(before, now)) {
            updateKnownSnapshot(file, snapshot.signature(), now);
            return false;
        }

        boolean applied;
        try {
            applied = Boolean.TRUE.equals(applyChange.apply(snapshot));
        } catch (RuntimeException failure) {
            FileState afterFailure = state(file);
            synchronized (watcherStateLock) {
                queueAfterSnapshotApplyLocked(snapshot, afterFailure, false);
                recordApplyCompletionLocked();
            }
            throw failure;
        }
        FileState after = state(file);
        synchronized (watcherStateLock) {
            if (applied) {
                updateKnownSnapshot(file, snapshot.signature(), now);
            } else {
                knownSignatures.put(path, snapshot.signature());
            }
            queueAfterSnapshotApplyLocked(snapshot, after, applied);
            recordApplyCompletionLocked();
        }
        if (!applied) {
            return false;
        }

        if (onApplied != null) {
            onApplied.accept(new ContentDelta(file, before, now));
        }

        return true;
    }

    void suppressDirectoryEventDelivery(boolean suppress) {
        synchronized (watcherStateLock) {
            suppressDirectoryEventDelivery = suppress;
        }
    }

    boolean isDirectoryEventWatchActive() {
        synchronized (watcherStateLock) {
            return directoryWatchService != null && !directoryWatchKeys.isEmpty();
        }
    }

    private void configureWatcherState(long pollIntervalMs,
                                       long hotloadCooldownMs,
                                       Collection<File> watchedFiles,
                                       Collection<File> watchedDirectories) {
        closeDirectoryWatchService();
        closeFileWatchers();
        closeFolderWatchers();
        fileWatchers.clear();
        directoryWatchers.clear();
        knownSignatures.clear();
        knownContents.clear();
        pendingStates.clear();
        pendingSinceNanos.clear();
        queuedTouchedSnapshots.clear();
        resetSignatureReconciliation();
        hotloadCooldownNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(100L, hotloadCooldownMs));
        lastTouchedEmissionNanos = 0L;
        emittedTouchedFiles = false;

        long effectivePollInterval = Math.max(100L, pollIntervalMs);
        fullWatchScanEveryPolls = cycleCountForWindow(effectivePollInterval, fullWatchScanWindowMs);
        signatureScanEveryPolls = cycleCountForWindow(effectivePollInterval, signatureScanWindowMs);
        fullWatchScanCountdown = 0;
        signatureScanCountdown = 0;

        if (watchedFiles != null) {
            for (File file : watchedFiles) {
                if (file == null) {
                    continue;
                }
                fileWatchers.add(new WatchedFile(file, new FileWatcher(file)));
            }
        }

        if (watchedDirectories != null) {
            for (File directory : watchedDirectories) {
                if (directory == null) {
                    continue;
                }
                directoryWatchers.add(new WatchedDirectory(directory, new FolderWatcher(directory)));
            }
        }

        initializeDirectoryWatchService();
        primeKnownSnapshots();
        if (directoryWatchService != null && !directoryWatchKeys.isEmpty()) {
            fullWatchScanCountdown = fullWatchScanEveryPolls;
            signatureScanCountdown = signatureScanEveryPolls;
        }
    }

    private void clearWatcherState() {
        closeDirectoryWatchService();
        closeFileWatchers();
        closeFolderWatchers();
        fileWatchers.clear();
        directoryWatchers.clear();
        knownSignatures.clear();
        knownContents.clear();
        pendingStates.clear();
        pendingSinceNanos.clear();
        queuedTouchedSnapshots.clear();
        resetSignatureReconciliation();
        fullWatchScanCountdown = 0;
        signatureScanCountdown = 0;
        lastTouchedEmissionNanos = 0L;
        emittedTouchedFiles = false;
    }

    private Set<StableContentSnapshot> pollTouchedSnapshotsLocked() {
        Set<File> touched = new HashSet<>();
        for (WatchedFile watchedFile : fileWatchers) {
            if (watchedFile.watcher().checkModified()) {
                touched.add(watchedFile.file());
            }
        }

        boolean reconciliationRequired = drainDirectoryEvents(touched);
        boolean fullWatchScan = reconciliationRequired || shouldRunFullWatchScan();
        if (fullWatchScan && directoryWatchService == null) {
            initializeDirectoryWatchService();
        }
        if (fullWatchScan && registerFallbackDirectoryWatchers()) {
            reconciliationRequired = true;
        }

        for (WatchedDirectory watchedDirectory : directoryWatchers) {
            FolderWatcher watcher = watchedDirectory.watcher();
            boolean changed = fullWatchScan ? watcher.checkModified() : watcher.checkModifiedEvents();
            if (!changed) {
                continue;
            }

            touched.addAll(watcher.getCreated());
            touched.addAll(watcher.getChanged());
            touched.addAll(watcher.getDeleted());
        }

        if (reconciliationRequired || shouldRunSignatureScan()) {
            startSignatureReconciliation();
        }
        if (signatureReconciliationInProgress) {
            touched.addAll(scanForMissedChanges());
        }

        touched.removeIf(file -> file == null || !managedConfigFilePredicate.test(file));
        Set<StableContentSnapshot> stable = emitStableTouchedSnapshots(touched);
        for (StableContentSnapshot snapshot : stable) {
            queuedTouchedSnapshots.put(snapshot.file().getAbsolutePath(), snapshot);
        }
        if (queuedTouchedSnapshots.isEmpty()) {
            return Set.of();
        }

        long now = System.nanoTime();
        if (emittedTouchedFiles && now - lastTouchedEmissionNanos < hotloadCooldownNanos) {
            return Set.of();
        }

        Set<StableContentSnapshot> emitted = new HashSet<>(queuedTouchedSnapshots.values());
        queuedTouchedSnapshots.clear();
        lastTouchedEmissionNanos = now;
        emittedTouchedFiles = true;
        return emitted;
    }

    private Set<StableContentSnapshot> emitStableTouchedSnapshots(Set<File> detected) {
        Map<String, File> candidates = new HashMap<>();
        for (File file : detected) {
            if (file != null) {
                candidates.put(file.getAbsolutePath(), file);
            }
        }
        for (String path : new HashSet<>(pendingStates.keySet())) {
            candidates.putIfAbsent(path, new File(path));
        }

        Set<StableContentSnapshot> stable = new HashSet<>();
        Map<String, FileState> stillPending = new HashMap<>();
        long now = System.nanoTime();
        for (Map.Entry<String, File> entry : candidates.entrySet()) {
            String path = entry.getKey();
            File file = entry.getValue();
            if (file == null || !managedConfigFilePredicate.test(file)) {
                continue;
            }

            FileState currentState = state(file);
            StableContentSnapshot queued = queuedTouchedSnapshots.get(path);
            if (queued != null && !queued.matches(currentState)) {
                queuedTouchedSnapshots.remove(path);
            }
            if (currentState.equals(pendingStates.get(path))) {
                long pendingSince = pendingSinceNanos.getOrDefault(path, now);
                if (currentState.missing() && now - pendingSince < hotloadCooldownNanos) {
                    stillPending.put(path, currentState);
                } else {
                    stable.add(new StableContentSnapshot(file, currentState.signature(), currentState.content()));
                    pendingSinceNanos.remove(path);
                }
            } else {
                stillPending.put(path, currentState);
                pendingSinceNanos.put(path, now);
            }
        }

        pendingStates.clear();
        pendingStates.putAll(stillPending);
        pendingSinceNanos.keySet().retainAll(stillPending.keySet());
        return stable;
    }

    private void primeKnownSnapshots() {
        for (File file : safeKnownFiles()) {
            if (file == null || !managedConfigFilePredicate.test(file)) {
                continue;
            }

            try {
                updateKnownSnapshot(file, readNormalizedContent(file));
            } catch (RuntimeException failure) {
                updateKnownSnapshot(file, signature(file), null);
            }
        }
    }

    private Set<File> scanForMissedChanges() {
        Set<File> changed = new HashSet<>();
        long startedAt = System.nanoTime();
        long bytes = 0L;
        int files = 0;
        while (signatureReconciliationIndex < signatureReconciliationFiles.size()
                && files < RECONCILIATION_FILE_BUDGET) {
            if (files > 0 && System.nanoTime() - startedAt >= RECONCILIATION_TIME_BUDGET_NANOS) {
                break;
            }
            File file = signatureReconciliationFiles.get(signatureReconciliationIndex);
            long size = reconciliationSize(file);
            if (files > 0 && bytes + size > RECONCILIATION_BYTE_BUDGET) {
                break;
            }
            signatureReconciliationIndex++;
            files++;
            bytes += size;
            if (file == null || !managedConfigFilePredicate.test(file)) {
                continue;
            }

            String path = file.getAbsolutePath();
            signatureReconciliationSeenPaths.add(path);
            String now = signature(file);
            String previous = knownSignatures.put(path, now);
            boolean contentChanged = false;
            try {
                String currentContent = readNormalizedContent(file);
                contentChanged = !Objects.equals(knownContents.get(path), currentContent);
            } catch (RuntimeException ignored) {
            }
            if (previous == null || !previous.equals(now) || contentChanged) {
                changed.add(file);
            }
        }

        if (signatureReconciliationIndex >= signatureReconciliationFiles.size()) {
            for (String path : new HashSet<>(knownSignatures.keySet())) {
                if (signatureReconciliationSeenPaths.contains(path)) {
                    continue;
                }

                String previous = knownSignatures.put(path, "missing");
                if (previous != null && !"missing".equals(previous)) {
                    changed.add(new File(path));
                }
            }
            resetSignatureReconciliation();
        }

        return changed;
    }

    private void startSignatureReconciliation() {
        if (signatureReconciliationInProgress) {
            return;
        }
        signatureReconciliationFiles = new ArrayList<>(safeKnownFiles());
        signatureReconciliationIndex = 0;
        signatureReconciliationSeenPaths.clear();
        signatureReconciliationInProgress = true;
    }

    private void resetSignatureReconciliation() {
        signatureReconciliationFiles = List.of();
        signatureReconciliationIndex = 0;
        signatureReconciliationSeenPaths.clear();
        signatureReconciliationInProgress = false;
    }

    private long reconciliationSize(File file) {
        if (file == null || !file.isFile()) {
            return 0L;
        }
        return Math.min(Math.max(0L, file.length()), MAX_RECONCILIATION_FILE_BYTES);
    }

    private void initializeDirectoryWatchService() {
        if (directoryWatchers.isEmpty()) {
            return;
        }

        try {
            directoryWatchService = FileSystems.getDefault().newWatchService();
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            directoryWatchService = null;
            return;
        }

        registerFallbackDirectoryWatchers();
    }

    private boolean registerFallbackDirectoryWatchers() {
        if (directoryWatchService == null) {
            return false;
        }

        boolean registered = false;
        for (WatchedDirectory watchedDirectory : directoryWatchers) {
            if (isDirectoryEventWatched(watchedDirectory) || !watchedDirectory.directory().isDirectory()) {
                continue;
            }

            try {
                WatchKey key = watchedDirectory.directory().toPath().register(
                        directoryWatchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE
                );
                directoryWatchKeys.put(key, watchedDirectory);
                directoryWatchKeysByPath.put(watchPath(watchedDirectory.directory()), key);
                registered = true;
            } catch (IOException | UnsupportedOperationException | SecurityException e) {
                directoryWatchKeysByPath.remove(watchPath(watchedDirectory.directory()));
            }
        }
        return registered;
    }

    private boolean drainDirectoryEvents(Set<File> touched) {
        if (directoryWatchService == null) {
            return false;
        }

        boolean reconciliationRequired = false;
        try {
            WatchKey key;
            while ((key = directoryWatchService.poll()) != null) {
                if (suppressDirectoryEventDelivery) {
                    key.pollEvents();
                    if (!key.reset()) {
                        WatchedDirectory removed = directoryWatchKeys.remove(key);
                        if (removed != null) {
                            directoryWatchKeysByPath.remove(watchPath(removed.directory()));
                        }
                        reconciliationRequired = true;
                    }
                    continue;
                }

                WatchedDirectory watchedDirectory = directoryWatchKeys.get(key);
                if (watchedDirectory == null) {
                    reconciliationRequired = true;
                } else {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                            reconciliationRequired = true;
                            continue;
                        }

                        Object context = event.context();
                        if (context instanceof Path relativePath) {
                            touched.add(watchedDirectory.directory().toPath().resolve(relativePath).toFile());
                        } else {
                            reconciliationRequired = true;
                        }
                    }
                }

                if (!key.reset()) {
                    WatchedDirectory removed = directoryWatchKeys.remove(key);
                    if (removed != null) {
                        directoryWatchKeysByPath.remove(watchPath(removed.directory()));
                    }
                    reconciliationRequired = true;
                }
            }
        } catch (ClosedWatchServiceException e) {
            closeDirectoryWatchService();
            reconciliationRequired = true;
        }

        return reconciliationRequired;
    }

    private boolean isDirectoryEventWatched(WatchedDirectory watchedDirectory) {
        return directoryWatchKeysByPath.containsKey(watchPath(watchedDirectory.directory()));
    }

    private String watchPath(File directory) {
        return directory.getAbsoluteFile().toPath().normalize().toString();
    }

    private void closeDirectoryWatchService() {
        WatchService service = directoryWatchService;
        directoryWatchService = null;
        directoryWatchKeys.clear();
        directoryWatchKeysByPath.clear();
        if (service == null) {
            return;
        }

        try {
            service.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Collection<File> safeKnownFiles() {
        Collection<File> files = knownFilesSupplier.get();
        return files == null ? List.of() : files;
    }

    private String signature(File file) {
        if (file == null || !file.exists()) {
            return "missing";
        }
        try {
            BasicFileAttributes attributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            return attributes.lastModifiedTime().toMillis() + ":" + attributes.size() + ":" + attributes.fileKey();
        } catch (IOException | SecurityException ignored) {
            return "unreadable";
        }
    }

    private FileState state(File file) {
        String currentSignature = signature(file);
        String currentContent;
        try {
            currentContent = readNormalizedContent(file);
        } catch (RuntimeException failure) {
            currentContent = null;
        }
        return new FileState(currentSignature, currentContent);
    }

    private String readNormalizedContent(File file) {
        if (file != null && file.isFile() && file.length() > MAX_RECONCILIATION_FILE_BYTES) {
            return null;
        }
        return normalize(fileReader.apply(file));
    }

    private void updateKnownSnapshot(File file, String normalizedContent) {
        if (file == null) {
            return;
        }

        updateKnownSnapshot(file, signature(file), normalizedContent);
    }

    private void updateKnownSnapshot(File file, String signature, String normalizedContent) {
        String path = file.getAbsolutePath();
        knownSignatures.put(path, signature);
        if (normalizedContent == null) {
            knownContents.remove(path);
        } else {
            knownContents.put(path, normalizedContent);
        }
    }

    private void queueAfterSnapshotApplyLocked(StableContentSnapshot snapshot, FileState after, boolean applied) {
        String path = snapshot.file().getAbsolutePath();
        if (snapshot.matches(after)) {
            if (!applied) {
                queuedTouchedSnapshots.put(path, snapshot);
            }
            return;
        }

        queuedTouchedSnapshots.remove(path);
        pendingStates.put(path, after);
        pendingSinceNanos.put(path, System.nanoTime());
    }

    private void recordApplyCompletionLocked() {
        lastTouchedEmissionNanos = System.nanoTime();
        emittedTouchedFiles = true;
    }

    private void closeFileWatchers() {
        for (WatchedFile watchedFile : fileWatchers) {
            watchedFile.watcher().close();
        }
    }

    private void closeFolderWatchers() {
        for (WatchedDirectory watchedDirectory : directoryWatchers) {
            watchedDirectory.watcher().close();
        }
    }

    private String normalize(String text) {
        return normalizer.apply(text);
    }

    private int cycleCountForWindow(long pollIntervalMs, long windowMs) {
        long safePoll = Math.max(100L, pollIntervalMs);
        long safeWindow = Math.max(safePoll, windowMs);
        return (int) Math.max(1L, safeWindow / safePoll);
    }

    private boolean shouldRunFullWatchScan() {
        if (fullWatchScanCountdown <= 0) {
            fullWatchScanCountdown = Math.max(1, fullWatchScanEveryPolls) - 1;
            return true;
        }

        fullWatchScanCountdown--;
        return false;
    }

    private boolean shouldRunSignatureScan() {
        if (signatureScanCountdown <= 0) {
            signatureScanCountdown = Math.max(1, signatureScanEveryPolls) - 1;
            return true;
        }

        signatureScanCountdown--;
        return false;
    }

    public static List<DiffEntry> computeStructuredDiff(String before,
                                                        String after,
                                                        Function<String, JsonElement> parser) {
        Map<String, String> left = flattenForDiff(before, parser);
        Map<String, String> right = flattenForDiff(after, parser);
        Set<String> keys = new HashSet<>(left.keySet());
        keys.addAll(right.keySet());

        List<String> ordered = new ArrayList<>(keys);
        ordered.sort(String::compareTo);

        List<DiffEntry> changes = new ArrayList<>();
        for (String key : ordered) {
            boolean inLeft = left.containsKey(key);
            boolean inRight = right.containsKey(key);
            String oldValue = inLeft ? left.get(key) : MISSING;
            String newValue = inRight ? right.get(key) : REMOVED;
            if (Objects.equals(oldValue, newValue)) {
                continue;
            }
            changes.add(new DiffEntry(key, oldValue, newValue));
        }

        return changes;
    }

    public static String compactValue(String value, int maxLength) {
        if (value == null) {
            return "null";
        }

        String compact = value.replace("\r", "\\r").replace("\n", "\\n");
        if (maxLength < 4 || compact.length() <= maxLength) {
            return compact;
        }

        return compact.substring(0, maxLength - 3) + "...";
    }

    private static Map<String, String> flattenForDiff(String raw,
                                                      Function<String, JsonElement> parser) {
        JsonElement element = parse(raw, parser);
        if (element == null) {
            Map<String, String> fallback = new HashMap<>();
            if (raw != null && !raw.isBlank()) {
                fallback.put("$", raw);
            }
            return fallback;
        }

        Map<String, String> out = new HashMap<>();
        flattenJson("$", element, out);
        return out;
    }

    private static JsonElement parse(String raw, Function<String, JsonElement> parser) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return parser.apply(raw);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void flattenJson(String path, JsonElement element, Map<String, String> out) {
        if (element == null || element.isJsonNull()) {
            out.put(path, "null");
            return;
        }

        if (element.isJsonPrimitive()) {
            out.put(path, element.toString());
            return;
        }

        if (element.isJsonArray()) {
            if (element.getAsJsonArray().size() == 0) {
                out.put(path, "[]");
                return;
            }

            for (int i = 0; i < element.getAsJsonArray().size(); i++) {
                flattenJson(path + "[" + i + "]", element.getAsJsonArray().get(i), out);
            }
            return;
        }

        JsonObject object = element.getAsJsonObject();
        if (object.entrySet().isEmpty()) {
            out.put(path, "{}");
            return;
        }

        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            flattenJson(path + "." + entry.getKey(), entry.getValue(), out);
        }
    }

    private record WatchedFile(File file, FileWatcher watcher) {
    }

    private record WatchedDirectory(File directory, FolderWatcher watcher) {
    }

    private record FileState(String signature, String content) {
        private boolean missing() {
            return "missing".equals(signature);
        }
    }

    public record StableContentSnapshot(File file, String signature, String normalizedContent) {
        public StableContentSnapshot {
            file = Objects.requireNonNull(file, "file").getAbsoluteFile();
            signature = Objects.requireNonNull(signature, "signature");
        }

        private boolean matches(FileState state) {
            return signature.equals(state.signature()) && Objects.equals(normalizedContent, state.content());
        }
    }

    public record ContentDelta(File file, String before, String after) {
    }

    public record DiffEntry(String key, String oldValue, String newValue) {
    }
}

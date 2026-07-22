package art.arcane.volmlib.util.hotload;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import art.arcane.volmlib.util.io.FileWatcher;
import art.arcane.volmlib.util.io.FolderWatcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

    private WatchService directoryWatchService;
    private int fullWatchScanEveryPolls = 1;
    private int fullWatchScanCountdown = 0;
    private int signatureScanEveryPolls = 1;
    private int signatureScanCountdown = 0;

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

    public void configure(long pollIntervalMs, Collection<File> watchedFiles, Collection<File> watchedDirectories) {
        synchronized (watcherStateLock) {
            configureWatcherState(pollIntervalMs, watchedFiles, watchedDirectories);
        }
    }

    public void clear() {
        synchronized (watcherStateLock) {
            clearWatcherState();
        }
    }

    public Set<File> pollTouchedFiles() {
        synchronized (watcherStateLock) {
            return pollTouchedFilesLocked();
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

        String path = file.getAbsolutePath();
        String before = knownContents.get(path);
        String now = normalize(fileReader.apply(file));

        if (Objects.equals(before, now)) {
            updateKnownSnapshot(file, now);
            return false;
        }

        boolean applied = Boolean.TRUE.equals(applyChange.apply(file));
        String after = normalize(fileReader.apply(file));
        updateKnownSnapshot(file, after);
        if (applied && onApplied != null) {
            onApplied.accept(new ContentDelta(file, before, after));
        }

        return applied;
    }

    boolean isDirectoryEventWatchActive() {
        synchronized (watcherStateLock) {
            return directoryWatchService != null && !directoryWatchKeys.isEmpty();
        }
    }

    private void configureWatcherState(long pollIntervalMs,
                                       Collection<File> watchedFiles,
                                       Collection<File> watchedDirectories) {
        closeDirectoryWatchService();
        fileWatchers.clear();
        directoryWatchers.clear();
        knownSignatures.clear();
        knownContents.clear();

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
    }

    private void clearWatcherState() {
        closeDirectoryWatchService();
        fileWatchers.clear();
        directoryWatchers.clear();
        knownSignatures.clear();
        knownContents.clear();
        fullWatchScanCountdown = 0;
        signatureScanCountdown = 0;
    }

    private Set<File> pollTouchedFilesLocked() {
        Set<File> touched = new HashSet<>();
        for (WatchedFile watchedFile : fileWatchers) {
            if (watchedFile.watcher().checkModified()) {
                touched.add(watchedFile.file());
            }
        }

        boolean reconciliationRequired = drainDirectoryEvents(touched);
        boolean fallbackRequired = hasFallbackDirectoryWatchers();
        boolean fullWatchScan = reconciliationRequired || (fallbackRequired && shouldRunFullWatchScan());
        if (fullWatchScan && registerFallbackDirectoryWatchers()) {
            reconciliationRequired = true;
        }

        if (fallbackRequired || reconciliationRequired) {
            for (WatchedDirectory watchedDirectory : directoryWatchers) {
                if (!reconciliationRequired && isDirectoryEventWatched(watchedDirectory)) {
                    continue;
                }

                FolderWatcher watcher = watchedDirectory.watcher();
                boolean changed = fullWatchScan ? watcher.checkModified() : watcher.checkModifiedFast();
                if (!changed) {
                    continue;
                }

                touched.addAll(watcher.getCreated());
                touched.addAll(watcher.getChanged());
                touched.addAll(watcher.getDeleted());
            }
        }

        boolean signatureFallbackRequired = fallbackRequired && !isDirectoryEventWatchActive();
        if (reconciliationRequired || (signatureFallbackRequired && shouldRunSignatureScan())) {
            touched.addAll(scanForMissedChanges());
        }

        touched.removeIf(file -> file == null || !managedConfigFilePredicate.test(file));
        return touched;
    }

    private void primeKnownSnapshots() {
        for (File file : safeKnownFiles()) {
            if (file == null || !managedConfigFilePredicate.test(file)) {
                continue;
            }

            updateKnownSnapshot(file, normalize(fileReader.apply(file)));
        }
    }

    private Set<File> scanForMissedChanges() {
        Set<File> changed = new HashSet<>();
        Set<String> seenPaths = new HashSet<>();
        for (File file : safeKnownFiles()) {
            if (file == null || !managedConfigFilePredicate.test(file)) {
                continue;
            }

            String path = file.getAbsolutePath();
            seenPaths.add(path);
            String now = signature(file);
            String previous = knownSignatures.put(path, now);
            if (previous == null || !previous.equals(now)) {
                changed.add(file);
            }
        }

        for (String path : new HashSet<>(knownSignatures.keySet())) {
            if (seenPaths.contains(path)) {
                continue;
            }

            String previous = knownSignatures.put(path, "missing");
            if (previous != null && !"missing".equals(previous)) {
                changed.add(new File(path));
            }
        }

        return changed;
    }

    private void initializeDirectoryWatchService() {
        if (directoryWatchers.isEmpty()) {
            return;
        }

        try {
            directoryWatchService = FileSystems.getDefault().newWatchService();
        } catch (IOException | UnsupportedOperationException e) {
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
            } catch (IOException | UnsupportedOperationException e) {
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

    private boolean hasFallbackDirectoryWatchers() {
        for (WatchedDirectory watchedDirectory : directoryWatchers) {
            if (!isDirectoryEventWatched(watchedDirectory)) {
                return true;
            }
        }
        return false;
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

        return file.lastModified() + ":" + file.length();
    }

    private void updateKnownSnapshot(File file, String normalizedContent) {
        if (file == null) {
            return;
        }

        String path = file.getAbsolutePath();
        knownSignatures.put(path, signature(file));
        if (normalizedContent == null) {
            knownContents.remove(path);
        } else {
            knownContents.put(path, normalizedContent);
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

    public record ContentDelta(File file, String before, String after) {
    }

    public record DiffEntry(String key, String oldValue, String newValue) {
    }
}

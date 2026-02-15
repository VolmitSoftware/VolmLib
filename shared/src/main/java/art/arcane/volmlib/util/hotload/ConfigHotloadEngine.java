package art.arcane.volmlib.util.hotload;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import art.arcane.volmlib.util.io.FileWatcher;
import art.arcane.volmlib.util.io.FolderWatcher;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    private final List<WatchedFile> fileWatchers = new ArrayList<>();
    private final List<FolderWatcher> directoryWatchers = new ArrayList<>();
    private final Map<String, String> knownSignatures = new HashMap<>();
    private final Map<String, String> knownContents = new HashMap<>();

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
                directoryWatchers.add(new FolderWatcher(directory));
            }
        }

        primeKnownSnapshots();
    }

    public void clear() {
        fileWatchers.clear();
        directoryWatchers.clear();
        knownSignatures.clear();
        knownContents.clear();
        fullWatchScanCountdown = 0;
        signatureScanCountdown = 0;
    }

    public Set<File> pollTouchedFiles() {
        Set<File> touched = new HashSet<>();
        for (WatchedFile watchedFile : fileWatchers) {
            if (watchedFile.watcher().checkModified()) {
                touched.add(watchedFile.file());
            }
        }

        boolean fullWatchScan = shouldRunFullWatchScan();
        for (FolderWatcher watcher : directoryWatchers) {
            boolean changed = fullWatchScan ? watcher.checkModified() : watcher.checkModifiedFast();
            if (!changed) {
                continue;
            }

            touched.addAll(watcher.getCreated());
            touched.addAll(watcher.getChanged());
            touched.addAll(watcher.getDeleted());
        }

        if (shouldRunSignatureScan()) {
            touched.addAll(scanForMissedChanges());
        }

        touched.removeIf(file -> file == null || !managedConfigFilePredicate.test(file));
        return touched;
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
            if (previous != null && !previous.equals(now)) {
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

    public record ContentDelta(File file, String before, String after) {
    }

    public record DiffEntry(String key, String oldValue, String newValue) {
    }
}

package art.arcane.volmlib.util.io;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.function.Consumer3;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

public class ReactiveFolder {
    public static final long HOTLOAD_COOLDOWN_MILLIS = 3_000L;
    public static final long STABILITY_WINDOW_MILLIS = 250L;
    static final long FULL_SCAN_INTERVAL_MILLIS = 5_000L;
    static final long CONTENT_RECONCILIATION_INTERVAL_MILLIS = 2_500L;
    private static final long RECONCILIATION_BYTE_BUDGET = 8L * 1024L * 1024L;
    private static final long RECONCILIATION_TIME_BUDGET_NANOS = TimeUnit.MILLISECONDS.toNanos(10L);
    static final int RECONCILIATION_FILE_BUDGET = 32;
    private static final int RECONCILIATION_PATH_BUDGET = 1024;
    private static final int RECONCILIATION_BUFFER_BYTES = 8192;

    private final File folder;
    private final Consumer3<KList<File>, KList<File>, KList<File>> hotload;
    private final KList<String> watchedExtensions;
    private final KList<String> ignoredPathContains;
    private final KList<String> ignoredNameSuffixes;
    private final LongSupplier clock;
    private final Set<File> pendingCreated = new LinkedHashSet<>();
    private final Set<File> pendingChanged = new LinkedHashSet<>();
    private final Set<File> pendingDeleted = new LinkedHashSet<>();
    private final Map<File, FileState> pendingStates = new HashMap<>();
    private final Map<File, FileState> appliedStates = new HashMap<>();
    private final Map<File, FileState> reconciledStates = new HashMap<>();
    private final Set<File> reconciliationPriority = new LinkedHashSet<>();
    private final Set<File> reconciliationCycleSeen = new HashSet<>();
    private final Deque<Path> reconciliationDirectories = new ArrayDeque<>();
    private final byte[] reconciliationBuffer = new byte[RECONCILIATION_BUFFER_BYTES];
    private FolderWatcher fw;
    private DirectoryStream<Path> reconciliationDirectoryStream;
    private Iterator<Path> reconciliationDirectoryIterator;
    private DigestProgress reconciliationDigest;
    private long nextFullScanAtNanos;
    private long nextReconciliationAtNanos;
    private long lastDetectedAtNanos;
    private long lastDeletionDetectedAtNanos;
    private long lastHotloadAtNanos;
    private boolean hotloaded;
    private boolean cleared;
    private boolean reconciliationPrimed;
    private boolean reconciliationCycleActive;
    private boolean reconciliationBatchPending;

    public ReactiveFolder(File folder,
                          Consumer3<KList<File>, KList<File>, KList<File>> hotload,
                          KList<String> watchedExtensions,
                          KList<String> ignoredPathContains,
                          KList<String> ignoredNameSuffixes) {
        this(folder, hotload, watchedExtensions, ignoredPathContains, ignoredNameSuffixes, System::nanoTime);
    }

    ReactiveFolder(File folder,
                   Consumer3<KList<File>, KList<File>, KList<File>> hotload,
                   KList<String> watchedExtensions,
                   KList<String> ignoredPathContains,
                   KList<String> ignoredNameSuffixes,
                   LongSupplier clock) {
        this.folder = folder;
        this.hotload = hotload;
        this.watchedExtensions = watchedExtensions;
        this.ignoredPathContains = ignoredPathContains;
        this.ignoredNameSuffixes = ignoredNameSuffixes;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.fw = new FolderWatcher(folder);
        fw.checkModified();
        resetMaintenanceDeadlines(this.clock.getAsLong());
    }

    public synchronized void checkIgnore() {
        fw.clear();
        fw = new FolderWatcher(folder);
        pendingCreated.clear();
        pendingChanged.clear();
        pendingDeleted.clear();
        pendingStates.clear();
        appliedStates.clear();
        reconciledStates.clear();
        reconciliationPriority.clear();
        reconciliationCycleSeen.clear();
        closeReconciliationCursor();
        reconciliationDigest = null;
        reconciliationPrimed = false;
        reconciliationCycleActive = false;
        reconciliationBatchPending = false;
        lastDetectedAtNanos = 0L;
        lastDeletionDetectedAtNanos = 0L;
        lastHotloadAtNanos = 0L;
        hotloaded = false;
        cleared = false;
        resetMaintenanceDeadlines(clock.getAsLong());
    }

    public synchronized boolean check() {
        if (cleared) {
            return false;
        }

        long now = clock.getAsLong();
        boolean fullScan = now >= nextFullScanAtNanos;
        boolean detected = fullScan ? fw.checkModified() : fw.checkModifiedEvents();
        if (fullScan) {
            nextFullScanAtNanos = saturatingAdd(
                    clock.getAsLong(),
                    TimeUnit.MILLISECONDS.toNanos(FULL_SCAN_INTERVAL_MILLIS)
            );
        }
        if (detected) {
            boolean deleted = queueDeleted(fw.getDeleted());
            boolean queued = deleted;
            queued = queueMatches(fw.getChanged(), pendingChanged) || queued;
            queued = queueCreated(fw.getCreated()) || queued;
            if (queued) {
                lastDetectedAtNanos = now;
            }
            if (deleted && !pendingDeleted.isEmpty()) {
                lastDeletionDetectedAtNanos = lastDetectedAtNanos;
            }
        }
        boolean reconciliationDue = now >= nextReconciliationAtNanos;
        boolean reconciliationPending = reconciliationCycleActive
                || reconciliationDigest != null
                || !reconciliationPriority.isEmpty();
        if (!reconciliationPrimed || reconciliationDue || reconciliationPending) {
            boolean allowFullCycle = !reconciliationPrimed || reconciliationDue || reconciliationCycleActive;
            if (reconcileContent(allowFullCycle)) {
                lastDetectedAtNanos = now;
                if (reconciliationCycleActive || reconciliationDigest != null) {
                    reconciliationBatchPending = true;
                }
            }
        }

        if (reconciliationBatchPending) {
            if (reconciliationCycleActive || reconciliationDigest != null) {
                return false;
            }
            reconciliationBatchPending = false;
        }

        if (pendingCreated.isEmpty() && pendingChanged.isEmpty() && pendingDeleted.isEmpty()) {
            return false;
        }

        if (!pendingStatesStable(now)) {
            return false;
        }
        discardAppliedDuplicates();
        if (pendingCreated.isEmpty() && pendingChanged.isEmpty() && pendingDeleted.isEmpty()) {
            return false;
        }
        if (now - lastDetectedAtNanos < TimeUnit.MILLISECONDS.toNanos(STABILITY_WINDOW_MILLIS)) {
            return false;
        }
        if (!pendingDeleted.isEmpty()
                && now - lastDeletionDetectedAtNanos < TimeUnit.MILLISECONDS.toNanos(HOTLOAD_COOLDOWN_MILLIS)) {
            return false;
        }
        if (hotloaded && now - lastHotloadAtNanos < TimeUnit.MILLISECONDS.toNanos(HOTLOAD_COOLDOWN_MILLIS)) {
            return false;
        }

        KList<File> created = new KList<>(pendingCreated);
        KList<File> changed = new KList<>(pendingChanged);
        KList<File> deleted = new KList<>(pendingDeleted);
        Map<File, FileState> emittedStates = emittedStates(created, changed, deleted);
        hotloaded = true;
        try {
            hotload.accept(created, changed, deleted);
            acknowledge(created, changed, deleted, emittedStates);
            return true;
        } finally {
            lastHotloadAtNanos = clock.getAsLong();
        }
    }

    private boolean queueMatches(KList<File> files, Set<File> target) {
        boolean queued = false;
        for (File file : files) {
            if (isIgnored(file)) {
                continue;
            }

            if (isWatched(file)) {
                File normalized = file.getAbsoluteFile();
                pendingDeleted.remove(normalized);
                if (!pendingCreated.contains(normalized)) {
                    queued = record(normalized, target) || queued;
                }
            }
        }
        return queued;
    }

    private boolean queueCreated(KList<File> files) {
        boolean queued = false;
        for (File file : files) {
            if (isIgnored(file) || !isWatched(file)) {
                continue;
            }
            File normalized = file.getAbsoluteFile();
            pendingDeleted.remove(normalized);
            pendingChanged.remove(normalized);
            queued = record(normalized, pendingCreated) || queued;
        }
        return queued;
    }

    private boolean queueDeleted(KList<File> files) {
        boolean queued = false;
        for (File file : files) {
            if (isIgnored(file) || !isWatched(file)) {
                continue;
            }
            File normalized = file.getAbsoluteFile();
            if (normalized.exists()) {
                pendingDeleted.remove(normalized);
                if (!pendingCreated.contains(normalized)) {
                    queued = record(normalized, pendingChanged) || queued;
                }
                continue;
            }
            pendingCreated.remove(normalized);
            pendingChanged.remove(normalized);
            queued = record(normalized, pendingDeleted) || queued;
        }
        return queued;
    }

    private boolean isWatched(File file) {
        String name = file.getName();

        for (String extension : watchedExtensions) {
            if (name.endsWith(extension)) {
                return true;
            }
        }

        return false;
    }

    private boolean isIgnored(File file) {
        String path = file.getPath();
        String name = file.getName();
        String lowerName = name.toLowerCase(Locale.ROOT);

        if (lowerName.startsWith(".")
                || lowerName.startsWith("~")
                || lowerName.startsWith("#")
                || lowerName.endsWith("~")
                || lowerName.endsWith(".tmp")
                || lowerName.endsWith(".temp")
                || lowerName.endsWith(".part")
                || lowerName.endsWith(".swp")
                || lowerName.endsWith(".swx")
                || lowerName.endsWith(".bak")
                || lowerName.contains(".tmp.")
                || lowerName.contains(".temp.")) {
            return true;
        }

        for (String ignored : ignoredPathContains) {
            if (path.contains(ignored)) {
                return true;
            }
        }

        for (String ignoredSuffix : ignoredNameSuffixes) {
            if (name.endsWith(ignoredSuffix)) {
                return true;
            }
        }

        return false;
    }

    public synchronized void clear() {
        if (cleared) {
            return;
        }
        cleared = true;
        fw.clear();
        pendingCreated.clear();
        pendingChanged.clear();
        pendingDeleted.clear();
        pendingStates.clear();
        appliedStates.clear();
        reconciledStates.clear();
        reconciliationPriority.clear();
        reconciliationCycleSeen.clear();
        closeReconciliationCursor();
        reconciliationDigest = null;
        reconciliationPrimed = false;
        reconciliationCycleActive = false;
        reconciliationBatchPending = false;
    }

    private boolean record(File file, Set<File> target) {
        FileState current = state(file);
        if (!current.missing()) {
            reconciliationPriority.add(file);
        }
        FileState previous = pendingStates.get(file);
        FileState pending = previous != null
                && previous.contentVerified()
                && current.sameAttributes(previous)
                ? previous
                : current;
        pendingStates.put(file, pending);
        return target.add(file) || !pending.equals(previous);
    }

    private boolean reconcileContent(boolean allowFullCycle) {
        boolean queued = false;
        long startedAt = System.nanoTime();
        long bytes = 0L;
        int files = 0;
        while (bytes < RECONCILIATION_BYTE_BUDGET && files < RECONCILIATION_FILE_BUDGET) {
            if (files > 0 && System.nanoTime() - startedAt >= RECONCILIATION_TIME_BUDGET_NANOS) {
                break;
            }
            if (reconciliationDigest == null) {
                File file = nextReconciliationFile(allowFullCycle);
                if (file == null) {
                    break;
                }
                reconciliationDigest = beginDigest(file);
                files++;
                if (reconciliationDigest == null) {
                    continue;
                }
            }

            long remaining = RECONCILIATION_BYTE_BUDGET - bytes;
            DigestAdvance advance = advanceDigest(reconciliationDigest, remaining);
            bytes += advance.bytesRead();
            if (advance.state() != null) {
                queued = queueReconciledState(reconciliationDigest.file, advance.state()) || queued;
            }
            reconciliationDigest = advance.progress();
            if (advance.bytesRead() == 0L && reconciliationDigest != null) {
                break;
            }
        }
        return queued;
    }

    private File nextReconciliationFile(boolean allowFullCycle) {
        while (!reconciliationPriority.isEmpty()) {
            Iterator<File> iterator = reconciliationPriority.iterator();
            File prioritized = iterator.next();
            iterator.remove();
            if (prioritized.isFile() && !isIgnored(prioritized) && isWatched(prioritized)) {
                reconciliationCycleSeen.add(prioritized);
                return prioritized;
            }
        }

        if (reconciliationDirectories.isEmpty() && reconciliationDirectoryIterator == null) {
            if (reconciliationCycleActive) {
                reconciliationCycleActive = false;
                reconciliationPrimed = true;
                reconciliationCycleSeen.clear();
                return null;
            }
            if (!allowFullCycle) {
                return null;
            }
            if (!folder.isDirectory()) {
                reconciliationPrimed = true;
                reconciliationCycleSeen.clear();
                scheduleNextContentReconciliation();
                return null;
            }
            reconciliationCycleActive = true;
            reconciliationCycleSeen.clear();
            reconciliationDirectories.add(folder.toPath().toAbsolutePath().normalize());
        }

        int scanned = 0;
        while (scanned < RECONCILIATION_PATH_BUDGET) {
            if (reconciliationDirectoryIterator == null) {
                if (reconciliationDirectories.isEmpty()) {
                    reconciliationCycleActive = false;
                    reconciliationPrimed = true;
                    reconciliationCycleSeen.clear();
                    scheduleNextContentReconciliation();
                    return null;
                }
                Path directory = reconciliationDirectories.removeFirst();
                try {
                    reconciliationDirectoryStream = Files.newDirectoryStream(directory);
                    reconciliationDirectoryIterator = reconciliationDirectoryStream.iterator();
                } catch (IOException | SecurityException failure) {
                    closeReconciliationDirectoryStream();
                    continue;
                }
            }

            boolean available;
            try {
                available = reconciliationDirectoryIterator.hasNext();
            } catch (RuntimeException failure) {
                closeReconciliationDirectoryStream();
                continue;
            }
            if (!available) {
                closeReconciliationDirectoryStream();
                continue;
            }

            Path path;
            try {
                path = reconciliationDirectoryIterator.next();
            } catch (RuntimeException failure) {
                closeReconciliationDirectoryStream();
                continue;
            }
            scanned++;
            if (Files.isSymbolicLink(path)) {
                continue;
            }
            File candidate = path.toFile().getAbsoluteFile();
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                if (!isIgnored(candidate)) {
                    reconciliationDirectories.addLast(path);
                }
                continue;
            }
            if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    && !isIgnored(candidate)
                    && isWatched(candidate)
                    && reconciliationCycleSeen.add(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private DigestProgress beginDigest(File file) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    file.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile()) {
                return null;
            }
            return new DigestProgress(
                    file,
                    attributesSignature(attributes),
                    attributes.size(),
                    0L,
                    MessageDigest.getInstance("SHA-256")
            );
        } catch (IOException | SecurityException failure) {
            return null;
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private DigestAdvance advanceDigest(DigestProgress progress, long byteBudget) {
        if (byteBudget <= 0L) {
            return new DigestAdvance(progress, null, 0L);
        }
        try {
            BasicFileAttributes before = Files.readAttributes(
                    progress.file.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            String beforeSignature = attributesSignature(before);
            if (!before.isRegularFile() || !beforeSignature.equals(progress.signature)) {
                return new DigestAdvance(null, null, 0L);
            }

            long consumed = 0L;
            long offset = progress.offset;
            try (FileChannel channel = FileChannel.open(progress.file.toPath(), StandardOpenOption.READ)) {
                channel.position(offset);
                while (consumed < byteBudget && offset < progress.size) {
                    int limit = (int) Math.min(reconciliationBuffer.length,
                            Math.min(byteBudget - consumed, progress.size - offset));
                    ByteBuffer buffer = ByteBuffer.wrap(reconciliationBuffer, 0, limit);
                    int read = channel.read(buffer);
                    if (read <= 0) {
                        break;
                    }
                    progress.digest.update(reconciliationBuffer, 0, read);
                    consumed += read;
                    offset += read;
                }
            }

            BasicFileAttributes after = Files.readAttributes(
                    progress.file.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            String afterSignature = attributesSignature(after);
            if (!after.isRegularFile() || !afterSignature.equals(progress.signature)) {
                return new DigestAdvance(null, null, consumed);
            }
            if (offset < progress.size) {
                return new DigestAdvance(
                        new DigestProgress(progress.file, progress.signature, progress.size, offset, progress.digest),
                        null,
                        consumed
                );
            }

            FileState completed = new FileState(
                    progress.signature + ":" + HexFormat.of().formatHex(progress.digest.digest()),
                    progress.signature,
                    true
            );
            return new DigestAdvance(null, completed, consumed);
        } catch (IOException | SecurityException failure) {
            return new DigestAdvance(null, null, 0L);
        }
    }

    private boolean queueReconciledState(File file, FileState current) {
        FileState pendingBefore = pendingStates.get(file);
        FileState previous = reconciledStates.put(file, current);
        boolean alreadyPending = pendingCreated.contains(file)
                || pendingChanged.contains(file)
                || pendingDeleted.contains(file);
        if (current.missing()) {
            return false;
        }
        if (previous == null && !reconciliationPrimed && !alreadyPending) {
            appliedStates.put(file, current);
            return false;
        }
        if (previous != null && previous.equals(current)) {
            if (alreadyPending && current.equals(appliedStates.get(file))) {
                pendingCreated.remove(file);
                pendingChanged.remove(file);
                pendingDeleted.remove(file);
                pendingStates.remove(file);
            } else if (alreadyPending) {
                pendingStates.put(file, current);
            }
            return false;
        }
        pendingDeleted.remove(file);
        if (!pendingCreated.contains(file)) {
            pendingChanged.add(file);
        }
        pendingStates.put(file, current);
        return !alreadyPending
                || pendingBefore == null
                || !current.sameAttributes(pendingBefore);
    }

    private String attributesSignature(BasicFileAttributes attributes) {
        return attributes.lastModifiedTime().toMillis() + ":" + attributes.size() + ":" + attributes.fileKey();
    }

    private void closeReconciliationCursor() {
        closeReconciliationDirectoryStream();
        reconciliationDirectories.clear();
    }

    private void closeReconciliationDirectoryStream() {
        reconciliationDirectoryIterator = null;
        DirectoryStream<Path> stream = reconciliationDirectoryStream;
        reconciliationDirectoryStream = null;
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (IOException ignored) {
        }
    }

    private boolean pendingStatesStable(long now) {
        boolean stable = true;
        for (File file : pendingFiles()) {
            FileState current = state(file);
            FileState previous = pendingStates.get(file);
            if (previous == null || !current.sameAttributes(previous)) {
                pendingStates.put(file, current);
                if (!current.missing()) {
                    reconciliationPriority.add(file);
                }
                stable = false;
            } else if (!previous.contentVerified() && !previous.missing()) {
                stable = false;
            }
        }
        if (!stable) {
            lastDetectedAtNanos = now;
        }
        return stable;
    }

    private void discardAppliedDuplicates() {
        for (File file : pendingFiles()) {
            FileState pending = pendingStates.get(file);
            if (!pending.equals(appliedStates.get(file))) {
                continue;
            }
            pendingCreated.remove(file);
            pendingChanged.remove(file);
            pendingDeleted.remove(file);
            pendingStates.remove(file);
        }
    }

    private Map<File, FileState> emittedStates(KList<File> created, KList<File> changed, KList<File> deleted) {
        Map<File, FileState> emitted = new HashMap<>();
        for (File file : created) {
            emitted.put(file, pendingStates.get(file));
        }
        for (File file : changed) {
            emitted.put(file, pendingStates.get(file));
        }
        for (File file : deleted) {
            emitted.put(file, pendingStates.get(file));
        }
        return emitted;
    }

    private void acknowledge(KList<File> created,
                             KList<File> changed,
                             KList<File> deleted,
                             Map<File, FileState> emittedStates) {
        long now = clock.getAsLong();
        Set<File> emittedFiles = new HashSet<>(created);
        emittedFiles.addAll(changed);
        emittedFiles.addAll(deleted);
        for (File file : emittedFiles) {
            FileState emitted = emittedStates.get(file);
            FileState current = state(file);
            appliedStates.put(file, emitted);
            if (emitted != null && emitted.contentVerified()) {
                reconciledStates.put(file, emitted);
            }
            pendingCreated.remove(file);
            pendingChanged.remove(file);
            pendingDeleted.remove(file);
            if (current.sameAttributes(emitted)) {
                pendingStates.remove(file);
                continue;
            }
            pendingStates.put(file, current);
            if (current.missing()) {
                pendingDeleted.add(file);
                lastDeletionDetectedAtNanos = now;
            } else {
                pendingChanged.add(file);
                reconciliationPriority.add(file);
            }
            lastDetectedAtNanos = now;
        }
    }

    private Set<File> pendingFiles() {
        Set<File> files = new LinkedHashSet<>(pendingCreated);
        files.addAll(pendingChanged);
        files.addAll(pendingDeleted);
        return files;
    }

    private FileState state(File file) {
        if (file == null || !file.isFile()) {
            return FileState.missingState();
        }
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    file.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile()) {
                return FileState.missingState();
            }
            String signature = attributesSignature(attributes);
            return new FileState(signature, signature, false);
        } catch (IOException | SecurityException failure) {
            return new FileState("unreadable", "unreadable", false);
        }
    }

    private void resetMaintenanceDeadlines(long now) {
        nextFullScanAtNanos = now;
        nextReconciliationAtNanos = now;
    }

    private void scheduleNextContentReconciliation() {
        nextReconciliationAtNanos = saturatingAdd(
                clock.getAsLong(),
                TimeUnit.MILLISECONDS.toNanos(CONTENT_RECONCILIATION_INTERVAL_MILLIS)
        );
    }

    private long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private record FileState(String identity, String attributes, boolean contentVerified) {
        private static FileState missingState() {
            return new FileState("missing", "missing", true);
        }

        private boolean missing() {
            return "missing".equals(identity);
        }

        private boolean sameAttributes(FileState other) {
            return other != null && attributes.equals(other.attributes);
        }
    }

    private static final class DigestProgress {
        private final File file;
        private final String signature;
        private final long size;
        private final long offset;
        private final MessageDigest digest;

        private DigestProgress(File file, String signature, long size, long offset, MessageDigest digest) {
            this.file = file;
            this.signature = signature;
            this.size = size;
            this.offset = offset;
            this.digest = digest;
        }
    }

    private record DigestAdvance(DigestProgress progress, FileState state, long bytesRead) {
    }
}

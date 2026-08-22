package art.arcane.volmlib.util.io;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;

import java.io.File;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class FolderWatcher extends FileWatcher {
    private final boolean rootWatcher;
    private final Set<Path> ancestors;
    private final Map<WatchKey, Path> watchedDirectoryPaths = new HashMap<>();
    private final Map<Path, WatchKey> watchedDirectoryKeys = new HashMap<>();
    private KMap<File, FolderWatcher> watchers;
    private KList<File> changed;
    private KList<File> created;
    private KList<File> deleted;
    private KList<File> pendingCreated;
    private KList<File> pendingDeleted;
    private boolean deltaTracking;
    private WatchService treeWatchService;
    private boolean nativeRegistrationComplete;
    private boolean cleared;

    public FolderWatcher(File file) {
        this(file, true, Set.of());
    }

    private FolderWatcher(File file, boolean rootWatcher, Set<Path> parentAncestors) {
        super(file, false, false);
        this.rootWatcher = rootWatcher;
        Set<Path> lineage = new HashSet<>(parentAncestors);
        lineage.add(directoryIdentity(file));
        ancestors = Set.copyOf(lineage);
        readProperties();
        deltaTracking = true;
        if (rootWatcher) {
            initializeTreeWatchService();
        }
    }

    @Override
    protected void readProperties() {
        if (watchers == null) {
            watchers = new KMap<>();
            changed = new KList<>();
            created = new KList<>();
            deleted = new KList<>();
            pendingCreated = new KList<>();
            pendingDeleted = new KList<>();
        }

        if (!file.isDirectory()) {
            super.readProperties();
            return;
        }

        File[] files = file.listFiles();
        if (files != null) {
            for (File child : files) {
                if (Files.isSymbolicLink(child.toPath())) {
                    continue;
                }
                if (child.isDirectory() && child.getName().startsWith(".")) {
                    continue;
                }
                if (child.isDirectory() && ancestors.contains(directoryIdentity(child))) {
                    continue;
                }
                if (watchers.containsKey(child)) {
                    continue;
                }

                FolderWatcher watcher = new FolderWatcher(child, false, ancestors);
                watchers.put(child, watcher);
                if (deltaTracking) {
                    pendingCreated.add(child);
                    watcher.collectKnownFiles(pendingCreated);
                }
            }
        }

        watchers.values().removeIf(watcher -> {
            if (!watcher.wasDeleted()) {
                return false;
            }
            if (deltaTracking) {
                pendingDeleted.add(watcher.file);
                watcher.collectKnownFiles(pendingDeleted);
            }
            watcher.close();
            return true;
        });
    }

    @Override
    public boolean checkModified() {
        return checkModified(true, false);
    }

    public boolean checkModifiedFast() {
        return checkModified(false, false);
    }

    public boolean checkModifiedEvents() {
        return checkModified(false, true);
    }

    private boolean checkModified(boolean fullScan, boolean eventOnly) {
        if (cleared) {
            return false;
        }

        EventDelta events = rootWatcher ? drainTreeEvents() : EventDelta.empty();
        boolean forceFullScan = fullScan || events.reconciliationRequired();
        boolean nativeEventsActive = isEventWatchActive();
        boolean detected;
        if (forceFullScan) {
            detected = scanFull();
        } else if (eventOnly && nativeEventsActive) {
            resetDeltas();
            detected = false;
        } else {
            detected = scanFast();
        }
        mergeEvents(events);

        if (rootWatcher && (forceFullScan || (!nativeEventsActive && file.isDirectory()))) {
            refreshTreeRegistrations();
        }
        return detected || events.detected() || !changed.isEmpty() || !created.isEmpty() || !deleted.isEmpty();
    }

    private boolean scanFull() {
        resetDeltas();
        if (!file.isDirectory()) {
            boolean hadChildren = !watchers.isEmpty();
            if (hadChildren) {
                for (FolderWatcher watcher : watchers.values()) {
                    deleted.add(watcher.file);
                    watcher.collectKnownFiles(deleted);
                    watcher.close();
                }
                watchers.clear();
            }
            return super.checkModified() || hadChildren;
        }

        readProperties();
        deleted.addAll(pendingDeleted);
        for (Map.Entry<File, FolderWatcher> entry : watchers.entrySet()) {
            File child = entry.getKey();
            FolderWatcher watcher = entry.getValue();
            if (watcher == null) {
                continue;
            }
            if (pendingCreated.contains(child)) {
                created.add(child);
                watcher.collectKnownFiles(created);
                continue;
            }
            if (watcher.scanFull()) {
                changed.add(watcher.file);
            }
            changed.addAll(watcher.getChanged());
            created.addAll(watcher.getCreated());
            deleted.addAll(watcher.getDeleted());
        }
        return !changed.isEmpty() || !created.isEmpty() || !deleted.isEmpty();
    }

    private boolean scanFast() {
        resetDeltas();
        if (watchers.isEmpty() || !file.isDirectory()) {
            return scanFull();
        }

        for (FolderWatcher watcher : watchers.values()) {
            if (watcher == null) {
                continue;
            }
            if (watcher.scanFast()) {
                changed.add(watcher.file);
            }
            changed.addAll(watcher.getChanged());
            created.addAll(watcher.getCreated());
            deleted.addAll(watcher.getDeleted());
        }
        return !changed.isEmpty() || !created.isEmpty() || !deleted.isEmpty();
    }

    private void resetDeltas() {
        changed.clear();
        created.clear();
        deleted.clear();
        pendingCreated.clear();
        pendingDeleted.clear();
    }

    boolean isEventWatchActive() {
        return rootWatcher
                && treeWatchService != null
                && nativeRegistrationComplete
                && !watchedDirectoryPaths.isEmpty();
    }

    private void mergeEvents(EventDelta events) {
        addUnique(created, events.created());
        addUnique(changed, events.changed());
        addUnique(deleted, events.deleted());
        // Writing a file's first bytes raises ENTRY_CREATE and ENTRY_MODIFY together on Windows,
        // and a removal can arrive the same way, so the same file would otherwise be reported as
        // created and changed in one poll. The stronger event wins.
        changed.removeIf(created::contains);
        changed.removeIf(deleted::contains);
    }

    private void addUnique(KList<File> target, Set<File> values) {
        for (File value : values) {
            if (!target.contains(value)) {
                target.add(value);
            }
        }
    }

    private EventDelta drainTreeEvents() {
        initializeTreeWatchService();
        WatchService service = treeWatchService;
        if (service == null) {
            return EventDelta.empty();
        }

        Set<File> eventCreated = new LinkedHashSet<>();
        Set<File> eventChanged = new LinkedHashSet<>();
        Set<File> eventDeleted = new LinkedHashSet<>();
        boolean reconciliationRequired = false;
        boolean detected = false;
        try {
            WatchKey key;
            while ((key = service.poll()) != null) {
                Path directory = watchedDirectoryPaths.get(key);
                if (directory == null) {
                    key.pollEvents();
                    reconciliationRequired = true;
                } else {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        detected = true;
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                            reconciliationRequired = true;
                            continue;
                        }
                        Object context = event.context();
                        if (!(context instanceof Path relativePath)) {
                            reconciliationRequired = true;
                            continue;
                        }
                        Path affected = directory.resolve(relativePath).toAbsolutePath().normalize();
                        File affectedFile = affected.toFile();
                        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                            if (Files.isSymbolicLink(affected)) {
                                continue;
                            }
                            eventCreated.add(affectedFile);
                            if (Files.isDirectory(affected)) {
                                if (!registerTree(affected)) {
                                    nativeRegistrationComplete = false;
                                    reconciliationRequired = true;
                                }
                                collectCurrentFiles(affected, eventCreated);
                            }
                        } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                            eventDeleted.add(affectedFile);
                            reconciliationRequired = true;
                        } else {
                            eventChanged.add(affectedFile);
                        }
                    }
                }
                if (!key.reset()) {
                    removeWatchKey(key);
                    reconciliationRequired = true;
                }
            }
        } catch (ClosedWatchServiceException ignored) {
            closeTreeWatchService();
            reconciliationRequired = true;
        }
        for (File deletedFile : Set.copyOf(eventDeleted)) {
            if (!deletedFile.exists()) {
                eventCreated.remove(deletedFile);
                eventChanged.remove(deletedFile);
                continue;
            }
            eventDeleted.remove(deletedFile);
            eventChanged.add(deletedFile);
        }
        return new EventDelta(eventCreated, eventChanged, eventDeleted, detected, reconciliationRequired);
    }

    private void initializeTreeWatchService() {
        if (!rootWatcher || cleared || treeWatchService != null) {
            return;
        }
        try {
            treeWatchService = FileSystems.getDefault().newWatchService();
            nativeRegistrationComplete = registerTree(file.toPath());
        } catch (IOException | UnsupportedOperationException | SecurityException ignored) {
            closeTreeWatchService();
        }
    }

    private void refreshTreeRegistrations() {
        initializeTreeWatchService();
        if (treeWatchService == null || !file.isDirectory()) {
            return;
        }
        nativeRegistrationComplete = registerTree(file.toPath());
    }

    private boolean registerTree(Path root) {
        WatchService service = treeWatchService;
        if (service == null || root == null || !Files.isDirectory(root)) {
            return false;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                    registerDirectory(directory, service);
                    return FileVisitResult.CONTINUE;
                }
            });
            return true;
        } catch (IOException | SecurityException ignored) {
            return false;
        }
    }

    private void registerDirectory(Path directory, WatchService service) throws IOException {
        Path normalized = directory.toAbsolutePath().normalize();
        if (watchedDirectoryKeys.containsKey(normalized)) {
            return;
        }
        WatchKey key = normalized.register(
                service,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE
        );
        watchedDirectoryKeys.put(normalized, key);
        watchedDirectoryPaths.put(key, normalized);
    }

    private void removeWatchKey(WatchKey key) {
        Path path = watchedDirectoryPaths.remove(key);
        if (path != null) {
            watchedDirectoryKeys.remove(path);
        }
        key.cancel();
    }

    private void collectCurrentFiles(Path root, Set<File> target) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (attributes.isRegularFile()) {
                        target.add(file.toFile());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | SecurityException ignored) {
        }
    }

    private void collectKnownFiles(KList<File> target) {
        for (FolderWatcher watcher : watchers.values()) {
            if (watcher == null) {
                continue;
            }
            target.addIfMissing(watcher.file);
            watcher.collectKnownFiles(target);
        }
    }

    private Path directoryIdentity(File directory) {
        try {
            return directory.toPath().toRealPath();
        } catch (IOException | SecurityException ignored) {
            return directory.toPath().toAbsolutePath().normalize();
        }
    }

    public KMap<File, FolderWatcher> getWatchers() {
        return watchers;
    }

    public KList<File> getChanged() {
        return changed;
    }

    public KList<File> getCreated() {
        return created;
    }

    public KList<File> getDeleted() {
        return deleted;
    }

    public void clear() {
        if (cleared) {
            return;
        }
        cleared = true;
        closeTreeWatchService();
        for (FolderWatcher watcher : watchers.values()) {
            if (watcher != null) {
                watcher.close();
            }
        }
        watchers.clear();
        changed.clear();
        deleted.clear();
        created.clear();
        pendingCreated.clear();
        pendingDeleted.clear();
        super.close();
    }

    @Override
    public void close() {
        clear();
    }

    private void closeTreeWatchService() {
        for (WatchKey key : watchedDirectoryPaths.keySet()) {
            key.cancel();
        }
        watchedDirectoryPaths.clear();
        watchedDirectoryKeys.clear();
        nativeRegistrationComplete = false;
        WatchService service = treeWatchService;
        treeWatchService = null;
        if (service == null) {
            return;
        }
        try {
            service.close();
        } catch (IOException ignored) {
        }
    }

    private record EventDelta(
            Set<File> created,
            Set<File> changed,
            Set<File> deleted,
            boolean detected,
            boolean reconciliationRequired
    ) {
        private static EventDelta empty() {
            return new EventDelta(Set.of(), Set.of(), Set.of(), false, false);
        }
    }
}

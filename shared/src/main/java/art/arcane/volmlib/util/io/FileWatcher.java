package art.arcane.volmlib.util.io;

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
import java.util.Objects;

public class FileWatcher implements AutoCloseable {
    protected final File file;
    private final boolean eventWatchingEnabled;
    private Path path;
    private long lastModified;
    private long size;
    private Object fileKey;
    private WatchService watchService;
    private WatchKey watchKey;
    private boolean closed;

    public FileWatcher(File file) {
        this(file, true, true);
    }

    protected FileWatcher(File file, boolean eventWatchingEnabled) {
        this(file, eventWatchingEnabled, true);
    }

    protected FileWatcher(File file, boolean eventWatchingEnabled, boolean initialize) {
        this.file = file;
        this.eventWatchingEnabled = eventWatchingEnabled;
        if (initialize) {
            readProperties();
            initializeWatchService();
        }
    }

    protected void readProperties() {
        try {
            BasicFileAttributes attributes = Files.readAttributes(path(), BasicFileAttributes.class);
            lastModified = attributes.lastModifiedTime().toMillis();
            size = attributes.isDirectory() ? -2 : attributes.size();
            fileKey = attributes.fileKey();
        } catch (IOException | SecurityException missing) {
            lastModified = -1;
            size = -1;
            fileKey = null;
        }
    }

    public boolean checkModified() {
        if (closed) {
            return false;
        }
        long m = lastModified;
        long g = size;
        Object previousFileKey = fileKey;
        boolean eventDetected = drainEvents();
        readProperties();
        return eventDetected || lastModified != m || g != size || !Objects.equals(previousFileKey, fileKey);
    }

    public boolean wasDeleted() {
        return !file.exists();
    }

    @Override
    public void close() {
        closed = true;
        closeWatchService();
    }

    private boolean drainEvents() {
        if (!eventWatchingEnabled || closed) {
            return false;
        }

        initializeWatchService();
        WatchService service = watchService;
        if (service == null) {
            return false;
        }

        boolean changed = false;
        try {
            WatchKey key;
            while ((key = service.poll()) != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        changed = true;
                        continue;
                    }
                    Object context = event.context();
                    if (context instanceof Path relativePath
                            && path().equals(watchedDirectory().resolve(relativePath).toAbsolutePath().normalize())) {
                        changed = true;
                    }
                }
                if (!key.reset()) {
                    closeWatchService();
                    changed = true;
                }
            }
        } catch (ClosedWatchServiceException ignored) {
            closeWatchService();
            changed = true;
        }
        return changed;
    }

    private void initializeWatchService() {
        if (!eventWatchingEnabled || closed || watchService != null) {
            return;
        }

        Path directory = watchedDirectory();
        if (directory == null || !Files.isDirectory(directory)) {
            return;
        }

        WatchService service = null;
        try {
            service = FileSystems.getDefault().newWatchService();
            WatchKey key = directory.register(
                    service,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE
            );
            watchService = service;
            watchKey = key;
        } catch (IOException | UnsupportedOperationException | SecurityException ignored) {
            if (service != null) {
                try {
                    service.close();
                } catch (IOException closeFailure) {
                    ignored.addSuppressed(closeFailure);
                }
            }
        }
    }

    private void closeWatchService() {
        WatchKey key = watchKey;
        watchKey = null;
        if (key != null) {
            key.cancel();
        }

        WatchService service = watchService;
        watchService = null;
        if (service == null) {
            return;
        }

        try {
            service.close();
        } catch (IOException ignored) {
        }
    }

    private Path watchedDirectory() {
        Path target = path();
        return target == null ? null : target.getParent();
    }

    private Path path() {
        Path resolved = path;

        if (resolved == null) {
            resolved = file.toPath().toAbsolutePath().normalize();
            path = resolved;
        }

        return resolved;
    }
}

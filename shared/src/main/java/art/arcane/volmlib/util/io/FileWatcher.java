package art.arcane.volmlib.util.io;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public class FileWatcher {
    protected final File file;
    private Path path;
    private long lastModified;
    private long size;

    public FileWatcher(File file) {
        this.file = file;
        readProperties();
    }

    protected void readProperties() {
        // One stat per poll: exists/lastModified/isDirectory/length used to be four separate
        // filesystem round trips per watched file, and a hot-reload tree polls all of them.
        try {
            BasicFileAttributes attributes = Files.readAttributes(path(), BasicFileAttributes.class);
            lastModified = attributes.lastModifiedTime().toMillis();
            size = attributes.isDirectory() ? -2 : attributes.size();
        } catch (Throwable missing) {
            lastModified = -1;
            size = -1;
        }
    }

    public boolean checkModified() {
        long m = lastModified;
        long g = size;
        boolean mod = false;
        readProperties();

        if (lastModified != m || g != size) {
            mod = true;
        }

        return mod;
    }

    public boolean wasDeleted() {
        return !file.exists();
    }

    private Path path() {
        Path resolved = path;

        if (resolved == null) {
            resolved = file.toPath();
            path = resolved;
        }

        return resolved;
    }
}

package art.arcane.volmlib.util.io;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;

import java.io.File;
import java.util.Map;

public class FolderWatcher extends FileWatcher {
    private KMap<File, FolderWatcher> watchers;
    private KList<File> changed;
    private KList<File> created;
    private KList<File> deleted;
    private KList<File> pendingCreated;
    private KList<File> pendingDeleted;
    /**
     * Stays false for the duration of the {@link FileWatcher} constructor's {@code readProperties()}
     * call, so the initial listing is not reported as a wave of creations.
     */
    private boolean deltaTracking;

    public FolderWatcher(File file) {
        super(file);
        deltaTracking = true;
    }

    protected void readProperties() {
        if (watchers == null) {
            watchers = new KMap<>();
            changed = new KList<>();
            created = new KList<>();
            deleted = new KList<>();
            pendingCreated = new KList<>();
            pendingDeleted = new KList<>();
        }

        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File i : files) {
                    if (i.isDirectory() && i.getName().startsWith(".")) {
                        continue;
                    }

                    if (!watchers.containsKey(i)) {
                        watchers.put(i, new FolderWatcher(i));

                        if (deltaTracking) {
                            pendingCreated.add(i);
                        }
                    }
                }
            }

            watchers.values().removeIf(watcher -> {
                if (!watcher.wasDeleted()) {
                    return false;
                }

                if (deltaTracking) {
                    pendingDeleted.add(watcher.file);
                }

                return true;
            });
        } else {
            super.readProperties();
        }
    }

    public boolean checkModified() {
        changed.clear();
        created.clear();
        deleted.clear();
        pendingCreated.clear();
        pendingDeleted.clear();

        if (file.isDirectory()) {
            // readProperties() is the only thing that adds or drops child watchers, so it reports
            // the membership delta itself instead of every poll copying the watcher map to diff
            // against afterwards.
            readProperties();
            deleted.addAll(pendingDeleted);

            for (Map.Entry<File, FolderWatcher> entry : watchers.entrySet()) {
                File i = entry.getKey();

                if (pendingCreated.contains(i)) {
                    created.add(i);
                    continue;
                }

                FolderWatcher fw = entry.getValue();
                if (fw == null) {
                    continue;
                }
                if (fw.checkModified()) {
                    changed.add(fw.file);
                }

                changed.addAll(fw.getChanged());
                created.addAll(fw.getCreated());
                deleted.addAll(fw.getDeleted());
            }

            return !changed.isEmpty() || !created.isEmpty() || !deleted.isEmpty();
        }

        return super.checkModified();
    }

    public boolean checkModifiedFast() {
        if (watchers == null || watchers.isEmpty()) {
            return checkModified();
        }

        changed.clear();
        created.clear();
        deleted.clear();
        pendingCreated.clear();
        pendingDeleted.clear();

        if (file.isDirectory()) {
            for (Map.Entry<File, FolderWatcher> entry : watchers.entrySet()) {
                FolderWatcher fw = entry.getValue();
                if (fw == null) {
                    continue;
                }
                if (fw.checkModifiedFast()) {
                    changed.add(fw.file);
                }

                changed.addAll(fw.getChanged());
                created.addAll(fw.getCreated());
                deleted.addAll(fw.getDeleted());
            }

            return !changed.isEmpty() || !created.isEmpty() || !deleted.isEmpty();
        }

        return super.checkModified();
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
        watchers.clear();
        changed.clear();
        deleted.clear();
        created.clear();
        pendingCreated.clear();
        pendingDeleted.clear();
    }
}

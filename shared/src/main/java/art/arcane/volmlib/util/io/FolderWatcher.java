package art.arcane.volmlib.util.io;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;

import java.io.File;

public class FolderWatcher extends FileWatcher {
    private KMap<File, FolderWatcher> watchers;
    private KList<File> changed;
    private KList<File> created;
    private KList<File> deleted;

    public FolderWatcher(File file) {
        super(file);
    }

    protected void readProperties() {
        if (watchers == null) {
            watchers = new KMap<>();
            changed = new KList<>();
            created = new KList<>();
            deleted = new KList<>();
        }

        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File i : files) {
                    if (!watchers.containsKey(i)) {
                        watchers.put(i, new FolderWatcher(i));
                    }
                }
            }

            watchers.values().removeIf(FileWatcher::wasDeleted);
        } else {
            super.readProperties();
        }
    }

    public boolean checkModified() {
        changed.clear();
        created.clear();
        deleted.clear();

        if (file.isDirectory()) {
            KMap<File, FolderWatcher> w = watchers.copy();
            readProperties();

            for (File i : w.keySet()) {
                if (!watchers.containsKey(i)) {
                    deleted.add(i);
                }
            }

            for (File i : watchers.keySet()) {
                if (!w.containsKey(i)) {
                    created.add(i);
                } else {
                    FolderWatcher fw = watchers.get(i);
                    if (fw.checkModified()) {
                        changed.add(fw.file);
                    }

                    changed.addAll(fw.getChanged());
                    created.addAll(fw.getCreated());
                    deleted.addAll(fw.getDeleted());
                }
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

        if (file.isDirectory()) {
            for (File i : watchers.keySet()) {
                FolderWatcher fw = watchers.get(i);
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
    }
}

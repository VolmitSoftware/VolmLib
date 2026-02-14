package art.arcane.volmlib.util.io;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.function.Consumer3;

import java.io.File;

public class ReactiveFolder {
    private final File folder;
    private final Consumer3<KList<File>, KList<File>, KList<File>> hotload;
    private final KList<String> watchedExtensions;
    private final KList<String> ignoredPathContains;
    private final KList<String> ignoredNameSuffixes;
    private FolderWatcher fw;
    private int checkCycle = 0;

    public ReactiveFolder(File folder,
                          Consumer3<KList<File>, KList<File>, KList<File>> hotload,
                          KList<String> watchedExtensions,
                          KList<String> ignoredPathContains,
                          KList<String> ignoredNameSuffixes) {
        this.folder = folder;
        this.hotload = hotload;
        this.watchedExtensions = watchedExtensions;
        this.ignoredPathContains = ignoredPathContains;
        this.ignoredNameSuffixes = ignoredNameSuffixes;
        this.fw = new FolderWatcher(folder);
        fw.checkModified();
    }

    public void checkIgnore() {
        fw = new FolderWatcher(folder);
    }

    public boolean check() {
        checkCycle++;
        boolean modified = false;

        if (checkCycle % 3 == 0 ? fw.checkModified() : fw.checkModifiedFast()) {
            modified = matchesAny(fw.getCreated()) || matchesAny(fw.getChanged()) || matchesAny(fw.getDeleted());
        }

        if (modified) {
            hotload.accept(fw.getCreated(), fw.getChanged(), fw.getDeleted());
        }

        return fw.checkModified();
    }

    private boolean matchesAny(KList<File> files) {
        for (File file : files) {
            if (isIgnored(file)) {
                continue;
            }

            if (isWatched(file)) {
                return true;
            }
        }

        return false;
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

    public void clear() {
        fw.clear();
    }
}

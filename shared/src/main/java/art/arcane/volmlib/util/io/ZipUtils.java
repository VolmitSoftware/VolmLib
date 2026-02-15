package art.arcane.volmlib.util.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ZipUtils {
    private ZipUtils() {
    }

    public static void unzipFile(File zipFile, File targetDir) throws IOException {
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("Failed to create target directory: " + targetDir);
        }

        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                File target = zipSlipProtect(entry, targetDir);
                if (entry.isDirectory()) {
                    if (!target.isDirectory() && !target.mkdirs()) {
                        throw new IOException("Failed to create zip directory for entry \"" + target + "\"!");
                    }
                } else {
                    File parent = target.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new IOException("Failed to create zip file parent directory for entry \"" + target + "\"!");
                    }

                    try (FileOutputStream out = new FileOutputStream(target)) {
                        int length;
                        while ((length = zip.read(buffer)) > 0) {
                            out.write(buffer, 0, length);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private static File zipSlipProtect(ZipEntry entry, File targetDir) throws IOException {
        File target = new File(targetDir, entry.getName());
        String targetPath = target.getCanonicalPath();
        String targetDirPath = targetDir.getCanonicalPath();

        if (!targetPath.startsWith(targetDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + entry.getName());
        }

        return target;
    }
}

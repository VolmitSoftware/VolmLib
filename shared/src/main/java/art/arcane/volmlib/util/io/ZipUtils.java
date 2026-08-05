package art.arcane.volmlib.util.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ZipUtils {
    private ZipUtils() {

    }

    public static void unzipFile(File zipFile, File targetDir) throws IOException {
        unzipFile(zipFile, targetDir, Integer.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE);
    }

    public static void unzipFile(File zipFile, File targetDir, int maxEntries, long maxExpandedBytes, long maxEntryBytes) throws IOException {
        if (maxEntries < 1 || maxExpandedBytes < 1 || maxEntryBytes < 1) {
            throw new IllegalArgumentException("Zip extraction limits must be positive");
        }
        Path source = zipFile.toPath().toAbsolutePath().normalize();
        if (Files.isSymbolicLink(source) || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Zip source must be a regular file: " + zipFile);
        }
        Path sourceReal = source.toRealPath();
        Path targetRoot = targetDir.toPath().toAbsolutePath().normalize();
        if (Files.isSymbolicLink(targetRoot)) {
            throw new IOException("Target directory cannot be a symbolic link: " + targetDir);
        }
        if (!Files.exists(targetRoot, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(targetRoot);
        }
        if (Files.isSymbolicLink(targetRoot)
                || !Files.isDirectory(targetRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Zip target must be a directory: " + targetDir);
        }

        targetRoot = targetRoot.toRealPath();
        Path targetRootReal = targetRoot;
        byte[] buffer = new byte[8192];
        Set<Path> targets = new HashSet<>();
        Set<Object> targetFileKeys = new HashSet<>();
        List<Path> keylessTargets = new ArrayList<>();
        List<Path> createdDirectories = new ArrayList<>();
        List<ExtractionChange> changes = new ArrayList<>();
        int entries = 0;
        long expandedBytes = 0;
        try (InputStream input = Files.newInputStream(sourceReal, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
             ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entries >= maxEntries) {
                    throw new IOException("Zip contains more than " + maxEntries + " entries");
                }
                entries++;
                Path target = zipSlipProtect(entry, targetRoot, targetRootReal);
                Path targetIdentity = resolveEffectivePath(target);
                if (!targets.add(targetIdentity)) {
                    throw new IOException("Zip contains duplicate entry: " + entry.getName());
                }
                rejectFileAlias(target, targetFileKeys, keylessTargets, entry.getName());
                rejectSourceAlias(sourceReal, target, targetIdentity, entry);
                long declaredSize = entry.getSize();
                if (declaredSize > maxEntryBytes
                        || (declaredSize >= 0 && declaredSize > maxExpandedBytes - expandedBytes)) {
                    throw new IOException("Zip entry exceeds extraction limits: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    EntryData data = transferEntry(
                            zip,
                            null,
                            null,
                            buffer,
                            entry.getName(),
                            expandedBytes,
                            maxExpandedBytes,
                            maxEntryBytes
                    );
                    expandedBytes += data.bytes();
                    if (data.bytes() != 0) {
                        throw new IOException("Zip directory entry contains data: " + entry.getName());
                    }
                    createDirectories(target, targetRoot, targetRootReal, createdDirectories, maxEntries);
                    rememberFileIdentity(target, targetFileKeys, keylessTargets);
                } else {
                    Path parent = target.getParent();
                    if (parent == null) {
                        throw new IOException("Zip file entry has no parent: " + entry.getName());
                    }
                    createDirectories(parent, targetRoot, targetRootReal, createdDirectories, maxEntries);
                    validateTarget(target, targetRoot, targetRootReal, entry.getName());
                    Path temporary = Files.createTempFile(parent, ".iris-extract-", ".tmp");
                    EntryData data;
                    try {
                        MessageDigest digest = sha256();
                        try (OutputStream output = Files.newOutputStream(
                                temporary,
                                StandardOpenOption.WRITE,
                                StandardOpenOption.TRUNCATE_EXISTING,
                                LinkOption.NOFOLLOW_LINKS
                        )) {
                            data = transferEntry(
                                    zip,
                                    output,
                                    digest,
                                    buffer,
                                    entry.getName(),
                                    expandedBytes,
                                    maxExpandedBytes,
                                    maxEntryBytes
                            );
                        }
                        expandedBytes += data.bytes();
                        publishFile(
                                sourceReal,
                                target,
                                targetRoot,
                                targetRootReal,
                                temporary,
                                data,
                                entry,
                                changes
                        );
                        rememberFileIdentity(target, targetFileKeys, keylessTargets);
                    } finally {
                        Files.deleteIfExists(temporary);
                    }
                }
                zip.closeEntry();
            }
        } catch (IOException | RuntimeException failure) {
            rollback(changes, createdDirectories, failure);
            if (failure instanceof IOException ioFailure) {
                throw ioFailure;
            }
            throw new IOException("Zip extraction failed", failure);
        }
        cleanupBackups(changes);
    }

    private static void publishFile(
            Path source,
            Path target,
            Path targetRoot,
            Path targetRootReal,
            Path temporary,
            EntryData data,
            ZipEntry entry,
            List<ExtractionChange> changes
    ) throws IOException {
        validateTarget(target, targetRoot, targetRootReal, entry.getName());
        Path targetIdentity = resolveEffectivePath(target);
        rejectSourceAlias(source, target, targetIdentity, entry);
        boolean existed = Files.exists(target, LinkOption.NOFOLLOW_LINKS);
        if (existed && (Files.isSymbolicLink(target)
                || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("Zip file entry replaces a non-file path: " + entry.getName());
        }

        Path backup = existed ? Files.createTempFile(target.getParent(), ".iris-extract-backup-", ".tmp") : null;
        ExtractionChange change = new ExtractionChange(target, backup);
        changes.add(change);
        if (backup != null) {
            Files.move(target, backup, StandardCopyOption.REPLACE_EXISTING);
            change.backupCaptured = true;
            if (Files.isSymbolicLink(backup)
                    || !Files.isRegularFile(backup, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Zip target changed while being replaced: " + entry.getName());
            }
        }
        validateTarget(target, targetRoot, targetRootReal, entry.getName());
        rejectSourceAlias(source, target, resolveEffectivePath(target), entry);
        Files.move(temporary, target);
        change.markPublished(data.digest(), data.bytes());
    }

    private static EntryData transferEntry(
            ZipInputStream zip,
            OutputStream output,
            MessageDigest digest,
            byte[] buffer,
            String entryName,
            long expandedBytes,
            long maxExpandedBytes,
            long maxEntryBytes
    ) throws IOException {
        long entryBytes = 0;
        int length;
        while ((length = zip.read(buffer)) != -1) {
            if (length == 0) {
                continue;
            }
            if (length > maxEntryBytes - entryBytes) {
                throw new IOException("Zip entry exceeds " + maxEntryBytes + " bytes: " + entryName);
            }
            if (length > maxExpandedBytes - expandedBytes - entryBytes) {
                throw new IOException("Zip expands beyond " + maxExpandedBytes + " bytes");
            }
            if (output != null) {
                output.write(buffer, 0, length);
            }
            if (digest != null) {
                digest.update(buffer, 0, length);
            }
            entryBytes += length;
        }
        return new EntryData(entryBytes, digest == null ? new byte[0] : digest.digest());
    }

    private static Path zipSlipProtect(
            ZipEntry entry,
            Path targetRoot,
            Path targetRootReal
    ) throws IOException {
        Path target;
        try {
            target = targetRoot.resolve(entry.getName()).normalize();
        } catch (RuntimeException failure) {
            throw new IOException("Invalid zip entry path: " + entry.getName(), failure);
        }
        if (!target.startsWith(targetRoot)) {
            throw new IOException("Entry is outside of the target dir: " + entry.getName());
        }
        validateTarget(target, targetRoot, targetRootReal, entry.getName());
        return target;
    }

    private static void validateTarget(
            Path target,
            Path targetRoot,
            Path targetRootReal,
            String entryName
    ) throws IOException {
        Path current = targetRoot;
        if (Files.isSymbolicLink(current)) {
            throw new IOException("Entry traverses a symbolic link: " + entryName);
        }
        Path relative = targetRoot.relativize(target);
        for (Path name : relative) {
            current = current.resolve(name);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Entry traverses a symbolic link: " + entryName);
            }
        }
        Path identity = resolveEffectivePath(target);
        if (!identity.startsWith(targetRootReal)) {
            throw new IOException("Entry is outside of the target dir: " + entryName);
        }
    }

    private static void rejectSourceAlias(
            Path source,
            Path target,
            Path targetIdentity,
            ZipEntry entry
    ) throws IOException {
        if (source.equals(targetIdentity)
                || (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && Files.isSameFile(source, target))) {
            throw new IOException("Zip entry cannot replace its source archive: " + entry.getName());
        }
    }

    private static void rejectFileAlias(
            Path target,
            Set<Object> fileKeys,
            List<Path> keylessTargets,
            String entryName
    ) throws IOException {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        BasicFileAttributes attributes = Files.readAttributes(
                target,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
        );
        Object fileKey = attributes.fileKey();
        if (fileKey != null) {
            if (!fileKeys.add(fileKey)) {
                throw new IOException("Zip contains aliased duplicate entry: " + entryName);
            }
            return;
        }
        for (Path existing : keylessTargets) {
            if (Files.exists(existing, LinkOption.NOFOLLOW_LINKS) && Files.isSameFile(existing, target)) {
                throw new IOException("Zip contains aliased duplicate entry: " + entryName);
            }
        }
        keylessTargets.add(target);
    }

    private static void rememberFileIdentity(
            Path target,
            Set<Object> fileKeys,
            List<Path> keylessTargets
    ) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                target,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
        );
        Object fileKey = attributes.fileKey();
        if (fileKey != null) {
            fileKeys.add(fileKey);
        } else if (!keylessTargets.contains(target)) {
            keylessTargets.add(target);
        }
    }

    private static void createDirectories(
            Path directory,
            Path targetRoot,
            Path targetRootReal,
            List<Path> created,
            int maxCreatedDirectories
    ) throws IOException {
        validateTarget(directory, targetRoot, targetRootReal, directory.toString());
        if (Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Zip directory path is not a directory: " + directory);
        }

        Deque<Path> missing = new ArrayDeque<>();
        Path current = directory;
        while (current != null && !Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            missing.addFirst(current);
            current = current.getParent();
        }
        if (missing.size() > maxCreatedDirectories - created.size()) {
            throw new IOException("Zip creates more than " + maxCreatedDirectories + " directories: " + directory);
        }
        if (current == null || Files.isSymbolicLink(current)
                || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Zip directory has an invalid parent: " + directory);
        }
        for (Path item : missing) {
            Files.createDirectory(item);
            created.add(item);
            validateTarget(item, targetRoot, targetRootReal, directory.toString());
        }
    }

    private static Path resolveEffectivePath(Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        Path existing = normalized;
        Deque<Path> missing = new ArrayDeque<>();
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            Path name = existing.getFileName();
            if (name != null) {
                missing.addFirst(name);
            }
            existing = existing.getParent();
        }
        if (existing == null) {
            throw new IOException("Zip path has no existing ancestor: " + path);
        }
        Path resolved = existing.toRealPath();
        for (Path name : missing) {
            resolved = resolved.resolve(name);
        }
        return resolved.normalize();
    }

    private static MessageDigest sha256() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IOException("SHA-256 is unavailable", failure);
        }
    }

    static BasicFileAttributes readBasicAttributes(Path path) throws IOException {
        return Files.readAttributes(
                path,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
        );
    }

    private static void rollback(
            List<ExtractionChange> changes,
            List<Path> createdDirectories,
            Throwable failure
    ) {
        for (int i = changes.size() - 1; i >= 0; i--) {
            ExtractionChange change = changes.get(i);
            try {
                rollback(change);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
        for (int i = createdDirectories.size() - 1; i >= 0; i--) {
            try {
                Files.deleteIfExists(createdDirectories.get(i));
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
    }

    private static void rollback(ExtractionChange change) throws IOException {
        if (change.installed && Files.exists(change.target, LinkOption.NOFOLLOW_LINKS)) {
            if (!change.matchesPublished()) {
                throw new IOException("Zip rollback preserved a target changed during extraction: " + change.target);
            }
            Files.delete(change.target);
        }
        if (change.backupCaptured) {
            if (Files.exists(change.target, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Zip rollback preserved a concurrent target; original remains at " + change.backup);
            }
            Files.move(change.backup, change.target);
            return;
        }
        if (change.backup != null) {
            Files.deleteIfExists(change.backup);
        }
    }

    private static void cleanupBackups(List<ExtractionChange> changes) throws IOException {
        IOException failure = null;
        for (ExtractionChange change : changes) {
            if (!change.published) {
                continue;
            }
            try {
                if (!Files.exists(change.target, LinkOption.NOFOLLOW_LINKS)
                        || !(change.backupCaptured
                        ? change.matchesPublished()
                        : change.matchesPublishedIdentity())) {
                    throw new IOException(
                            "Zip extraction target changed before commit: " + change.target
                                    + (change.backupCaptured ? "; original remains at " + change.backup : "")
                    );
                }
            } catch (IOException verificationFailure) {
                if (failure == null) {
                    failure = verificationFailure;
                } else {
                    failure.addSuppressed(verificationFailure);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
        for (ExtractionChange change : changes) {
            if (!change.backupCaptured) {
                continue;
            }
            try {
                Files.deleteIfExists(change.backup);
            } catch (IOException cleanupFailure) {
                if (failure == null) {
                    failure = cleanupFailure;
                } else {
                    failure.addSuppressed(cleanupFailure);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private record EntryData(long bytes, byte[] digest) {
    }

    private static final class ExtractionChange {
        private final Path target;
        private final Path backup;
        private boolean backupCaptured;
        private boolean installed;
        private boolean published;
        private Object publishedFileKey;
        private long publishedBytes;
        private byte[] publishedDigest;
        private FileTime publishedModifiedTime;

        private ExtractionChange(Path target, Path backup) {
            this.target = target;
            this.backup = backup;
        }

        private void markPublished(byte[] digest, long bytes) throws IOException {
            installed = true;
            publishedDigest = digest.clone();
            publishedBytes = bytes;
            BasicFileAttributes attributes = readBasicAttributes(target);
            publishedFileKey = attributes.fileKey();
            publishedModifiedTime = attributes.lastModifiedTime();
            published = true;
        }

        private boolean matchesPublishedIdentity() throws IOException {
            if (Files.isSymbolicLink(target)
                    || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                return false;
            }
            BasicFileAttributes attributes = Files.readAttributes(
                    target,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS
            );
            if (attributes.size() != publishedBytes) {
                return false;
            }
            if (publishedFileKey != null && !publishedFileKey.equals(attributes.fileKey())) {
                return false;
            }
            return publishedModifiedTime == null
                    || publishedModifiedTime.equals(attributes.lastModifiedTime());
        }

        private boolean matchesPublished() throws IOException {
            if (!matchesPublishedIdentity()) {
                return false;
            }
            MessageDigest digest = sha256();
            try (InputStream input = Files.newInputStream(
                    target,
                    StandardOpenOption.READ,
                    LinkOption.NOFOLLOW_LINKS
            )) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = input.read(buffer)) != -1) {
                    if (length > 0) {
                        digest.update(buffer, 0, length);
                    }
                }
            }
            return Arrays.equals(publishedDigest, digest.digest());
        }
    }
}

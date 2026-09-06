package art.arcane.volmit.packaging;

import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;

public final class JarCompactor {
    private static final int BUFFER_BYTES = 64 * 1024;

    private JarCompactor() {
    }

    public static void compact(File artifact, CompactionOptions options) throws IOException {
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(options, "options");
        Path source = artifact.toPath().toAbsolutePath();
        if (!Files.isRegularFile(source)) {
            throw new IOException("Cannot compact missing jar artifact: " + source);
        }
        Path temporary = Files.createTempFile(source.getParent(), artifact.getName(), ".compact");
        try {
            Map<String, EntryIdentity> expected = rewrite(source, temporary, options);
            verify(temporary, expected);
            if (Files.size(temporary) < Files.size(source)) {
                Files.move(temporary, source, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException failure) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        Files.deleteIfExists(temporary);
    }

    private static Map<String, EntryIdentity> rewrite(Path source, Path destination, CompactionOptions options)
            throws IOException {
        Map<String, EntryIdentity> expected = new LinkedHashMap<>();
        Set<String> names = new HashSet<>();
        byte[] buffer = new byte[BUFFER_BYTES];
        ReleaseDeflater releaseDeflater = options.releaseCompression() ? new ReleaseDeflater() : null;
        try (ZipFile input = ZipFile.builder().setPath(source).get();
             ZipArchiveOutputStream output = new ZipArchiveOutputStream(destination);
             JarFile archiveMetadata = new JarFile(source.toFile(), false)) {
            if (!output.isSeekable()) {
                throw new IOException("Jar compaction requires seekable output.");
            }
            output.setLevel(9);
            output.setUseZip64(Zip64Mode.AsNeeded);
            output.setCreateUnicodeExtraFields(ZipArchiveOutputStream.UnicodeExtraFieldPolicy.NEVER);
            if (archiveMetadata.getComment() != null) {
                output.setComment(archiveMetadata.getComment());
            }
            Enumeration<ZipArchiveEntry> entries = input.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                if (!names.add(entry.getName())) {
                    throw new IOException("Duplicate jar entry: " + entry.getName());
                }
                boolean explicitlyRemoved = options.removedEntries().contains(entry.getName());
                if (explicitlyRemoved && entry.isDirectory()) {
                    throw new IOException("Cannot explicitly remove a jar directory: " + entry.getName());
                }
                boolean removed = explicitlyRemoved || options.stripDirectories() && entry.isDirectory();
                if (removed) {
                    copyEntry(input, OutputStream.nullOutputStream(), entry, buffer);
                } else {
                    expected.put(entry.getName(), writeEntry(input, output, entry, buffer,
                            options.stripLocalVariables(), releaseDeflater));
                }
            }
        }
        return expected;
    }

    private static EntryIdentity writeEntry(ZipFile input, ZipArchiveOutputStream output, ZipArchiveEntry entry,
                                           byte[] buffer, boolean stripLocalVariables, ReleaseDeflater releaseDeflater)
            throws IOException {
        boolean transformClass = stripLocalVariables && entry.getName().endsWith(".class");
        ZipArchiveEntry target = new ZipArchiveEntry(entry);
        target.setMethod(ZipEntry.DEFLATED);
        target.setCompressedSize(-1L);
        if (!transformClass && releaseDeflater == null) {
            output.putArchiveEntry(target);
            EntryIdentity identity = copyEntry(input, output, entry, buffer);
            output.closeArchiveEntry();
            return identity;
        }
        ByteArrayOutputStream source = new ByteArrayOutputStream();
        EntryIdentity originalIdentity = copyEntry(input, source, entry, buffer);
        byte[] original = source.toByteArray();
        byte[] content = transformClass ? LocalVariableStripper.strip(original) : original;
        EntryIdentity identity = content == original ? originalIdentity : identity(content);
        target.setSize(identity.size());
        target.setCrc(identity.crc());
        if (releaseDeflater != null) {
            byte[] compressed = releaseDeflater.compress(content);
            target.setCompressedSize(compressed.length);
            output.addRawArchiveEntry(target, new ByteArrayInputStream(compressed));
        } else {
            output.putArchiveEntry(target);
            output.write(content);
            output.closeArchiveEntry();
        }
        return identity;
    }

    private static EntryIdentity identity(byte[] content) {
        CRC32 crc = new CRC32();
        crc.update(content);
        return new EntryIdentity(content.length, crc.getValue(), HexFormat.of().formatHex(sha256().digest(content)));
    }

    private static EntryIdentity copyEntry(ZipFile archive, OutputStream output, ZipArchiveEntry entry, byte[] buffer)
            throws IOException {
        CRC32 crc = new CRC32();
        MessageDigest digest = sha256();
        long size = 0L;
        try (InputStream content = archive.getInputStream(entry)) {
            int read;
            while ((read = content.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                crc.update(buffer, 0, read);
                digest.update(buffer, 0, read);
                size += read;
            }
        }
        if (size != entry.getSize() || crc.getValue() != entry.getCrc()) {
            throw new IOException("Jar entry failed size or CRC verification: " + entry.getName());
        }
        return new EntryIdentity(size, crc.getValue(), HexFormat.of().formatHex(digest.digest()));
    }

    private static void verify(Path candidate, Map<String, EntryIdentity> expected) throws IOException {
        byte[] buffer = new byte[BUFFER_BYTES];
        try (ZipFile archive = ZipFile.builder().setPath(candidate).get()) {
            Enumeration<ZipArchiveEntry> entries = archive.getEntries();
            for (Map.Entry<String, EntryIdentity> expectation : expected.entrySet()) {
                if (!entries.hasMoreElements()) {
                    throw new IOException("Compacted jar is missing entry: " + expectation.getKey());
                }
                ZipArchiveEntry entry = entries.nextElement();
                if (!entry.getName().equals(expectation.getKey())
                        || entry.getGeneralPurposeBit().usesDataDescriptor()
                        || !copyEntry(archive, OutputStream.nullOutputStream(), entry, buffer)
                        .equals(expectation.getValue())) {
                    throw new IOException("Compacted jar changed entry: " + expectation.getKey());
                }
            }
            if (entries.hasMoreElements()) {
                throw new IOException("Compacted jar contains unexpected entries.");
            }
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable.", failure);
        }
    }

    public record CompactionOptions(Set<String> removedEntries, boolean stripDirectories,
                                    boolean stripLocalVariables, boolean releaseCompression) {
        public CompactionOptions {
            removedEntries = Set.copyOf(removedEntries);
        }
    }

    private record EntryIdentity(long size, long crc, String sha256) {
    }
}

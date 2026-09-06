package art.arcane.volmit.packaging;

import art.arcane.volmit.packaging.JarCompactor.CompactionOptions;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JarCompactorTest {
    @TempDir
    Path temporaryFolder;

    @Test
    public void preservesResourcesAndClassLoaderDirectoryDiscovery() throws Exception {
        File artifact = temporaryFolder.resolve("artifact.jar").toFile();
        byte[] content = "Plugin artifact content".getBytes(StandardCharsets.UTF_8);
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(artifact))) {
            output.putNextEntry(new JarEntry("example/"));
            output.closeEntry();
            output.putNextEntry(new JarEntry("example/value.txt"));
            output.write(content);
            output.closeEntry();
        }

        JarCompactor.compact(artifact, new CompactionOptions(Set.of(), false, false, false));

        try (JarFile jar = new JarFile(artifact, false)) {
            assertNotNull(jar.getJarEntry("example/"));
            assertArrayEquals(content, jar.getInputStream(
                    jar.getJarEntry("example/value.txt")).readAllBytes());
        }
        try (URLClassLoader loader = new URLClassLoader(new URL[]{artifact.toURI().toURL()}, null)) {
            assertNotNull(loader.getResource("example/"));
            assertNotNull(loader.getResource("example/value.txt"));
        }
    }

    @Test
    public void preservesJarIdentityAndProducesDeterministicSeekableOutput() throws Exception {
        File artifact = temporaryFolder.resolve("identity.jar").toFile();
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(artifact));
             InputStream classResource = getClass().getResourceAsStream("/art/arcane/volmit/packaging/JarCompactorTest.class")) {
            output.setLevel(9);
            output.setComment("preserved archive comment");
            writeEntry(output, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nMulti-Release: true\r\n\r\n"
                    .getBytes(StandardCharsets.UTF_8));
            writeEntry(output, "JarCompactorTest.class", classResource.readAllBytes());
            writeEntry(output, "META-INF/PLUGIN.SF", "Signature-Version: 1.0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            writeEntry(output, "META-INF/PLUGIN.RSA", new byte[]{1, 2, 3, 4});
            writeEntry(output, "META-INF/plugin/runtime.revision", "registry fixture".getBytes(StandardCharsets.UTF_8));
            writeEntry(output, "example/caf\u00e9.txt", "unicode name".getBytes(StandardCharsets.UTF_8));
            for (int index = 0; index < 100; index++) {
                writeEntry(output, "example/item-" + index + ".txt", ("value-" + index).getBytes(StandardCharsets.UTF_8));
            }
        }
        Map<String, EntryIdentity> original = identity(artifact);
        long originalBytes = artifact.length();

        JarCompactor.compact(artifact, new CompactionOptions(Set.of(), false, false, false));

        assertEquals(original, identity(artifact));
        assertTrue(artifact.length() < originalBytes);
        try (JarFile jar = new JarFile(artifact, false)) {
            assertEquals("preserved archive comment", jar.getComment());
            assertEquals("true", jar.getManifest().getMainAttributes().getValue("Multi-Release"));
            assertEquals(0xCA, jar.getInputStream(jar.getJarEntry("JarCompactorTest.class")).read());
        }
        try (ZipFile zip = ZipFile.builder().setFile(artifact).get()) {
            Enumeration<ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                assertFalse(entries.nextElement().getGeneralPurposeBit().usesDataDescriptor());
            }
        }
        byte[] first = Files.readAllBytes(artifact.toPath());
        JarCompactor.compact(artifact, new CompactionOptions(Set.of(), false, false, false));
        assertArrayEquals(first, Files.readAllBytes(artifact.toPath()));
    }

    @Test
    public void rejectsMalformedArchivesWithoutReplacingTheSource() throws Exception {
        File artifact = temporaryFolder.resolve("invalid.jar").toFile();
        byte[] original = "not a zip archive".getBytes(StandardCharsets.UTF_8);
        Files.write(artifact.toPath(), original);

        assertThrows(IOException.class, () -> JarCompactor.compact(artifact, new CompactionOptions(Set.of(), false, false, false)));

        assertArrayEquals(original, Files.readAllBytes(artifact.toPath()));
        assertEquals(1, temporaryFolder.toFile().listFiles().length);
    }

    @Test
    public void rejectsCorruptPayloadWithoutReplacingTheSource() throws Exception {
        File artifact = temporaryFolder.resolve("corrupt.jar").toFile();
        byte[] payload = "checked-payload".getBytes(StandardCharsets.UTF_8);
        CRC32 crc = new CRC32();
        crc.update(payload);
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(artifact))) {
            JarEntry entry = new JarEntry("value.txt");
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(payload.length);
            entry.setCrc(crc.getValue());
            output.putNextEntry(entry);
            output.write(payload);
            output.closeEntry();
        }
        byte[] corrupt = Files.readAllBytes(artifact.toPath());
        int payloadOffset = new String(corrupt, StandardCharsets.ISO_8859_1).indexOf("checked-payload");
        assertTrue(payloadOffset > 0);
        corrupt[payloadOffset] ^= 1;
        Files.write(artifact.toPath(), corrupt);

        assertThrows(IOException.class, () -> JarCompactor.compact(artifact, new CompactionOptions(Set.of(), false, false, false)));
        assertThrows(IOException.class, () -> JarCompactor.compact(artifact, new CompactionOptions(Set.of("value.txt"), false, false, false)));

        assertArrayEquals(corrupt, Files.readAllBytes(artifact.toPath()));
    }

    @Test
    public void rejectsDuplicateEntriesWithoutReplacingTheSource() throws Exception {
        File artifact = temporaryFolder.resolve("duplicate.jar").toFile();
        try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(artifact)) {
            for (int index = 0; index < 2; index++) {
                output.putArchiveEntry(new ZipArchiveEntry("duplicate.txt"));
                output.write(index);
                output.closeArchiveEntry();
            }
        }
        byte[] original = Files.readAllBytes(artifact.toPath());

        assertThrows(IOException.class, () -> JarCompactor.compact(artifact, new CompactionOptions(Set.of(), false, false, false)));

        assertArrayEquals(original, Files.readAllBytes(artifact.toPath()));
    }

    @Test
    public void removesOnlyRequestedEntriesAndPreservesOrder() throws Exception {
        File artifact = temporaryFolder.resolve("removals.jar").toFile();
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(artifact))) {
            writeEntry(output, "retained/", new byte[0]);
            writeEntry(output, "retained/data.txt", new byte[]{3, 2, 1});
            writeEntry(output, "retained/Unused.class", new byte[8192]);
            writeEntry(output, "retained/Used.class", new byte[]{1, 2, 3});
        }

        JarCompactor.compact(artifact, new CompactionOptions(Set.of("retained/Unused.class"), false, false, false));

        try (JarFile archive = new JarFile(artifact)) {
            Enumeration<JarEntry> entries = archive.entries();
            assertEquals("retained/", entries.nextElement().getName());
            assertEquals("retained/data.txt", entries.nextElement().getName());
            assertEquals("retained/Used.class", entries.nextElement().getName());
            assertFalse(entries.hasMoreElements());
            assertNull(archive.getJarEntry("retained/Unused.class"));
        }
    }

    @Test
    public void stripsOnlyDirectoriesWhenEnabled() throws Exception {
        File artifact = temporaryFolder.resolve("stripped.jar").toFile();
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(artifact))) {
            writeEntry(output, "META-INF/", new byte[0]);
            writeEntry(output, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\n\r\n"
                    .getBytes(StandardCharsets.UTF_8));
            writeEntry(output, "retained/", new byte[0]);
            writeEntry(output, "retained/data.txt", new byte[]{3, 2, 1});
            writeEntry(output, "retained/Used.class", new byte[]{1, 2, 3});
        }
        Map<String, EntryIdentity> expected = identity(artifact);
        expected.keySet().removeIf(name -> name.endsWith("/"));

        JarCompactor.compact(artifact, new CompactionOptions(Set.of(), true, false, false));

        assertEquals(expected, identity(artifact));
        try (JarFile archive = new JarFile(artifact)) {
            assertNull(archive.getJarEntry("META-INF/"));
            assertNull(archive.getJarEntry("retained/"));
            assertNotNull(archive.getManifest());
        }
    }

    @Test
    public void rejectsDirectoryRemovalWithoutReplacingTheSource() throws Exception {
        File artifact = temporaryFolder.resolve("directory.jar").toFile();
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(artifact))) {
            writeEntry(output, "retained/", new byte[0]);
        }
        byte[] original = Files.readAllBytes(artifact.toPath());

        assertThrows(IOException.class, () -> JarCompactor.compact(artifact, new CompactionOptions(Set.of("retained/"), false, false, false)));
        assertThrows(IOException.class, () -> JarCompactor.compact(artifact, new CompactionOptions(Set.of("retained/"), true, false, false)));

        assertArrayEquals(original, Files.readAllBytes(artifact.toPath()));
    }

    @Test
    public void leavesAlreadyCompactArchiveUntouched() throws Exception {
        File artifact = temporaryFolder.resolve("compact.jar").toFile();
        try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(artifact)) {
            output.setLevel(9);
            ZipArchiveEntry entry = new ZipArchiveEntry("value.txt");
            entry.setTime(315_532_800_000L);
            output.putArchiveEntry(entry);
            output.write(new byte[4096]);
            output.closeArchiveEntry();
        }
        byte[] original = Files.readAllBytes(artifact.toPath());
        Files.setLastModifiedTime(artifact.toPath(), FileTime.fromMillis(42_000L));

        JarCompactor.compact(artifact, new CompactionOptions(Set.of(), false, false, false));

        assertArrayEquals(original, Files.readAllBytes(artifact.toPath()));
        assertEquals(42_000L, Files.getLastModifiedTime(artifact.toPath()).toMillis());
    }

    @Test
    public void leavesArchiveUntouchedWhenCompressionWouldIncreaseItsSize() throws Exception {
        File artifact = temporaryFolder.resolve("stored.jar").toFile();
        try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(artifact)) {
            ZipArchiveEntry entry = new ZipArchiveEntry("value.txt");
            entry.setMethod(ZipEntry.STORED);
            entry.setTime(315_532_800_000L);
            output.putArchiveEntry(entry);
            output.write(1);
            output.closeArchiveEntry();
        }
        byte[] original = Files.readAllBytes(artifact.toPath());

        JarCompactor.compact(artifact, new CompactionOptions(Set.of(), false, false, false));

        assertArrayEquals(original, Files.readAllBytes(artifact.toPath()));
    }

    @Test
    public void stripsOnlyLocalMetadataWithBothCompressionModes() throws Exception {
        byte[] originalClass = LocalVariableStripperTest.fixture(true);
        byte[] noLocals = LocalVariableStripperTest.fixture(false);
        byte[] resource = new byte[]{0, 1, 2, 3, (byte) 255};
        for (boolean release : new boolean[]{false, true}) {
            File artifact = temporaryFolder.resolve("locals-" + release + ".jar").toFile();
            try (JarOutputStream output = new JarOutputStream(new FileOutputStream(artifact))) {
                writeEntry(output, "example/", new byte[0]);
                writeEntry(output, "example/Fixture.class", originalClass);
                writeEntry(output, "unchanged/Fixture.class", noLocals);
                writeEntry(output, "example/resource.bin", resource);
            }

            JarCompactor.compact(artifact, new CompactionOptions(Set.of(), false, true, release));

            try (JarFile archive = new JarFile(artifact)) {
                assertNotNull(archive.getJarEntry("example/"));
                assertArrayEquals(LocalVariableStripper.strip(originalClass),
                        archive.getInputStream(archive.getJarEntry("example/Fixture.class")).readAllBytes());
                assertArrayEquals(noLocals,
                        archive.getInputStream(archive.getJarEntry("unchanged/Fixture.class")).readAllBytes());
                assertArrayEquals(resource,
                        archive.getInputStream(archive.getJarEntry("example/resource.bin")).readAllBytes());
            }
            try (ZipFile archive = ZipFile.builder().setFile(artifact).get()) {
                Enumeration<ZipArchiveEntry> entries = archive.getEntries();
                while (entries.hasMoreElements()) {
                    assertFalse(entries.nextElement().getGeneralPurposeBit().usesDataDescriptor());
                }
            }
            byte[] first = Files.readAllBytes(artifact.toPath());
            JarCompactor.compact(artifact, new CompactionOptions(Set.of(), false, true, release));
            assertArrayEquals(first, Files.readAllBytes(artifact.toPath()));
        }
    }

    @Test
    public void preservesOriginalArchiveWhenClassMetadataCannotBeRewritten() throws Exception {
        File artifact = temporaryFolder.resolve("unknown-attribute.jar").toFile();
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(artifact))) {
            writeEntry(output, "example/Fixture.class", LocalVariableStripperTest.fixture(true, "class"));
        }
        byte[] original = Files.readAllBytes(artifact.toPath());

        assertThrows(IOException.class,
                () -> JarCompactor.compact(artifact, new CompactionOptions(Set.of(), false, true, false)));

        assertArrayEquals(original, Files.readAllBytes(artifact.toPath()));
        assertEquals(1, temporaryFolder.toFile().listFiles().length);
    }

    @Test
    public void rejectsCorruptClassBeforeMetadataTransformation() throws Exception {
        File artifact = temporaryFolder.resolve("corrupt-class.jar").toFile();
        byte[] payload = LocalVariableStripperTest.fixture(true);
        CRC32 crc = new CRC32();
        crc.update(payload);
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(artifact))) {
            JarEntry entry = new JarEntry("example/Fixture.class");
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(payload.length);
            entry.setCrc(crc.getValue());
            output.putNextEntry(entry);
            output.write(payload);
            output.closeEntry();
        }
        byte[] corrupt = Files.readAllBytes(artifact.toPath());
        int offset = new String(corrupt, StandardCharsets.ISO_8859_1).indexOf("unusedDebuggerLocalName");
        assertTrue(offset > 0);
        corrupt[offset] ^= 1;
        Files.write(artifact.toPath(), corrupt);

        IOException failure = assertThrows(IOException.class,
                () -> JarCompactor.compact(artifact, new CompactionOptions(Set.of(), false, true, true)));

        assertTrue(failure.getMessage().contains("CRC"));
        assertArrayEquals(corrupt, Files.readAllBytes(artifact.toPath()));
    }

    private static void writeEntry(JarOutputStream output, String name, byte[] content) throws Exception {
        JarEntry entry = new JarEntry(name);
        entry.setTime(315_532_800_000L);
        entry.setComment("preserved entry comment");
        entry.setExtra(new byte[]{(byte) 0xFE, (byte) 0xCA, 1, 0, 7});
        output.putNextEntry(entry);
        output.write(content);
        output.closeEntry();
    }

    private static Map<String, EntryIdentity> identity(File artifact) throws Exception {
        Map<String, EntryIdentity> entries = new TreeMap<>();
        try (JarFile jar = new JarFile(artifact, false)) {
            Enumeration<JarEntry> contents = jar.entries();
            while (contents.hasMoreElements()) {
                JarEntry entry = contents.nextElement();
                byte[] bytes;
                try (InputStream input = jar.getInputStream(entry)) {
                    bytes = input.readAllBytes();
                }
                entries.put(entry.getName(), new EntryIdentity(
                        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                        entry.getSize(), entry.getCrc(), entry.getTime(), entry.getComment(),
                        entry.getExtra() == null ? "" : HexFormat.of().formatHex(entry.getExtra())));
            }
        }
        return entries;
    }

    private record EntryIdentity(String sha256, long size, long crc, long time, String comment, String extra) {
    }
}

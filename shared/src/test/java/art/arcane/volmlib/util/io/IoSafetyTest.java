package art.arcane.volmlib.util.io;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

public class IoSafetyTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void recursiveDeleteDoesNotFollowDirectorySymlinks() throws Exception {
        File outside = temporaryFolder.newFolder("outside");
        File retained = new File(outside, "retained.txt");
        Files.writeString(retained.toPath(), "retained", StandardCharsets.UTF_8);
        File managed = temporaryFolder.newFolder("managed");
        File link = new File(managed, "link");
        Files.createSymbolicLink(link.toPath(), outside.toPath());

        IO.delete(managed);

        assertFalse(managed.exists());
        assertTrue(retained.isFile());
    }

    @Test
    public void boundedExtractionRejectsOversizedEntries() throws Exception {
        File archive = temporaryFolder.newFile("oversized.zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("data/value.txt"));
            zip.write("oversized".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        File destination = temporaryFolder.newFolder("destination");

        try {
            ZipUtils.unzipFile(archive, destination, 10, 1024, 4);
            fail("Expected oversized entry rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("entry exceeds"));
        }
        assertFalse(new File(destination, "data/value.txt").exists());
        assertFalse(new File(destination, "data").exists());
    }

    @Test
    public void boundedExtractionAcceptsExactLimits() throws Exception {
        File archive = temporaryFolder.newFile("exact-limits.zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("data/"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("data/value.txt"));
            zip.write("value".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        File destination = temporaryFolder.newFolder("exact-limits-destination");

        ZipUtils.unzipFile(archive, destination, 2, 5, 5);

        assertEquals(
                "value",
                Files.readString(new File(destination, "data/value.txt").toPath(), StandardCharsets.UTF_8)
        );
        try (Stream<Path> files = Files.walk(destination.toPath())) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".iris-extract-")));
        }
    }

    @Test
    public void boundedExtractionCountsDirectoryEntryPayloads() throws Exception {
        File archive = temporaryFolder.newFile("directory-payload.zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("data/"));
            zip.write("payload".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        File destination = temporaryFolder.newFolder("directory-payload-destination");

        try {
            ZipUtils.unzipFile(archive, destination, 10, 1024, 1024);
            fail("Expected directory payload rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("directory entry contains data"));
        }
        assertFalse(new File(destination, "data").exists());
    }

    @Test
    public void boundedExtractionRejectsOneEntryCreatingTooManyDirectories() throws Exception {
        File archive = temporaryFolder.newFile("deep-entry.zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("one/two/three/value.txt"));
            zip.write("value".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        File destination = temporaryFolder.newFolder("deep-entry-destination");

        try {
            ZipUtils.unzipFile(archive, destination, 2, 1024, 1024);
            fail("Expected deep directory creation rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("creates more than 2 directories"));
        }
        assertFalse(new File(destination, "one").exists());
    }

    @Test
    public void boundedExtractionRejectsAggregateImplicitDirectoryOverflowAndRollsBack() throws Exception {
        File archive = temporaryFolder.newFile("aggregate-directories.zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("one/value.txt"));
            zip.write("first".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("two/three/value.txt"));
            zip.write("second".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        File destination = temporaryFolder.newFolder("aggregate-directories-destination");

        try {
            ZipUtils.unzipFile(archive, destination, 2, 1024, 1024);
            fail("Expected aggregate directory creation rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("creates more than 2 directories"));
        }
        assertFalse(new File(destination, "one").exists());
        assertFalse(new File(destination, "two").exists());
    }

    @Test
    public void boundedExtractionAcceptsExactImplicitDirectoryLimit() throws Exception {
        File archive = temporaryFolder.newFile("exact-directories.zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("one/two/value.txt"));
            zip.write("value".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        File destination = temporaryFolder.newFolder("exact-directories-destination");

        ZipUtils.unzipFile(archive, destination, 2, 1024, 1024);

        assertEquals("value", Files.readString(new File(destination, "one/two/value.txt").toPath()));
    }

    @Test
    public void publicationMetadataFailureDoesNotMaskTheFailureOrLeaveThePublishedFile() throws Exception {
        File archive = temporaryFolder.newFile("metadata-failure.zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("value.txt"));
            zip.write("value".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        File destination = temporaryFolder.newFolder("metadata-failure-destination");
        Path published = destination.toPath().toRealPath().resolve("value.txt");
        AtomicBoolean injected = new AtomicBoolean();

        try (MockedStatic<ZipUtils> zipUtils = mockStatic(ZipUtils.class, CALLS_REAL_METHODS)) {
            zipUtils.when(() -> ZipUtils.readBasicAttributes(published)).thenAnswer(invocation -> {
                if (injected.compareAndSet(false, true)) {
                    throw new IOException("Injected publication metadata failure");
                }
                return invocation.callRealMethod();
            });

            try {
                ZipUtils.unzipFile(archive, destination, 2, 1024, 1024);
                fail("Expected publication metadata failure");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("Injected publication metadata failure"));
            }
        }

        assertTrue(injected.get());
        assertFalse(Files.exists(published));
    }

    @Test
    public void boundedExtractionRejectsAggregateOverflowAndRollsBack() throws Exception {
        File archive = temporaryFolder.newFile("aggregate-overflow.zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("first.txt"));
            zip.write("1234".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("second.txt"));
            zip.write("5678".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        File destination = temporaryFolder.newFolder("aggregate-overflow-destination");

        try {
            ZipUtils.unzipFile(archive, destination, 10, 7, 4);
            fail("Expected aggregate size rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("extraction limits")
                    || expected.getMessage().contains("expands beyond"));
        }
        assertFalse(new File(destination, "first.txt").exists());
        assertFalse(new File(destination, "second.txt").exists());
    }

    @Test
    public void extractionDoesNotTraverseSymlinkParents() throws Exception {
        File archive = temporaryFolder.newFile("symlink-parent.zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("sub/escape.txt"));
            zip.write("escape".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        File destination = temporaryFolder.newFolder("symlink-destination");
        File outside = temporaryFolder.newFolder("symlink-outside");
        Files.createSymbolicLink(new File(destination, "sub").toPath(), outside.toPath());

        try {
            ZipUtils.unzipFile(archive, destination, 10, 1024, 1024);
            fail("Expected symlink-parent extraction to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("symbolic link")
                    || expected.getMessage().contains("outside of the target dir"));
        }
        assertFalse(new File(outside, "escape.txt").exists());
    }

    @Test
    public void extractionRequiresRegularSourceAndDirectoryTarget() throws Exception {
        File archive = temporaryFolder.newFile("regular-source.zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("value.txt"));
            zip.write("value".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        File linkedArchive = new File(temporaryFolder.getRoot(), "linked-source.zip");
        Files.createSymbolicLink(linkedArchive.toPath(), archive.toPath());
        File destination = temporaryFolder.newFolder("regular-source-destination");

        try {
            ZipUtils.unzipFile(linkedArchive, destination, 10, 1024, 1024);
            fail("Expected symbolic-link archive rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("regular file"));
        }

        File invalidTarget = temporaryFolder.newFile("invalid-extraction-target");
        try {
            ZipUtils.unzipFile(archive, invalidTarget, 10, 1024, 1024);
            fail("Expected non-directory target rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("must be a directory"));
        }
        assertEquals(0, Files.size(invalidTarget.toPath()));
    }

    @Test
    public void extractionRejectsEntryThatReplacesSourceArchive() throws Exception {
        File destination = temporaryFolder.newFolder("source-alias-destination");
        File archive = new File(destination, "archive.zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("archive.zip"));
            zip.write("replacement".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        long originalSize = archive.length();

        try {
            ZipUtils.unzipFile(archive, destination, 10, 1024, 1024);
            fail("Expected source archive replacement rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("source archive"));
        }
        assertTrue(archive.isFile());
        assertEquals(originalSize, archive.length());
    }

    @Test
    public void extractionRejectsNormalizedDuplicateEntries() throws Exception {
        File archive = temporaryFolder.newFile("normalized-duplicates.zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("nested/../value.txt"));
            zip.write("first".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("value.txt"));
            zip.write("second".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        File destination = temporaryFolder.newFolder("normalized-duplicates-destination");

        try {
            ZipUtils.unzipFile(archive, destination, 10, 1024, 1024);
            fail("Expected normalized duplicate rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("duplicate entry"));
        }
        assertFalse(new File(destination, "value.txt").exists());
    }

    @Test
    public void extractionRejectsFilesystemAliasedDuplicateEntries() throws Exception {
        File destination = temporaryFolder.newFolder("filesystem-alias-destination");
        Path probe = destination.toPath().resolve("AliasProbe");
        Files.writeString(probe, "probe", StandardCharsets.UTF_8);
        Path probeAlias = destination.toPath().resolve("aliasprobe");
        boolean aliasesByCase = Files.exists(probeAlias) && Files.isSameFile(probe, probeAlias);
        Files.delete(probe);

        File archive = temporaryFolder.newFile("filesystem-alias.zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("Value.txt"));
            zip.write("first".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("value.txt"));
            zip.write("second".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        if (aliasesByCase) {
            try {
                ZipUtils.unzipFile(archive, destination, 10, 1024, 1024);
                fail("Expected filesystem-aliased duplicate rejection");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("duplicate entry"));
            }
            assertFalse(new File(destination, "Value.txt").exists());
            return;
        }

        ZipUtils.unzipFile(archive, destination, 10, 1024, 1024);
        assertEquals("first", Files.readString(new File(destination, "Value.txt").toPath()));
        assertEquals("second", Files.readString(new File(destination, "value.txt").toPath()));
    }

    @Test
    public void failedExtractionRestoresOverwrittenFiles() throws Exception {
        File archive = temporaryFolder.newFile("rollback.zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("existing.txt"));
            zip.write("replacement".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("oversized.txt"));
            zip.write("too-large".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        File destination = temporaryFolder.newFolder("rollback-destination");
        File existing = new File(destination, "existing.txt");
        Files.writeString(existing.toPath(), "original", StandardCharsets.UTF_8);

        try {
            ZipUtils.unzipFile(archive, destination, 10, 1024, 8);
            fail("Expected oversized entry rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("entry exceeds"));
        }

        assertEquals("original", Files.readString(existing.toPath(), StandardCharsets.UTF_8));
        assertFalse(new File(destination, "oversized.txt").exists());
        try (Stream<Path> files = Files.list(destination.toPath())) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".iris-extract-")));
        }
    }

    @Test
    public void successfulExtractionCommitsOverwrittenFilesAndRemovesBackups() throws Exception {
        File archive = temporaryFolder.newFile("overwrite-success.zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("existing.txt"));
            zip.write("replacement".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        File destination = temporaryFolder.newFolder("overwrite-success-destination");
        File existing = new File(destination, "existing.txt");
        Files.writeString(existing.toPath(), "original", StandardCharsets.UTF_8);

        ZipUtils.unzipFile(archive, destination, 10, 1024, 1024);

        assertEquals("replacement", Files.readString(existing.toPath(), StandardCharsets.UTF_8));
        try (Stream<Path> files = Files.list(destination.toPath())) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".iris-extract-")));
        }
    }

    @Test
    public void directoryCopyRejectsOverlappingTargets() throws Exception {
        File source = temporaryFolder.newFolder("copy-overlap-source");
        Files.writeString(new File(source, "value.txt").toPath(), "value", StandardCharsets.UTF_8);
        File nestedTarget = new File(source, "nested");

        try {
            IO.copyDirectory(source.toPath(), nestedTarget.toPath());
            fail("Expected an overlapping directory copy to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("cannot overlap"));
        }
        assertFalse(nestedTarget.exists());
    }

    @Test
    public void directoryCopyCopiesRegularTrees() throws Exception {
        File source = temporaryFolder.newFolder("copy-regular-source");
        File sourceDirectory = new File(source, "data");
        assertTrue(sourceDirectory.mkdir());
        Files.writeString(new File(sourceDirectory, "value.txt").toPath(), "value", StandardCharsets.UTF_8);
        File target = new File(temporaryFolder.getRoot(), "copy-regular-target");

        IO.copyDirectory(source.toPath(), target.toPath());

        assertEquals(
                "value",
                Files.readString(new File(target, "data/value.txt").toPath(), StandardCharsets.UTF_8)
        );
        try (Stream<Path> files = Files.walk(target.toPath())) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().contains("volmlib-copy")));
        }
    }

    @Test
    public void directoryCopyRejectsSymlinkAliasedOverlappingTargets() throws Exception {
        File source = temporaryFolder.newFolder("copy-aliased-overlap-source");
        Files.writeString(new File(source, "value.txt").toPath(), "value", StandardCharsets.UTF_8);
        Path alias = temporaryFolder.getRoot().toPath().resolve("copy-source-alias");
        Files.createSymbolicLink(alias, source.toPath());
        Path nestedTarget = alias.resolve("nested");

        try {
            IO.copyDirectory(source.toPath(), nestedTarget);
            fail("Expected a symlink-aliased overlap to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("cannot overlap"));
        }
        assertFalse(new File(source, "nested").exists());
    }

    @Test
    public void directoryCopyRejectsSymbolicLinkEntries() throws Exception {
        File source = temporaryFolder.newFolder("copy-symlink-source");
        File outside = temporaryFolder.newFile("copy-symlink-outside");
        Files.writeString(outside.toPath(), "outside", StandardCharsets.UTF_8);
        Files.createSymbolicLink(new File(source, "linked.txt").toPath(), outside.toPath());
        File target = new File(temporaryFolder.getRoot(), "copy-symlink-target");

        try {
            IO.copyDirectory(source.toPath(), target.toPath());
            fail("Expected a symbolic-link source entry to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("symbolic link"));
        }
        assertFalse(new File(target, "linked.txt").exists());
        assertEquals("outside", Files.readString(outside.toPath(), StandardCharsets.UTF_8));
    }
}

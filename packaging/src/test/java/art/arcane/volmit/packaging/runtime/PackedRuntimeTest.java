package art.arcane.volmit.packaging.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZOutputStream;
import org.tukaani.xz.XZInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

public class PackedRuntimeTest {
    @TempDir
    Path directory;

    @Test
    public void extractsAndVerifiesTheExactPayload() throws Exception {
        byte[] content = "Plugin runtime contents".getBytes(StandardCharsets.UTF_8);
        String hash = hash(content);
        Path target = directory.resolve("runtime/" + hash + ".jar");
        PackedRuntime.extract(new ByteArrayInputStream(compress(content)), target, hash);
        PackedRuntime.verify(target, hash);
        assertArrayEquals(content, Files.readAllBytes(target));
    }

    @Test
    public void rejectsAnIncorrectHashWithoutPublishingOrLeavingTemporaryFiles() throws Exception {
        byte[] content = "Plugin runtime contents".getBytes(StandardCharsets.UTF_8);
        Path target = directory.resolve("runtime/runtime.jar");
        assertThrows(IOException.class, () -> PackedRuntime.extract(
                new ByteArrayInputStream(compress(content)), target, "0".repeat(64)));
        assertFalse(Files.exists(target));
        try (Stream<Path> files = Files.list(target.getParent())) {
            assertEquals(0L, files.count());
        }
    }

    @Test
    public void rejectsTruncatedCompressedData() throws Exception {
        byte[] content = "Plugin runtime contents".getBytes(StandardCharsets.UTF_8);
        byte[] compressed = compress(content);
        Path target = directory.resolve("runtime/runtime.jar");
        assertThrows(IOException.class, () -> PackedRuntime.extract(
                new ByteArrayInputStream(compressed, 0, compressed.length / 2), target, hash(content)));
        assertFalse(Files.exists(target));
    }

    @Test
    public void detectsModifiedCachedRuntime() throws Exception {
        byte[] content = "Plugin runtime contents".getBytes(StandardCharsets.UTF_8);
        Path target = directory.resolve("runtime.jar");
        Files.writeString(target, "changed");
        assertThrows(IOException.class, () -> PackedRuntime.verify(target, hash(content)));
    }

    @Test
    public void nativeArchiveReplacementKeepsClassIdentityAndClosesOwnedHandles() throws Exception {
        Path outer = createArchive("outer.jar", false);
        Path payload = createArchive("payload.jar", true);
        JarFile original = new JarFile(outer.toFile());
        PackedRuntime.RuntimeArchive replacement;
        try (ArchiveLoader loader = new ArchiveLoader(original)) {
            assertThrows(ClassNotFoundException.class, () -> loader.loadClass(Fixture.class.getName()));
            replacement = PackedRuntime.replaceArchive(loader, ArchiveLoader.class.getDeclaredField("jar"), payload, true);
            Class<?> loaded = loader.loadClass(Fixture.class.getName());
            assertSame(loader, loaded.getClassLoader());
            assertEquals("runtime", loaded.getMethod("value").invoke(null));
            assertEquals(1, loader.definitions);
        }
        assertThrows(IllegalStateException.class, original::size);
        assertThrows(IllegalStateException.class, replacement::size);
    }

    @Test
    public void temporaryArchiveCloseLeavesTheSharedOriginalOpen() throws Exception {
        Path outer = createArchive("outer.jar", false);
        Path payload = createArchive("payload.jar", true);
        try (JarFile original = new JarFile(outer.toFile());
             ArchiveLoader loader = new ArchiveLoader(original)) {
            PackedRuntime.RuntimeArchive replacement = PackedRuntime.replaceArchive(
                    loader, ArchiveLoader.class.getDeclaredField("jar"), payload, false);
            replacement.close();
            assertEquals(0, original.size());
            assertThrows(IllegalStateException.class, replacement::size);
        }
    }

    @Test
    public void appendedRuntimeUsesTheExistingUrlClassLoader() throws Exception {
        Path payload = createArchive("payload.jar", true);
        try (URLClassLoader loader = new URLClassLoader(new URL[0], ClassLoader.getPlatformClassLoader())) {
            assertThrows(ClassNotFoundException.class, () -> loader.loadClass(Fixture.class.getName()));
            PackedRuntime.appendArchive(loader, payload);
            Class<?> loaded = loader.loadClass(Fixture.class.getName());
            assertSame(loader, loaded.getClassLoader());
            assertEquals("runtime", loaded.getMethod("value").invoke(null));
            assertEquals(payload.toUri().toURL(), loaded.getProtectionDomain().getCodeSource().getLocation());
        }
    }

    @Test
    public void rejectsCachedFilesAboveTheSizeLimit() throws Exception {
        Path payload = directory.resolve("oversized.jar");
        try (RandomAccessFile file = new RandomAccessFile(payload.toFile(), "rw")) {
            file.setLength(256L * 1024 * 1024 + 1);
        }
        IOException failure = assertThrows(IOException.class, () -> PackedRuntime.verify(payload, "0".repeat(64)));
        assertEquals("Embedded runtime exceeds the size limit", failure.getMessage());
    }

    @Test
    public void rejectsExpandedPayloadAboveTheSizeLimitWithoutPublishing() throws Exception {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (XZOutputStream output = new XZOutputStream(compressed, new LZMA2Options(0))) {
            byte[] zeros = new byte[1024 * 1024];
            for (int block = 0; block < 257; block++) {
                output.write(zeros);
            }
        }
        Path target = directory.resolve("runtime/runtime.jar");
        IOException failure = assertThrows(IOException.class, () -> PackedRuntime.extract(
                new ByteArrayInputStream(compressed.toByteArray()), target, "0".repeat(64)));
        assertEquals("Embedded runtime exceeds the size limit", failure.getMessage());
        assertFalse(Files.exists(target));
        try (Stream<Path> files = Files.list(target.getParent())) {
            assertEquals(0, files.count());
        }
    }

    @Test
    public void failedExtractionPreservesExistingPayload() throws Exception {
        Path target = directory.resolve("runtime/runtime.jar");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "original");
        assertThrows(IOException.class, () -> PackedRuntime.extract(
                new ByteArrayInputStream(compress("replacement".getBytes(StandardCharsets.UTF_8))), target, "0".repeat(64)));
        assertEquals("original", Files.readString(target));
    }

    @Test
    public void verificationDoesNotRewriteAValidCache() throws Exception {
        byte[] contents = "Plugin runtime contents".getBytes(StandardCharsets.UTF_8);
        Path target = directory.resolve("runtime.jar");
        Files.write(target, contents);
        Files.setLastModifiedTime(target, FileTime.fromMillis(1000000));
        PackedRuntime.verify(target, hash(contents));
        assertEquals(1000000, Files.getLastModifiedTime(target).toMillis());
    }

    @Test
    public void remappedJarUsesTheNormalPluginCache() {
        Path plugins = directory.resolve("plugins");
        assertEquals(plugins.resolve("Example/cache/runtime"),
                PackedRuntime.cacheDirectory(plugins.resolve(".paper-remapped/example.jar"), "Example"));
        assertEquals(plugins.resolve("Example/cache/runtime"),
                PackedRuntime.cacheDirectory(plugins.resolve("example.jar"), "Example"));
    }

    @Test
    public void installsFromItsOwnArchiveAndReusesVerifiedCache() throws Exception {
        Path payload = createArchive("payload.jar", true);
        byte[] contents = Files.readAllBytes(payload);
        Path outer = directory.resolve("example.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(outer))) {
            addRuntimeClasses(output, PackedRuntime.class);
            Properties metadata = new Properties();
            metadata.setProperty("pluginName", "Example");
            metadata.setProperty("sha256", hash(contents));
            output.putNextEntry(new JarEntry("META-INF/volmit/runtime.properties"));
            metadata.store(output, null);
            output.closeEntry();
            output.putNextEntry(new JarEntry("META-INF/volmit/runtime.jar.xz"));
            output.write(compress(contents));
            output.closeEntry();
        }
        Path extracted = directory.resolve("Example/cache/runtime/" + hash(contents) + ".jar");
        URL decoder = XZInputStream.class.getProtectionDomain().getCodeSource().getLocation();
        for (int start = 0; start < 2; start++) {
            try (URLClassLoader loader = new URLClassLoader(new URL[]{outer.toUri().toURL(), decoder}, ClassLoader.getPlatformClassLoader())) {
                Class<?> runtime = loader.loadClass(PackedRuntime.class.getName());
                runtime.getMethod("install").invoke(null);
                assertEquals(extracted.toFile(), runtime.getMethod("runtimeJar").invoke(null));
                Class<?> loaded = loader.loadClass(Fixture.class.getName());
                assertSame(loader, loaded.getClassLoader());
                assertEquals("runtime", loaded.getMethod("value").invoke(null));
            }
            if (start == 0) {
                Files.setLastModifiedTime(extracted, FileTime.fromMillis(1000000));
            }
        }
        assertEquals(1000000, Files.getLastModifiedTime(extracted).toMillis());
        assertArrayEquals(contents, Files.readAllBytes(extracted));
    }

    private void addRuntimeClasses(JarOutputStream output, Class<?> type) throws IOException {
        String entry = type.getName().replace('.', '/') + ".class";
        output.putNextEntry(new JarEntry(entry));
        try (InputStream input = type.getResourceAsStream("/" + entry)) {
            input.transferTo(output);
        }
        output.closeEntry();
        for (Class<?> nested : type.getDeclaredClasses()) {
            addRuntimeClasses(output, nested);
        }
    }

    private Path createArchive(String name, boolean withFixture) throws IOException {
        Path path = directory.resolve(name);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
            if (withFixture) {
                String entry = Fixture.class.getName().replace('.', '/') + ".class";
                output.putNextEntry(new JarEntry(entry));
                try (InputStream bytes = getClass().getClassLoader().getResourceAsStream(entry)) {
                    bytes.transferTo(output);
                }
                output.closeEntry();
            }
        }
        return path;
    }

    private static byte[] compress(byte[] content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (XZOutputStream output = new XZOutputStream(bytes, new LZMA2Options(1))) {
            output.write(content);
        }
        return bytes.toByteArray();
    }

    private static String hash(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    public static class Fixture {
        public static String value() {
            return "runtime";
        }
    }

    private static final class ArchiveLoader extends URLClassLoader {
        private final JarFile jar;
        private int definitions;

        private ArchiveLoader(JarFile jar) {
            super(new URL[0], ClassLoader.getPlatformClassLoader());
            this.jar = jar;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            JarEntry entry = jar.getJarEntry(name.replace('.', '/') + ".class");
            if (entry == null) {
                throw new ClassNotFoundException(name);
            }
            try (InputStream input = jar.getInputStream(entry)) {
                byte[] bytes = input.readAllBytes();
                definitions++;
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException failure) {
                throw new ClassNotFoundException(name, failure);
            }
        }

        @Override
        public void close() throws IOException {
            try (jar) {
                super.close();
            }
        }
    }
}

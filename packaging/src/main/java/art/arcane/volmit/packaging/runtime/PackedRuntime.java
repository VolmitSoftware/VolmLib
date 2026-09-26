package art.arcane.volmit.packaging.runtime;

import org.tukaani.xz.XZInputStream;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;

public final class PackedRuntime {
    private static final long MAXIMUM_BYTES = 256L * 1024 * 1024;
    private static final int DECODER_MEMORY_KIB = 128 * 1024;
    private static final String METADATA = "META-INF/volmit/runtime.properties";
    private static final String PAYLOAD = "META-INF/volmit/runtime.jar.xz";
    private static Path runtime;
    private static RuntimeArchive archive;
    private static Field archiveField;

    private PackedRuntime() {
    }

    public static synchronized void install() {
        if (runtime != null) {
            return;
        }
        try {
            Path pluginJar = Path.of(PackedRuntime.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path payload;
            String pluginName;
            try (JarFile source = new JarFile(pluginJar.toFile())) {
                Properties metadata = metadata(source);
                pluginName = metadata.getProperty("pluginName");
                String hash = metadata.getProperty("sha256");
                if (pluginName == null || !pluginName.matches("[A-Za-z0-9][A-Za-z0-9 _.-]*")) {
                    throw new IOException("Invalid embedded runtime plugin name");
                }
                if (hash == null || !hash.matches("[0-9a-f]{64}")) {
                    throw new IOException("Invalid embedded runtime checksum");
                }
                payload = cacheDirectory(pluginJar, pluginName).resolve(hash + ".jar");
                if (Files.exists(payload)) {
                    verify(payload, hash);
                } else {
                    try (InputStream input = resource(source, PAYLOAD)) {
                        extract(input, payload, hash);
                    }
                }
            }
            ClassLoader loader = PackedRuntime.class.getClassLoader();
            if (!(loader instanceof URLClassLoader urlLoader)) {
                throw new IllegalStateException("Unsupported plugin classloader: " + loader.getClass().getName());
            }
            appendArchive(urlLoader, payload);
            Class<?> owner = archiveOwner(loader);
            if (owner != null) {
                archiveField = owner.getDeclaredField("jar");
                archive = replaceArchive(loader, archiveField, payload, !temporaryLoader(loader));
            }
            runtime = payload;
            Logger.getLogger(pluginName).info("Loaded embedded XZ runtime: " + payload.getFileName());
        } catch (IOException | URISyntaxException | ReflectiveOperationException failure) {
            throw new IllegalStateException("Unable to load the embedded plugin runtime", failure);
        }
    }

    public static synchronized void releaseTemporary(Throwable failure) {
        ClassLoader loader = PackedRuntime.class.getClassLoader();
        if (!temporaryLoader(loader) || archive == null) {
            return;
        }
        RuntimeArchive released = archive;
        archive = null;
        try (released) {
            archiveField.set(loader, released.original);
        } catch (IOException | IllegalAccessException cleanupFailure) {
            if (failure != null) {
                failure.addSuppressed(cleanupFailure);
            } else {
                throw new IllegalStateException("Unable to close the temporary runtime archive", cleanupFailure);
            }
        }
    }

    public static File runtimeJar() {
        install();
        return runtime.toFile();
    }

    static void appendArchive(URLClassLoader loader, Path payload) throws ReflectiveOperationException, IOException {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field instance = unsafeType.getDeclaredField("theUnsafe");
        instance.setAccessible(true);
        Object unsafe = instance.get(null);
        Field trusted = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
        Object base = unsafeType.getMethod("staticFieldBase", Field.class).invoke(unsafe, trusted);
        long offset = (long) unsafeType.getMethod("staticFieldOffset", Field.class).invoke(unsafe, trusted);
        MethodHandles.Lookup lookup = (MethodHandles.Lookup) unsafeType.getMethod("getObject", Object.class, long.class)
                .invoke(unsafe, base, offset);
        MethodHandle append = lookup.findVirtual(URLClassLoader.class, "addURL", MethodType.methodType(void.class, URL.class));
        try {
            append.invokeExact(loader, payload.toUri().toURL());
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IOException("Unable to append the embedded runtime to the plugin classloader", failure);
        }
    }

    static RuntimeArchive replaceArchive(ClassLoader loader, Field field, Path payload, boolean closeOriginal)
            throws IOException, IllegalAccessException {
        if (field.getType() != JarFile.class || !field.getDeclaringClass().isInstance(loader)) {
            throw new IllegalArgumentException("Unsupported plugin archive field: " + field);
        }
        field.setAccessible(true);
        JarFile original = (JarFile) field.get(loader);
        RuntimeArchive replacement = new RuntimeArchive(new ArchiveOptions(payload, original, closeOriginal));
        try {
            field.set(loader, replacement);
        } catch (IllegalAccessException | RuntimeException failure) {
            try {
                replacement.closePayload();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
        return replacement;
    }

    static void extract(InputStream compressed, Path target, String expectedHash) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".runtime-", ".jar");
        try {
            MessageDigest digest = digest();
            try (InputStream input = new XZInputStream(compressed, DECODER_MEMORY_KIB);
                 OutputStream output = Files.newOutputStream(temporary)) {
                copyAndHash(input, output, digest);
            }
            requireHash(digest.digest(), expectedHash);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static void verify(Path payload, String expectedHash) throws IOException {
        if (Files.size(payload) > MAXIMUM_BYTES) {
            throw new IOException("Embedded runtime exceeds the size limit");
        }
        MessageDigest digest = digest();
        try (InputStream input = Files.newInputStream(payload)) {
            copyAndHash(input, OutputStream.nullOutputStream(), digest);
        }
        requireHash(digest.digest(), expectedHash);
    }

    static Path cacheDirectory(Path pluginJar, String pluginName) {
        Path directory = pluginJar.toAbsolutePath().getParent();
        if (directory.getFileName().toString().equals(".paper-remapped")) {
            directory = directory.getParent();
        }
        return directory.resolve(pluginName).resolve("cache/runtime");
    }

    private static Properties metadata(JarFile source) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = resource(source, METADATA)) {
            byte[] bytes = input.readNBytes(4097);
            if (bytes.length > 4096) {
                throw new IOException("Embedded runtime metadata exceeds the size limit");
            }
            properties.load(new ByteArrayInputStream(bytes));
        }
        return properties;
    }

    private static InputStream resource(JarFile source, String name) throws IOException {
        JarEntry entry = source.getJarEntry(name);
        if (entry == null) {
            throw new IOException("Missing embedded runtime resource: " + name);
        }
        return source.getInputStream(entry);
    }

    private static Class<?> archiveOwner(ClassLoader loader) {
        for (Class<?> type = loader.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals("org.bukkit.plugin.java.PluginClassLoader")
                    || type.getName().equals("io.papermc.paper.plugin.entrypoint.classloader.PaperSimplePluginClassLoader")) {
                return type;
            }
        }
        return null;
    }

    private static boolean temporaryLoader(ClassLoader loader) {
        return loader.getClass().getName()
                .equals("io.papermc.paper.plugin.entrypoint.classloader.PaperSimplePluginClassLoader");
    }

    private static void copyAndHash(InputStream input, OutputStream output, MessageDigest digest) throws IOException {
        byte[] buffer = new byte[65536];
        long size = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            size += count;
            if (size > MAXIMUM_BYTES) {
                throw new IOException("Embedded runtime exceeds the size limit");
            }
            digest.update(buffer, 0, count);
            output.write(buffer, 0, count);
        }
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private static void requireHash(byte[] actual, String expected) throws IOException {
        if (!HexFormat.of().formatHex(actual).equals(expected)) {
            throw new IOException("Embedded runtime checksum does not match");
        }
    }

    static final class RuntimeArchive extends JarFile {
        private final JarFile original;
        private final boolean closeOriginal;

        RuntimeArchive(ArchiveOptions options) throws IOException {
            super(options.payload().toFile());
            this.original = options.original();
            this.closeOriginal = options.closeOriginal();
        }

        @Override
        public void close() throws IOException {
            if (closeOriginal) {
                try (original) {
                    super.close();
                }
            } else {
                super.close();
            }
        }

        private void closePayload() throws IOException {
            super.close();
        }
    }

    private record ArchiveOptions(Path payload, JarFile original, boolean closeOriginal) {
    }
}

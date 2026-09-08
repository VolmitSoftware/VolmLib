package art.arcane.volmit.packaging;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class JdkClassLibrary {
    private static final String MODULE_INFO = "module-info.class";

    private JdkClassLibrary() {
    }

    public static File export(File cacheDirectory) throws IOException {
        String runtime = System.getProperty("java.runtime.version", System.getProperty("java.version"))
                .replaceAll("[^A-Za-z0-9.]", "_");
        Path target = cacheDirectory.toPath().resolve("jdk-" + runtime + ".jar");
        if (Files.isRegularFile(target) && Files.size(target) > 0) {
            return target.toFile();
        }
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "jdk-", ".export");
        try {
            write(temporary);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException failure) {
            Files.deleteIfExists(temporary);
            throw failure;
        }
        return target.toFile();
    }

    private static void write(Path destination) throws IOException {
        FileSystem runtime = FileSystems.getFileSystem(URI.create("jrt:/"));
        Path modules = runtime.getPath("/modules");
        Set<String> written = new HashSet<>();
        try (OutputStream file = Files.newOutputStream(destination);
             ZipOutputStream output = new ZipOutputStream(file);
             Stream<Path> paths = Files.walk(modules)) {
            output.setLevel(Deflater.BEST_SPEED);
            for (Path path : (Iterable<Path>) paths::iterator) {
                if (path.getNameCount() < 3 || !Files.isRegularFile(path)) {
                    continue;
                }
                String name = modules.relativize(path).toString();
                String entry = name.substring(name.indexOf('/') + 1);
                if (!entry.endsWith(".class") || entry.endsWith(MODULE_INFO) || !written.add(entry)) {
                    continue;
                }
                output.putNextEntry(new ZipEntry(entry));
                Files.copy(path, output);
                output.closeEntry();
            }
        }
    }
}

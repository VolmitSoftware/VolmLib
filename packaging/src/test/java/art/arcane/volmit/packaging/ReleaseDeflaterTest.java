package art.arcane.volmit.packaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseDeflaterTest {
    @TempDir
    Path directory;

    @Test
    void preservesEmptyBinaryAndRepeatedContentWithoutGrowingDeflate() throws IOException {
        byte[] random = new byte[8192];
        new Random(24).nextBytes(random);
        List<byte[]> contents = List.of(new byte[0], new byte[]{0}, random,
                "Iris generation registry and world configuration\n".repeat(300).getBytes(StandardCharsets.UTF_8));
        ReleaseDeflater compressor = new ReleaseDeflater(directory.resolve("first"));
        ReleaseDeflater repeat = new ReleaseDeflater(directory.resolve("repeat"));
        for (byte[] content : contents) {
            byte[] compressed = compressor.compress(content);
            assertTrue(compressed.length <= deflate(content).length);
            assertArrayEquals(compressed, repeat.compress(content));
            Inflater inflater = new Inflater(true);
            try (InflaterInputStream stream = new InflaterInputStream(new ByteArrayInputStream(compressed), inflater)) {
                assertArrayEquals(content, stream.readAllBytes());
            } finally {
                inflater.end();
            }
        }
    }

    @Test
    void reusesVerifiedContentAcrossCompressorInstances() throws IOException {
        byte[] content = "Shared plugin registry data\n".repeat(100).getBytes(StandardCharsets.UTF_8);
        byte[] compressed = new ReleaseDeflater(directory).compress(content);
        Path cached = cacheFiles().get(0);
        FileTime unchanged = FileTime.fromMillis(1_000_000L);
        Files.setLastModifiedTime(cached, unchanged);

        assertArrayEquals(compressed, new ReleaseDeflater(directory).compress(content));
        assertEquals(unchanged, Files.getLastModifiedTime(cached));
        assertEquals(1, cacheFiles().size());
    }

    @Test
    void changedInputCreatesAnIndependentEntry() throws IOException {
        byte[] original = "one registry entry".getBytes(StandardCharsets.UTF_8);
        byte[] changed = "two registry entries".getBytes(StandardCharsets.UTF_8);
        ReleaseDeflater compressor = new ReleaseDeflater(directory);
        byte[] first = compressor.compress(original);
        Path cached = cacheFiles().get(0);

        byte[] second = compressor.compress(changed);

        assertEquals(2, cacheFiles().size());
        assertArrayEquals(first, Files.readAllBytes(cached));
        assertArrayEquals(second, new ReleaseDeflater(directory).compress(changed));
        assertArrayEquals(first, new ReleaseDeflater(directory).compress(original));
    }

    @Test
    void repairsMalformedTruncatedWrongContentAndOversizedEntries() throws IOException {
        byte[] content = "Current registry content\n".repeat(100).getBytes(StandardCharsets.UTF_8);
        byte[] expected = new ReleaseDeflater(directory).compress(content);
        Path cached = cacheFiles().get(0);
        List<byte[]> corruptions = List.of(new byte[0], new byte[]{7, 7, 7},
                Arrays.copyOf(expected, expected.length - 1),
                Arrays.copyOf(expected, expected.length + 1),
                deflate("different content".getBytes(StandardCharsets.UTF_8)),
                new byte[content.length * 2]);

        for (byte[] corrupted : corruptions) {
            Files.write(cached, corrupted);
            assertArrayEquals(expected, new ReleaseDeflater(directory).compress(content));
            assertArrayEquals(expected, Files.readAllBytes(cached));
        }
        assertEquals(1, cacheFiles().size());
    }

    @Test
    void simultaneousWritersPublishCompleteEntries() throws Exception {
        byte[] content = "Concurrent packaging cache entry\n".repeat(100).getBytes(StandardCharsets.UTF_8);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<byte[]> first = workers.submit(() -> new ReleaseDeflater(directory).compress(content));
            Future<byte[]> second = workers.submit(() -> new ReleaseDeflater(directory).compress(content));
            byte[] expected = first.get(30, TimeUnit.SECONDS);
            assertArrayEquals(expected, second.get(30, TimeUnit.SECONDS));
            assertArrayEquals(expected, new ReleaseDeflater(directory).compress(content));
            List<Path> files = cacheFiles();
            assertEquals(1, files.size());
            assertArrayEquals(expected, Files.readAllBytes(files.get(0)));
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(30, TimeUnit.SECONDS));
        }
    }

    private List<Path> cacheFiles() throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile).toList();
        }
    }

    private byte[] deflate(byte[] content) {
        Deflater deflater = new Deflater(9, true);
        try {
            deflater.setInput(content);
            deflater.finish();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            deflater.end();
        }
    }
}

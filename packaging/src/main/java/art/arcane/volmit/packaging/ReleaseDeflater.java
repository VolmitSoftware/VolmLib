package art.arcane.volmit.packaging;

import com.googlecode.pngtastic.core.processing.zopfli.Options;
import com.googlecode.pngtastic.core.processing.zopfli.Zopfli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

final class ReleaseDeflater {
    private static final Options OPTIONS = new Options(
            Options.OutputFormat.DEFLATE, Options.BlockSplitting.FIRST, 15);
    private static final String CACHE_VERSION = "pngtastic-1.8-deflate-first-15-v1";

    private final Path cacheDirectory;
    private final Zopfli zopfli = new Zopfli(256 * 1024);

    ReleaseDeflater(Path cacheDirectory) {
        this.cacheDirectory = Objects.requireNonNull(cacheDirectory, "cacheDirectory").resolve(CACHE_VERSION);
    }

    public byte[] compress(byte[] input) throws IOException {
        String key = cacheKey(input);
        Path cacheFile = cacheDirectory.resolve(key.substring(0, 2)).resolve(key + ".deflate");
        byte[] cached = readCached(cacheFile, input);
        if (cached != null) {
            return cached;
        }
        byte[] standard = standardCompression(input);
        ByteArrayOutputStream output = new ByteArrayOutputStream(standard.length);
        zopfli.compress(OPTIONS, input, output);
        byte[] optimized = output.toByteArray();
        byte[] result = optimized.length < standard.length ? optimized : standard;
        verify(input, result);
        writeCached(cacheFile, result);
        return result;
    }

    private String cacheKey(byte[] input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable.", failure);
        }
    }

    private byte[] readCached(Path cacheFile, byte[] input) throws IOException {
        int maximumBytes = (int) Math.min(Integer.MAX_VALUE - 8L,
                input.length + (long) (input.length / 8) + 1024L);
        byte[] compressed;
        try (InputStream cached = Files.newInputStream(cacheFile)) {
            compressed = cached.readNBytes(maximumBytes);
            if (cached.read() != -1) {
                return null;
            }
        } catch (NoSuchFileException missing) {
            return null;
        }
        try {
            verify(input, compressed);
            return compressed;
        } catch (IOException corrupt) {
            return null;
        }
    }

    private void writeCached(Path cacheFile, byte[] compressed) throws IOException {
        Files.createDirectories(cacheFile.getParent());
        Path temporary = Files.createTempFile(cacheFile.getParent(), "deflate-", ".tmp");
        try {
            Files.write(temporary, compressed);
            Files.move(temporary, cacheFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private byte[] standardCompression(byte[] input) {
        Deflater deflater = new Deflater(9, true);
        try {
            deflater.setInput(input);
            deflater.finish();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            while (!deflater.finished()) {
                int length = deflater.deflate(buffer);
                output.write(buffer, 0, length);
            }
            return output.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private void verify(byte[] input, byte[] compressed) throws IOException {
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(compressed);
            byte[] buffer = new byte[8192];
            int offset = 0;
            while (!inflater.finished()) {
                int length = inflater.inflate(buffer);
                if (length == 0 && !inflater.finished()) {
                    throw new IOException("Release compression produced an incomplete DEFLATE stream.");
                }
                if (length > input.length - offset
                        || !Arrays.equals(input, offset, offset + length, buffer, 0, length)) {
                    throw new IOException("Release compression changed entry content.");
                }
                offset += length;
            }
            if (offset != input.length || inflater.getRemaining() != 0) {
                throw new IOException("Release compression changed entry length.");
            }
        } catch (DataFormatException failure) {
            throw new IOException("Release compression produced invalid DEFLATE data.", failure);
        } finally {
            inflater.end();
        }
    }
}

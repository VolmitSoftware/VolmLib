package art.arcane.volmit.packaging;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseDeflaterTest {
    @Test
    void preservesEmptyBinaryAndRepeatedContentWithoutGrowingDeflate() throws IOException {
        byte[] random = new byte[8192];
        new Random(24).nextBytes(random);
        List<byte[]> contents = List.of(new byte[0], new byte[]{0}, random,
                "Iris generation registry and world configuration\n".repeat(300).getBytes(StandardCharsets.UTF_8));
        ReleaseDeflater compressor = new ReleaseDeflater();
        ReleaseDeflater repeat = new ReleaseDeflater();
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

package art.arcane.volmlib.util.io;

import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CountingDataInputStreamTest {
    @Test
    public void readAheadMatchesTheBufferedWrapUnderRandomOperations() throws IOException {
        for (long seed = 1; seed <= 40; seed++) {
            Random random = new Random(seed);
            byte[] source = new byte[1 + random.nextInt(400_000)];
            random.nextBytes(source);
            CountingDataInputStream expected = CountingDataInputStream.wrap(new BufferedInputStream(new ChunkedStream(source, seed)));
            CountingDataInputStream actual = CountingDataInputStream.readAhead(new ChunkedStream(source, seed));
            assertTrue(actual.markSupported());
            boolean marked = false;
            for (int step = 0; step < 4_000; step++) {
                int op = random.nextInt(10);
                String context = "seed " + seed + " step " + step + " op " + op;
                switch (op) {
                    case 0, 1 -> assertEquals(context, readByte(expected), readByte(actual));
                    case 2 -> assertEquals(context, readLong(expected), readLong(actual));
                    case 3 -> {
                        int length = random.nextInt(random.nextBoolean() ? 32 : 200_000);
                        assertArrayEquals(context, readFully(expected, length), readFully(actual, length));
                    }
                    case 4 -> {
                        long target = expected.count() + random.nextInt(random.nextBoolean() ? 64 : 150_000);
                        expected.skipTo(Math.min(target, source.length));
                        actual.skipTo(Math.min(target, source.length));
                    }
                    case 5 -> {
                        expected.mark(Integer.MAX_VALUE);
                        actual.mark(Integer.MAX_VALUE);
                        marked = true;
                    }
                    case 6 -> {
                        if (marked) {
                            expected.reset();
                            actual.reset();
                        }
                    }
                    case 7 -> {
                        expected.mark(0);
                        actual.mark(0);
                        marked = false;
                    }
                    default -> {
                        byte[] expectedBytes = new byte[random.nextInt(5_000)];
                        int read = expected.read(expectedBytes);
                        if (read < 0) {
                            assertEquals(context, -1, actual.read());
                        } else if (read > 0) {
                            byte[] actualBytes = new byte[read];
                            actual.readFully(actualBytes);
                            assertArrayEquals(context, Arrays.copyOf(expectedBytes, read), actualBytes);
                        }
                    }
                }
                assertEquals(context, expected.count(), actual.count());
            }
        }
    }

    private static int readByte(CountingDataInputStream in) throws IOException {
        try {
            return in.readUnsignedByte();
        } catch (EOFException end) {
            return -1;
        }
    }

    private static long readLong(CountingDataInputStream in) throws IOException {
        try {
            return in.readLong();
        } catch (EOFException end) {
            return Long.MIN_VALUE;
        }
    }

    private static byte[] readFully(CountingDataInputStream in, int length) throws IOException {
        byte[] bytes = new byte[length];
        try {
            in.readFully(bytes);
            return bytes;
        } catch (EOFException end) {
            return new byte[0];
        }
    }

    private static final class ChunkedStream extends InputStream {
        private final ByteArrayInputStream in;
        private final Random chunks;

        private ChunkedStream(byte[] source, long seed) {
            this.in = new ByteArrayInputStream(source);
            this.chunks = new Random(seed ^ 0x51L);
        }

        @Override
        public int read() {
            return in.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
            return in.read(b, off, Math.min(len, 1 + chunks.nextInt(70_000)));
        }

        @Override
        public long skip(long n) {
            return in.skip(Math.min(n, 1 + chunks.nextInt(70_000)));
        }

        @Override
        public int available() {
            return in.available();
        }
    }

    @Test
    public void readAheadRestoresTheCountOnReset() throws IOException {
        byte[] source = new byte[300_000];
        Arrays.fill(source, (byte) 7);
        CountingDataInputStream in = CountingDataInputStream.readAhead(new ByteArrayInputStream(source));
        in.skipTo(10);
        in.mark(Integer.MAX_VALUE);
        in.readFully(new byte[200_000]);
        assertEquals(200_010, in.count());
        in.reset();
        assertEquals(10, in.count());
        in.skipTo(300_000);
        assertEquals(-1, in.read());
    }
}

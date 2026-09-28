package art.arcane.volmlib.util.io;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class CountingDataInputStream extends DataInputStream {
    private static final int READ_AHEAD_BYTES = 64 * 1024;

    private final Counter counter;

    protected CountingDataInputStream(InputStream in) {
        super(in);
        if (!(in instanceof Counter c)) {
            throw new IllegalArgumentException("Underlying stream must be a Counter");
        }
        this.counter = c;
    }

    public static CountingDataInputStream wrap(InputStream in) {
        return new CountingDataInputStream(new Counter(in));
    }

    /**
     * Counting stream that reads ahead into its own unsynchronized buffer. Only for callers that own the rest of
     * {@code in}: bytes read ahead are gone from the source. A mark keeps every byte after it until it is cleared.
     */
    public static CountingDataInputStream readAhead(InputStream in) {
        return new CountingDataInputStream(new ReadAheadCounter(in, READ_AHEAD_BYTES));
    }

    public long count() {
        return counter.count;
    }

    public void skipTo(long target) throws IOException {
        skipNBytes(Math.max(target - counter.count, 0));
    }

    protected static class Counter extends InputStream {
        protected final InputStream in;
        protected long count;
        private long mark = -1;
        private int markLimit = 0;

        public Counter(InputStream in) {
            this.in = in;
        }

        @Override
        public int read() throws IOException {
            int i = in.read();
            if (i != -1) {
                count(1);
            }
            return i;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int i = in.read(b, off, len);
            if (i != -1) {
                count(i);
            }
            return i;
        }

        private void count(int i) {
            count = Math.addExact(count, i);
            if (mark == -1) {
                return;
            }

            markLimit -= i;
            if (markLimit <= 0) {
                mark = -1;
            }
        }

        @Override
        public boolean markSupported() {
            return in.markSupported();
        }

        @Override
        public synchronized void mark(int readlimit) {
            if (!in.markSupported()) {
                return;
            }

            in.mark(readlimit);
            if (readlimit <= 0) {
                mark = -1;
                markLimit = 0;
                return;
            }

            mark = count;
            markLimit = readlimit;
        }

        @Override
        public synchronized void reset() throws IOException {
            in.reset();
            count = mark;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }

    private static final class ReadAheadCounter extends Counter {
        private byte[] buffer;
        private int position;
        private int limit;
        private int markPosition = -1;
        private long markCount;

        private ReadAheadCounter(InputStream in, int size) {
            super(in);
            this.buffer = new byte[size];
        }

        @Override
        public int read() throws IOException {
            if (position >= limit && !fill()) {
                return -1;
            }
            count++;
            return buffer[position++] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) {
                return 0;
            }
            if (position >= limit) {
                if (len >= buffer.length && markPosition < 0) {
                    int read = in.read(b, off, len);
                    if (read > 0) {
                        count += read;
                    }
                    return read;
                }
                if (!fill()) {
                    return -1;
                }
            }
            int copied = Math.min(limit - position, len);
            System.arraycopy(buffer, position, b, off, copied);
            position += copied;
            count += copied;
            return copied;
        }

        @Override
        public long skip(long n) throws IOException {
            if (n <= 0L) {
                return 0L;
            }
            if (position >= limit) {
                if (markPosition < 0) {
                    long skipped = in.skip(n);
                    if (skipped > 0L) {
                        count += skipped;
                    }
                    return skipped;
                }
                if (!fill()) {
                    return 0L;
                }
            }
            int skipped = (int) Math.min(limit - position, n);
            position += skipped;
            count += skipped;
            return skipped;
        }

        @Override
        public int available() throws IOException {
            return (limit - position) + in.available();
        }

        @Override
        public boolean markSupported() {
            return true;
        }

        @Override
        public synchronized void mark(int readlimit) {
            if (readlimit <= 0) {
                markPosition = -1;
                return;
            }
            markPosition = position;
            markCount = count;
        }

        @Override
        public synchronized void reset() throws IOException {
            if (markPosition < 0) {
                throw new IOException("Resetting to invalid mark");
            }
            position = markPosition;
            count = markCount;
        }

        private boolean fill() throws IOException {
            if (markPosition < 0) {
                position = 0;
                limit = 0;
            } else {
                int kept = limit - markPosition;
                if (markPosition > 0) {
                    System.arraycopy(buffer, markPosition, buffer, 0, kept);
                } else if (kept == buffer.length) {
                    buffer = Arrays.copyOf(buffer, buffer.length << 1);
                }
                position -= markPosition;
                limit = kept;
                markPosition = 0;
            }
            int read = in.read(buffer, limit, buffer.length - limit);
            if (read <= 0) {
                return false;
            }
            limit += read;
            return true;
        }
    }
}

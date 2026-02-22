package art.arcane.volmlib.util.mantle;

import art.arcane.volmlib.util.io.CountingDataInputStream;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceArray;

public abstract class MantleChunkSupport<M> extends FlaggedChunk {
    private final AtomicReferenceArray<M> sections;
    private final Semaphore ref;
    private final AtomicBoolean closed;
    private final int x;
    private final int z;

    protected MantleChunkSupport(int sectionHeight, int x, int z) {
        super();
        this.sections = new AtomicReferenceArray<>(sectionHeight);
        this.ref = new Semaphore(Integer.MAX_VALUE, true);
        this.closed = new AtomicBoolean(false);
        this.x = x;
        this.z = z;
    }

    protected MantleChunkSupport(int version, int sectionHeight, CountingDataInputStream din) throws IOException {
        this(sectionHeight, din.readByte(), din.readByte());

        int sectionCount = din.readByte();
        readFlags(version, din);

        for (int i = 0; i < sectionCount; i++) {
            onBeforeReadSection(i);

            long size = din.readInt();
            if (size == 0L) {
                continue;
            }

            long start = din.count();
            if (i >= sectionHeight) {
                din.skipTo(start + size);
                continue;
            }

            try {
                sections.set(i, readSection(din));
            } catch (IOException exception) {
                long end = start + size;
                onReadSectionFailure(i, start, end, din, exception);
                din.skipTo(end);
            }

            if (din.count() != start + size) {
                throw new IOException("Chunk section read size mismatch!");
            }
        }
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    public int sectionCount() {
        return sections.length();
    }

    public void close() {
        closed.set(true);
        ref.acquireUninterruptibly(Integer.MAX_VALUE);
        ref.release(Integer.MAX_VALUE);
    }

    public boolean inUse() {
        return ref.availablePermits() < Integer.MAX_VALUE;
    }

    public MantleChunkSupport<M> use() {
        if (closed.get()) {
            throw new IllegalStateException("Chunk is closed!");
        }

        ref.acquireUninterruptibly();
        if (closed.get()) {
            ref.release();
            throw new IllegalStateException("Chunk is closed!");
        }

        return this;
    }

    public void release() {
        ref.release();
    }

    public void copyFrom(MantleChunkSupport<M> chunk) {
        use();
        try {
            super.copyFrom(chunk, () -> {
                for (int i = 0; i < sections.length(); i++) {
                    sections.set(i, chunk.get(i));
                }
            });
        } finally {
            release();
        }
    }

    public boolean exists(int section) {
        return get(section) != null;
    }

    public M get(int section) {
        return sections.get(section);
    }

    public void clear() {
        for (int i = 0; i < sections.length(); i++) {
            delete(i);
        }
    }

    public void delete(int section) {
        sections.set(section, null);
    }

    public M getOrCreate(int section) {
        M sectionData = get(section);
        if (sectionData != null) {
            return sectionData;
        }

        M instance = createSection();
        M value = sections.compareAndExchange(section, null, instance);
        return value == null ? instance : value;
    }

    public void write(DataOutputStream dos) throws IOException {
        close();
        dos.writeByte(x);
        dos.writeByte(z);
        dos.writeByte(sections.length());
        writeFlags(dos);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream(8192);
        DataOutputStream sub = new DataOutputStream(bytes);
        for (int i = 0; i < sections.length(); i++) {
            trimIndex(i);
            if (exists(i)) {
                try {
                    M section = get(i);
                    if (section == null) {
                        dos.writeInt(0);
                        continue;
                    }
                    writeSection(section, sub);
                    dos.writeInt(bytes.size());
                    bytes.writeTo(dos);
                } finally {
                    bytes.reset();
                }
            } else {
                dos.writeInt(0);
            }
        }
    }

    public void trimSections() {
        for (int i = 0; i < sections.length(); i++) {
            if (exists(i)) {
                trimIndex(i);
            }
        }
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    protected void trimIndex(int index) {
        if (!exists(index)) {
            return;
        }

        M section = get(index);
        if (section == null) {
            return;
        }

        if (isSectionEmpty(section)) {
            sections.set(index, null);
            return;
        }

        trimSection(section);
        if (isSectionEmpty(section)) {
            sections.set(index, null);
        }
    }

    protected void onBeforeReadSection(int index) {
    }

    protected void onReadSectionFailure(int index, long start, long end, CountingDataInputStream din, IOException error) {
    }

    protected abstract M createSection();

    protected abstract M readSection(CountingDataInputStream din) throws IOException;

    protected abstract void writeSection(M section, DataOutputStream dos) throws IOException;

    protected abstract void trimSection(M section);

    protected abstract boolean isSectionEmpty(M section);
}

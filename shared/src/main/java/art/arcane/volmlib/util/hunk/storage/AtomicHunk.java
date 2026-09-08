package art.arcane.volmlib.util.hunk.storage;

import java.util.concurrent.atomic.AtomicReferenceArray;

public class AtomicHunk<T> extends StorageHunk<T> {
    private final AtomicReferenceArray<T> data;
    private final int xStride;
    private final int zStride;

    public AtomicHunk(int w, int h, int d) {
        super(w, h, d);
        data = new AtomicReferenceArray<>(w * h * d);
        xStride = w;
        zStride = w * h;
    }

    public AtomicReferenceArray<T> getData() {
        return data;
    }

    @Override
    public boolean isAtomic() {
        return true;
    }

    @Override
    public void setRaw(int x, int y, int z, T t) {
        data.set(index(x, y, z), t);
    }

    @Override
    public T getRaw(int x, int y, int z) {
        return data.get(index(x, y, z));
    }

    protected int index(int x, int y, int z) {
        return (z * zStride) + (y * xStride) + x;
    }
}

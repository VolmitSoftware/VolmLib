package art.arcane.volmlib.util.hunk.storage;

import java.util.concurrent.atomic.AtomicLongArray;

public class AtomicLongHunk extends StorageHunk<Long> {
    private final AtomicLongArray data;

    public AtomicLongHunk(int w, int h, int d) {
        super(w, h, d);
        data = new AtomicLongArray(w * h * d);
    }

    public AtomicLongArray getData() {
        return data;
    }

    @Override
    public boolean isAtomic() {
        return true;
    }

    @Override
    public void setRaw(int x, int y, int z, Long t) {
        data.set(index(x, y, z), t);
    }

    @Override
    public Long getRaw(int x, int y, int z) {
        return data.get(index(x, y, z));
    }

    protected int index(int x, int y, int z) {
        return (z * getWidth() * getHeight()) + (y * getWidth()) + x;
    }
}

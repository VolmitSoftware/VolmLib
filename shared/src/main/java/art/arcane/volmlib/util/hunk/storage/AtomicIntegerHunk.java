package art.arcane.volmlib.util.hunk.storage;

import java.util.concurrent.atomic.AtomicIntegerArray;

public class AtomicIntegerHunk extends StorageHunk<Integer> {
    private final AtomicIntegerArray data;

    public AtomicIntegerHunk(int w, int h, int d) {
        super(w, h, d);
        data = new AtomicIntegerArray(w * h * d);
    }

    public AtomicIntegerArray getData() {
        return data;
    }

    @Override
    public boolean isAtomic() {
        return true;
    }

    @Override
    public void setRaw(int x, int y, int z, Integer t) {
        data.set(index(x, y, z), t);
    }

    @Override
    public Integer getRaw(int x, int y, int z) {
        return data.get(index(x, y, z));
    }

    protected int index(int x, int y, int z) {
        return (z * getWidth() * getHeight()) + (y * getWidth()) + x;
    }
}

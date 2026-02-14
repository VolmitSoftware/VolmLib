package art.arcane.volmlib.util.hunk.storage;

import java.util.Arrays;

public class SynchronizedArrayHunk<T> extends StorageHunk<T> {
    private final T[] data;

    @SuppressWarnings("unchecked")
    public SynchronizedArrayHunk(int w, int h, int d) {
        super(w, h, d);
        data = (T[]) new Object[w * h * d];
    }

    public T[] getData() {
        return data;
    }

    @Override
    public void setRaw(int x, int y, int z, T t) {
        synchronized (data) {
            data[index(x, y, z)] = t;
        }
    }

    @Override
    public T getRaw(int x, int y, int z) {
        synchronized (data) {
            return data[index(x, y, z)];
        }
    }

    protected int index(int x, int y, int z) {
        return (z * getWidth() * getHeight()) + (y * getWidth()) + x;
    }

    @Override
    public void fill(T t) {
        synchronized (data) {
            Arrays.fill(data, t);
        }
    }
}

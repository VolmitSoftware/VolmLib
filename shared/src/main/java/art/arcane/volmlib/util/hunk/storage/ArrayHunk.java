package art.arcane.volmlib.util.hunk.storage;

import art.arcane.volmlib.util.cache.CacheKey;

import java.util.Arrays;

public class ArrayHunk<T> extends StorageHunk<T> {
    private final T[] data;

    @SuppressWarnings("unchecked")
    public ArrayHunk(int w, int h, int d) {
        super(w, h, d);
        data = (T[]) new Object[w * h * d];
    }

    public T[] getData() {
        return data;
    }

    @Override
    public void setRaw(int x, int y, int z, T t) {
        data[index(x, y, z)] = t;
    }

    @Override
    public T getRaw(int x, int y, int z) {
        return data[index(x, y, z)];
    }

    protected int index(int x, int y, int z) {
        return CacheKey.to1D(x, y, z, getWidth(), getHeight());
    }

    @Override
    public void fill(T t) {
        Arrays.fill(data, t);
    }
}

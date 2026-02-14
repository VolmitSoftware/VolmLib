package art.arcane.volmlib.util.hunk.storage;

import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.function.Consumer4IO;

import java.io.IOException;
import java.util.Map;

public class MappedHunk<T> extends StorageHunk<T> {
    private final Map<Integer, T> data;

    public MappedHunk(int w, int h, int d) {
        super(w, h, d);
        data = new KMap<>();
    }

    public Map<Integer, T> getData() {
        return data;
    }

    public int getEntryCount() {
        return data.size();
    }

    public boolean isMapped() {
        return true;
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

    @Override
    public void setRaw(int x, int y, int z, T t) {
        if (t == null) {
            data.remove(index(x, y, z));
            return;
        }

        data.put(index(x, y, z), t);
    }

    @Override
    public T getRaw(int x, int y, int z) {
        return data.get(index(x, y, z));
    }

    public MappedHunk<T> iterateSync(Consumer4<Integer, Integer, Integer, T> c) {
        int idx;
        int z;

        for (Map.Entry<Integer, T> g : data.entrySet()) {
            idx = g.getKey();
            z = idx / (getWidth() * getHeight());
            idx -= (z * getWidth() * getHeight());
            c.accept(idx % getWidth(), idx / getWidth(), z, g.getValue());
        }

        return this;
    }

    public MappedHunk<T> iterateSyncIO(Consumer4IO<Integer, Integer, Integer, T> c) throws IOException {
        int idx;
        int z;

        for (Map.Entry<Integer, T> g : data.entrySet()) {
            idx = g.getKey();
            z = idx / (getWidth() * getHeight());
            idx -= (z * getWidth() * getHeight());
            c.accept(idx % getWidth(), idx / getWidth(), z, g.getValue());
        }

        return this;
    }

    public void empty(T b) {
        data.clear();
    }

    protected int index(int x, int y, int z) {
        return (z * getWidth() * getHeight()) + (y * getWidth()) + x;
    }
}

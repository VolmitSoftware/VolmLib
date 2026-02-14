package art.arcane.volmlib.util.hunk.storage;

import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.function.Consumer4IO;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MappedSyncHunk<T> extends StorageHunk<T> {
    private final Map<Integer, T> data;

    public MappedSyncHunk(int w, int h, int d) {
        super(w, h, d);
        data = new HashMap<>();
    }

    public Map<Integer, T> getData() {
        return data;
    }

    public int getEntryCount() {
        synchronized (data) {
            return data.size();
        }
    }

    public boolean isMapped() {
        return true;
    }

    public boolean isEmpty() {
        synchronized (data) {
            return data.isEmpty();
        }
    }

    @Override
    public void setRaw(int x, int y, int z, T t) {
        synchronized (data) {
            if (t == null) {
                data.remove(index(x, y, z));
                return;
            }

            data.put(index(x, y, z), t);
        }
    }

    @Override
    public T getRaw(int x, int y, int z) {
        synchronized (data) {
            return data.get(index(x, y, z));
        }
    }

    public MappedSyncHunk<T> iterateSync(Consumer4<Integer, Integer, Integer, T> c) {
        synchronized (data) {
            int idx;
            int z;

            for (Map.Entry<Integer, T> g : data.entrySet()) {
                idx = g.getKey();
                z = idx / (getWidth() * getHeight());
                idx -= (z * getWidth() * getHeight());
                c.accept(idx % getWidth(), idx / getWidth(), z, g.getValue());
            }
        }

        return this;
    }

    public MappedSyncHunk<T> iterateSyncIO(Consumer4IO<Integer, Integer, Integer, T> c) throws IOException {
        synchronized (data) {
            int idx;
            int z;

            for (Map.Entry<Integer, T> g : data.entrySet()) {
                idx = g.getKey();
                z = idx / (getWidth() * getHeight());
                idx -= (z * getWidth() * getHeight());
                c.accept(idx % getWidth(), idx / getWidth(), z, g.getValue());
            }
        }

        return this;
    }

    public void empty(T b) {
        synchronized (data) {
            data.clear();
        }
    }

    protected int index(int x, int y, int z) {
        return (z * getWidth() * getHeight()) + (y * getWidth()) + x;
    }
}

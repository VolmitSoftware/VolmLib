package art.arcane.volmlib.util.hunk.storage;

import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.function.Consumer4IO;
import art.arcane.volmlib.util.hunk.bits.DataContainer;
import art.arcane.volmlib.util.hunk.bits.Writable;

import java.io.IOException;

public class PaletteHunk<T> extends StorageHunk<T> {
    private DataContainer<T> data;

    public PaletteHunk(int w, int h, int d, Writable<T> writer) {
        super(w, h, d);
        data = new DataContainer<>(writer, w * h * d);
    }

    public DataContainer<T> getData() {
        return data;
    }

    public void setPalette(DataContainer<T> c) {
        data = c;
    }

    public boolean isMapped() {
        return false;
    }

    @Override
    public void setRaw(int x, int y, int z, T t) {
        data.set(index(x, y, z), t);
    }

    @Override
    public T getRaw(int x, int y, int z) {
        return data.get(index(x, y, z));
    }

    public PaletteHunk<T> iterateSync(Consumer4<Integer, Integer, Integer, T> c) {
        for (int i = 0; i < getWidth(); i++) {
            for (int j = 0; j < getHeight(); j++) {
                for (int k = 0; k < getDepth(); k++) {
                    T t = getRaw(i, j, k);
                    if (t != null) {
                        c.accept(i, j, k, t);
                    }
                }
            }
        }

        return this;
    }

    public PaletteHunk<T> iterateSyncIO(Consumer4IO<Integer, Integer, Integer, T> c) throws IOException {
        for (int i = 0; i < getWidth(); i++) {
            for (int j = 0; j < getHeight(); j++) {
                for (int k = 0; k < getDepth(); k++) {
                    T t = getRaw(i, j, k);
                    if (t != null) {
                        c.accept(i, j, k, t);
                    }
                }
            }
        }

        return this;
    }

    public void empty(T b) {
        fill(b);
    }

    protected int index(int x, int y, int z) {
        return (z * getWidth() * getHeight()) + (y * getWidth()) + x;
    }
}

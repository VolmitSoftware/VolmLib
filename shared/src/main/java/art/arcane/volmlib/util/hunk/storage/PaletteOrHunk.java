package art.arcane.volmlib.util.hunk.storage;

import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.function.Consumer4IO;
import art.arcane.volmlib.util.hunk.HunkLike;
import art.arcane.volmlib.util.hunk.bits.DataContainer;
import art.arcane.volmlib.util.hunk.bits.Writable;

import java.io.IOException;
import java.util.function.Supplier;

public abstract class PaletteOrHunk<T> extends StorageHunk<T> implements Writable<T> {
    private final HunkLike<T> hunk;

    public PaletteOrHunk(int width, int height, int depth, boolean allow, Supplier<? extends HunkLike<T>> factory) {
        super(width, height, depth);
        hunk = (allow && (width * height * depth <= 4096)) ? new PaletteHunk<>(width, height, depth, this) : factory.get();
    }

    public DataContainer<T> palette() {
        return isPalette() ? ((PaletteHunk<T>) hunk).getData() : null;
    }

    public boolean isPalette() {
        return hunk instanceof PaletteHunk;
    }

    public void setPalette(DataContainer<T> c) {
        if (isPalette()) {
            ((PaletteHunk<T>) hunk).setPalette(c);
        }
    }

    @Override
    public void setRaw(int x, int y, int z, T t) {
        hunk.setRaw(x, y, z, t);
    }

    @Override
    public T getRaw(int x, int y, int z) {
        return hunk.getRaw(x, y, z);
    }

    public int getEntryCount() {
        if (hunk instanceof MappedHunk<?>) {
            return ((MappedHunk<T>) hunk).getEntryCount();
        }

        if (hunk instanceof MappedSyncHunk<?>) {
            return ((MappedSyncHunk<T>) hunk).getEntryCount();
        }

        return getWidth() * getHeight() * getDepth();
    }

    public boolean isMapped() {
        return hunk instanceof MappedHunk<?> || hunk instanceof MappedSyncHunk<?>;
    }

    public boolean isEmpty() {
        return isMapped();
    }

    public PaletteOrHunk<T> iterateSync(Consumer4<Integer, Integer, Integer, T> c) {
        if (hunk instanceof MappedHunk<?>) {
            ((MappedHunk<T>) hunk).iterateSync(c);
            return this;
        }

        if (hunk instanceof MappedSyncHunk<?>) {
            ((MappedSyncHunk<T>) hunk).iterateSync(c);
            return this;
        }

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

    public PaletteOrHunk<T> iterateSyncIO(Consumer4IO<Integer, Integer, Integer, T> c) throws IOException {
        if (hunk instanceof MappedHunk<?>) {
            ((MappedHunk<T>) hunk).iterateSyncIO(c);
            return this;
        }

        if (hunk instanceof MappedSyncHunk<?>) {
            ((MappedSyncHunk<T>) hunk).iterateSyncIO(c);
            return this;
        }

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
        if (hunk instanceof MappedHunk<?>) {
            ((MappedHunk<T>) hunk).empty(b);
            return;
        }

        if (hunk instanceof MappedSyncHunk<?>) {
            ((MappedSyncHunk<T>) hunk).empty(b);
            return;
        }

        if (hunk instanceof PaletteHunk<?>) {
            ((PaletteHunk<T>) hunk).empty(b);
            return;
        }

        fill(b);
    }

    protected HunkLike<T> source() {
        return hunk;
    }
}

package art.arcane.volmlib.util.hunk.view;

import art.arcane.volmlib.util.hunk.HunkLike;

import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("ClassCanBeRecord")
public class WriteTrackHunk<T> implements HunkLike<T> {
    private final HunkLike<T> src;
    private final AtomicBoolean b;

    public WriteTrackHunk(HunkLike<T> src, AtomicBoolean b) {
        this.src = src;
        this.b = b;
    }

    @Override
    public void setRaw(int x, int y, int z, T t) {
        if (!b.get()) {
            b.set(true);
        }

        src.setRaw(x, y, z, t);
    }

    @Override
    public T getRaw(int x, int y, int z) {
        return src.getRaw(x, y, z);
    }

    @Override
    public int getWidth() {
        return src.getWidth();
    }

    @Override
    public int getHeight() {
        return src.getHeight();
    }

    @Override
    public int getDepth() {
        return src.getDepth();
    }

    protected HunkLike<T> source() {
        return src;
    }
}

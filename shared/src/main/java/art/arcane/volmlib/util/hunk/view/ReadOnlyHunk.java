package art.arcane.volmlib.util.hunk.view;

import art.arcane.volmlib.util.hunk.HunkLike;

@SuppressWarnings("ClassCanBeRecord")
public class ReadOnlyHunk<T> implements HunkLike<T> {
    private final HunkLike<T> src;

    public ReadOnlyHunk(HunkLike<T> src) {
        this.src = src;
    }

    @Override
    public void setRaw(int x, int y, int z, T t) {
        throw new IllegalStateException("This hunk is read only!");
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

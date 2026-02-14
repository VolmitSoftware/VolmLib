package art.arcane.volmlib.util.hunk.view;

import art.arcane.volmlib.util.hunk.HunkLike;

@SuppressWarnings("ClassCanBeRecord")
public class FringedHunkView<T> implements HunkLike<T> {
    private final HunkLike<T> src;
    private final HunkLike<T> out;

    public FringedHunkView(HunkLike<T> src, HunkLike<T> out) {
        this.src = src;
        this.out = out;
    }

    @Override
    public void setRaw(int x, int y, int z, T t) {
        out.setRaw(x, y, z, t);
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

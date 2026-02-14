package art.arcane.volmlib.util.hunk.view;

import art.arcane.volmlib.util.hunk.HunkLike;

@SuppressWarnings("ClassCanBeRecord")
public class DriftHunkView<T> implements HunkLike<T> {
    private final int ox;
    private final int oy;
    private final int oz;
    private final HunkLike<T> src;

    public DriftHunkView(HunkLike<T> src, int ox, int oy, int oz) {
        this.src = src;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
    }

    @Override
    public void setRaw(int x, int y, int z, T t) {
        src.setRaw(x + ox, y + oy, z + oz, t);
    }

    @Override
    public T getRaw(int x, int y, int z) {
        return src.getRaw(x + ox, y + oy, z + oz);
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

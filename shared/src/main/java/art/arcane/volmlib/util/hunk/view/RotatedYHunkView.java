package art.arcane.volmlib.util.hunk.view;

import art.arcane.volmlib.util.hunk.HunkLike;

public class RotatedYHunkView<T> implements HunkLike<T> {
    private final HunkLike<T> src;
    private final double sin;
    private final double cos;

    public RotatedYHunkView(HunkLike<T> src, double deg) {
        this.src = src;
        this.sin = Math.sin(Math.toRadians(deg));
        this.cos = Math.cos(Math.toRadians(deg));
    }

    @Override
    public void setRaw(int x, int y, int z, T t) {
        int xc = (int) Math.round(cos * (getWidth() / 2f) + sin * (getDepth() / 2f));
        int zc = (int) Math.round(-sin * (getWidth() / 2f) + cos * (getDepth() / 2f));
        int rx = (int) Math.round(cos * (x - xc) + sin * (z - zc)) - xc;
        int rz = (int) Math.round(-sin * (x - xc) + cos * (z - zc)) - zc;

        if (contains(rx, y, rz)) {
            src.setRaw(rx, y, rz, t);
        }
    }

    @Override
    public T getRaw(int x, int y, int z) {
        int xc = (int) Math.round(cos * (getWidth() / 2f) + sin * (getDepth() / 2f));
        int zc = (int) Math.round(-sin * (getWidth() / 2f) + cos * (getDepth() / 2f));
        int rx = (int) Math.round(cos * (x - xc) + sin * (z - zc)) - xc;
        int rz = (int) Math.round(-sin * (x - xc) + cos * (z - zc)) - zc;

        return contains(rx, y, rz) ? src.getRaw(rx, y, rz) : null;
    }

    @Override
    public int getWidth() {
        return src.getWidth();
    }

    @Override
    public int getDepth() {
        return src.getDepth();
    }

    @Override
    public int getHeight() {
        return src.getHeight();
    }

    protected HunkLike<T> source() {
        return src;
    }

    private boolean contains(int x, int y, int z) {
        return x >= 0 && x < getWidth() && y >= 0 && y < getHeight() && z >= 0 && z < getDepth();
    }
}

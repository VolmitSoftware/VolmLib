package art.arcane.volmlib.util.hunk.view;

import art.arcane.volmlib.util.hunk.HunkLike;

public class RotatedXHunkView<T> implements HunkLike<T> {
    private final HunkLike<T> src;
    private final double sin;
    private final double cos;

    public RotatedXHunkView(HunkLike<T> src, double deg) {
        this.src = src;
        this.sin = Math.sin(Math.toRadians(deg));
        this.cos = Math.cos(Math.toRadians(deg));
    }

    @Override
    public void setRaw(int x, int y, int z, T t) {
        int yc = (int) Math.round(cos * (getHeight() / 2f) - sin * (getDepth() / 2f));
        int zc = (int) Math.round(sin * (getHeight() / 2f) + cos * (getDepth() / 2f));
        int ry = (int) Math.round(cos * (y - yc) - sin * (z - zc)) - yc;
        int rz = (int) Math.round(sin * y - yc + cos * (z - zc)) - zc;

        if (contains(x, ry, rz)) {
            src.setRaw(x, ry, rz, t);
        }
    }

    @Override
    public T getRaw(int x, int y, int z) {
        int yc = (int) Math.round(cos * (getHeight() / 2f) - sin * (getDepth() / 2f));
        int zc = (int) Math.round(sin * (getHeight() / 2f) + cos * (getDepth() / 2f));
        int ry = (int) Math.round(cos * (y - yc) - sin * (z - zc)) - yc;
        int rz = (int) Math.round(sin * y - yc + cos * (z - zc)) - zc;

        return contains(x, ry, rz) ? src.getRaw(x, ry, rz) : null;
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

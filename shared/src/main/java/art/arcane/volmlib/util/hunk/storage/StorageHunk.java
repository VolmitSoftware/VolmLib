package art.arcane.volmlib.util.hunk.storage;

import art.arcane.volmlib.util.hunk.HunkLike;

public abstract class StorageHunk<T> implements HunkLike<T> {
    private final int width;
    private final int height;
    private final int depth;

    protected StorageHunk(int width, int height, int depth) {
        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new IllegalArgumentException("Unsupported size " + width + " " + height + " " + depth);
        }

        this.width = width;
        this.height = height;
        this.depth = depth;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getDepth() {
        return depth;
    }

    @Override
    public int getHeight() {
        return height;
    }

    public boolean isAtomic() {
        return false;
    }

    public void fill(T t) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    setRaw(x, y, z, t);
                }
            }
        }
    }
}

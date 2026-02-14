package art.arcane.volmlib.util.hunk;

import java.util.function.Predicate;

public final class HunkMutationSupport {
    private HunkMutationSupport() {
    }

    public static <T> void setRangeInclusive(HunkLike<T> h, int x1, int y1, int z1, int x2, int y2, int z2, T t) {
        for (int i = x1; i <= x2; i++) {
            for (int j = y1; j <= y2; j++) {
                for (int k = z1; k <= z2; k++) {
                    h.setRaw(i, j, k, t);
                }
            }
        }
    }

    public static <T> T getClosest(HunkLike<T> h, int x, int y, int z) {
        return h.getRaw(x >= h.getWidth() ? h.getWidth() - 1 : x < 0 ? 0 : x,
                y >= h.getHeight() ? h.getHeight() - 1 : y < 0 ? 0 : y,
                z >= h.getDepth() ? h.getDepth() - 1 : z < 0 ? 0 : z);
    }

    public static <T> void fill(HunkLike<T> h, T t) {
        setRangeInclusive(h, 0, 0, 0, h.getWidth() - 1, h.getHeight() - 1, h.getDepth() - 1, t);
    }

    public static <T> void setIfExists(HunkLike<T> h, int x, int y, int z, T t) {
        if (x < 0 || x >= h.getWidth() || y < 0 || y >= h.getHeight() || z < 0 || z >= h.getDepth()) {
            return;
        }

        h.setRaw(x, y, z, t);
    }

    public static <T> T getOr(HunkLike<T> h, int x, int y, int z, T t) {
        T v = h.getRaw(x, y, z);

        if (v == null) {
            return t;
        }

        return v;
    }

    public static <T> T getIfExists(HunkLike<T> h, int x, int y, int z, T t) {
        if (x < 0 || x >= h.getWidth() || y < 0 || y >= h.getHeight() || z < 0 || z >= h.getDepth()) {
            return t;
        }

        return getOr(h, x, y, z, t);
    }

    public static <T> void insert(HunkLike<T> target, int offX, int offY, int offZ, HunkLike<T> source) {
        for (int i = offX; i < offX + source.getWidth(); i++) {
            for (int j = offY; j < offY + source.getHeight(); j++) {
                for (int k = offZ; k < offZ + source.getDepth(); k++) {
                    target.setRaw(i, j, k, source.getRaw(i - offX, j - offY, k - offZ));
                }
            }
        }
    }

    public static <T> void insertSoftly(HunkLike<T> target, int offX, int offY, int offZ, HunkLike<T> source, Predicate<T> shouldOverwrite) {
        for (int i = offX; i < offX + source.getWidth(); i++) {
            for (int j = offY; j < offY + source.getHeight(); j++) {
                for (int k = offZ; k < offZ + source.getDepth(); k++) {
                    if (shouldOverwrite.test(target.getRaw(i, j, k))) {
                        target.setRaw(i, j, k, source.getRaw(i - offX, j - offY, k - offZ));
                    }
                }
            }
        }
    }
}

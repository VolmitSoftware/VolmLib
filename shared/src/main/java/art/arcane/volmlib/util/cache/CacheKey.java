package art.arcane.volmlib.util.cache;

import org.bukkit.Chunk;

/**
 * Shared coordinate/key conversion helpers for cache utilities.
 */
public final class CacheKey {
    private CacheKey() {
    }

    public static long key(Chunk chunk) {
        return key(chunk.getX(), chunk.getZ());
    }

    public static long key(int x, int z) {
        return (((long) x) << 32) | (z & 0xffffffffL);
    }

    public static int keyX(long key) {
        return (int) (key >> 32);
    }

    public static int keyZ(long key) {
        return (int) key;
    }

    public static int to1D(int x, int y, int z, int w, int h) {
        return (z * w * h) + (y * w) + x;
    }

    public static int[] to3D(int idx, int w, int h) {
        final int z = idx / (w * h);
        idx -= (z * w * h);
        final int y = idx / w;
        final int x = idx % w;
        return new int[]{x, y, z};
    }
}

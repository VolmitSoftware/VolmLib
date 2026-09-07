package art.arcane.volmlib.util.cache;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class WorldCache2DDoubleTest {
    @Test
    public void interleavedFillsAndReadsRetainSignedChunkIdentity() {
        AtomicInteger calls = new AtomicInteger();
        WorldCache2DDouble cache = new WorldCache2DDouble((x, z) -> {
            calls.incrementAndGet();
            return x * 0.5D - z * 0.25D;
        }, 16);
        int[][] chunks = {{0, 0}, {-1, 1}, {1, -1}, {134217727, -134217728}, {-134217728, 134217727}};
        Object[] boxed = new Object[256];
        double[] values = new double[256];
        for (int round = 0; round < 2; round++) {
            for (int[] chunk : chunks) {
                int x = chunk[0] << 4;
                int z = chunk[1] << 4;
                assertEquals(x * 0.5D - z * 0.25D, cache.get(x, z), 0D);
                cache.fillChunk(chunk[0], chunk[1], boxed);
                cache.fillChunk(chunk[0], chunk[1], values);
                for (int localX = 0; localX < 16; localX++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        double expected = (x + localX) * 0.5D - (z + localZ) * 0.25D;
                        assertEquals(expected, (Double) boxed[localZ * 16 + localX], 0D);
                        assertEquals(expected, values[localZ * 16 + localX], 0D);
                        assertEquals(expected, cache.get(x + localX, z + localZ), 0D);
                    }
                }
            }
        }
        assertEquals(chunks.length * 256, calls.get());
    }

    @Test
    public void getCachesResolvedValuesPerCoordinate() {
        AtomicInteger calls = new AtomicInteger();
        WorldCache2DDouble cache = new WorldCache2DDouble((x, z) -> {
            calls.incrementAndGet();
            return (x * 31D) + z;
        }, 16);

        double first = cache.get(12, -7);
        double second = cache.get(12, -7);

        assertEquals(first, second, 0D);
        assertEquals(1, calls.get());
    }

    @Test
    public void fillChunkProducesExpectedValues() {
        WorldCache2DDouble cache = new WorldCache2DDouble((x, z) -> (x * 0.5D) - (z * 0.25D), 8);
        Object[] values = new Object[256];

        cache.fillChunk(3, -2, values);

        assertEquals((3 << 4) * 0.5D - ((-2 << 4) * 0.25D), (Double) values[0], 0D);
        assertEquals((((3 << 4) + 15) * 0.5D) - (((-2 << 4) + 15) * 0.25D), (Double) values[255], 0D);
    }

    @Test
    public void fillChunkDoublesProducesExpectedValues() {
        WorldCache2DDouble cache = new WorldCache2DDouble((x, z) -> (x * 0.5D) - (z * 0.25D), 8);
        double[] values = new double[256];

        cache.fillChunk(3, -2, values);

        assertEquals((3 << 4) * 0.5D - ((-2 << 4) * 0.25D), values[0], 0D);
        assertEquals((((3 << 4) + 15) * 0.5D) - (((-2 << 4) + 15) * 0.25D), values[255], 0D);
    }

    @Test
    public void evictedChunksAreNotRetainedOutsideTheDeclaredCapacity() {
        AtomicInteger calls = new AtomicInteger();
        WorldCache2DDouble cache = new WorldCache2DDouble((x, z) -> calls.incrementAndGet(), 1);

        assertEquals(1D, cache.get(0, 0), 0D);
        assertEquals(2D, cache.get(16, 0), 0D);
        assertEquals(3D, cache.get(0, 0), 0D);
        assertEquals(3, calls.get());
        assertEquals(256L, cache.getSize());
    }

    @Test
    public void maximumChunkCapacityCanGrowWithoutDiscardingCachedValues() {
        AtomicInteger calls = new AtomicInteger();
        WorldCache2DDouble cache = new WorldCache2DDouble((x, z) -> calls.incrementAndGet(), 1);

        assertEquals(1D, cache.get(0, 0), 0D);
        cache.setMaximumChunks(4);
        assertEquals(1D, cache.get(0, 0), 0D);

        assertEquals(1, calls.get());
        assertEquals(1_024L, cache.getMaxSize());
        assertThrows(IllegalArgumentException.class, () -> cache.setMaximumChunks(0));
    }

}

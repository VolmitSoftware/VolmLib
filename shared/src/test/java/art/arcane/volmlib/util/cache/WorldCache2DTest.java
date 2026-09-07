package art.arcane.volmlib.util.cache;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class WorldCache2DTest {
    @Test
    public void interleavedFillsAndReadsRetainSignedChunkIdentity() {
        AtomicInteger calls = new AtomicInteger();
        WorldCache2D<Integer> cache = WorldCache2D.ofInts((x, z) -> {
            calls.incrementAndGet();
            return x * 31 + z;
        }, 16, () -> new ChunkCache2D<>("iris"));
        int[][] chunks = {{0, 0}, {-1, 1}, {1, -1}, {134217727, -134217728}, {-134217728, 134217727}};
        Object[] values = new Object[256];
        for (int round = 0; round < 2; round++) {
            for (int[] chunk : chunks) {
                int x = chunk[0] << 4;
                int z = chunk[1] << 4;
                assertEquals(x * 31 + z, cache.get(x, z).intValue());
                cache.fillChunk(chunk[0], chunk[1], values);
                for (int localX = 0; localX < 16; localX++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        int expected = (x + localX) * 31 + z + localZ;
                        assertEquals(expected, values[localZ * 16 + localX]);
                        assertEquals(expected, cache.get(x + localX, z + localZ).intValue());
                    }
                }
            }
        }
        assertEquals(chunks.length * 256, calls.get());
    }

    @Test
    public void getCachesResolvedValuesPerCoordinate() {
        AtomicInteger calls = new AtomicInteger();
        WorldCache2D<Integer> cache = new WorldCache2D<>((x, z) -> {
            calls.incrementAndGet();
            return (x * 31) + z;
        }, 16, () -> new ChunkCache2D<>("iris"));

        int first = cache.get(12, -7);
        int second = cache.get(12, -7);

        assertEquals(first, second);
        assertEquals(1, calls.get());
    }

    @Test
    public void fillChunkProducesExpectedValues() {
        WorldCache2D<String> cache = new WorldCache2D<>((x, z) -> x + ":" + z, 8, () -> new ChunkCache2D<>("iris"));
        Object[] values = new Object[256];

        cache.fillChunk(3, -2, values);

        assertEquals("48:-32", values[0]);
        assertEquals("63:-17", values[255]);
    }

    @Test
    public void nullResultsRemainUncached() {
        AtomicInteger calls = new AtomicInteger();
        WorldCache2D<String> cache = new WorldCache2D<>((x, z) -> {
            calls.incrementAndGet();
            return null;
        }, 4, () -> new ChunkCache2D<>("iris"));

        assertNull(cache.get(1, 2));
        assertNull(cache.get(1, 2));
        assertEquals(2, calls.get());
    }

    @Test(timeout = 2_000L)
    public void resolverFailureDoesNotPoisonTheCoordinate() {
        AtomicInteger calls = new AtomicInteger();
        WorldCache2D<Integer> cache = new WorldCache2D<>((x, z) -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("first attempt failed");
            }
            return 42;
        }, 4, () -> new ChunkCache2D<>("iris"));

        assertThrows(IllegalStateException.class, () -> cache.get(1, 2));
        assertEquals(42, cache.get(1, 2).intValue());
        assertEquals(2, calls.get());
    }

    @Test
    public void evictedChunksAreNotRetainedOutsideTheDeclaredCapacity() {
        AtomicInteger calls = new AtomicInteger();
        WorldCache2D<Integer> cache = new WorldCache2D<>((x, z) -> calls.incrementAndGet(),
                1, () -> new ChunkCache2D<>("iris"));

        assertEquals(1, cache.get(0, 0).intValue());
        assertEquals(2, cache.get(16, 0).intValue());
        assertEquals(3, cache.get(0, 0).intValue());
        assertEquals(3, calls.get());
        assertEquals(256L, cache.getSize());
    }
}

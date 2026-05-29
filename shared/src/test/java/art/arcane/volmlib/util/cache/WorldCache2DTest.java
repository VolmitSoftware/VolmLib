package art.arcane.volmlib.util.cache;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WorldCache2DTest {
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
}

package art.arcane.volmlib.util.cache;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class WorldCache2DDoubleTest {
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
}

package art.arcane.volmlib.util.cache;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
    public void cachesValuesAcrossSignedChunkBoundaries() {
        int[] coordinates = {-17, -16, -15, -1, 0, 15, 16, 17};
        AtomicInteger calls = new AtomicInteger();
        WorldCache2DDouble cache = new WorldCache2DDouble((x, z) -> {
            calls.incrementAndGet();
            return (x * 31D) + z;
        }, 16);

        for (int coordinate : coordinates) {
            assertEquals((coordinate * 31D) - 17D, cache.get(coordinate, -17), 0D);
        }
        for (int coordinate : coordinates) {
            assertEquals((coordinate * 31D) - 17D, cache.get(coordinate, -17), 0D);
        }

        assertEquals(coordinates.length, calls.get());
    }

    @Test
    public void resolvesSharedCoordinateOnceAcrossThreads() throws Exception {
        int workerCount = 8;
        AtomicInteger calls = new AtomicInteger();
        WorldCache2DDouble cache = new WorldCache2DDouble((x, z) -> calls.incrementAndGet(), 16);
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Double>> futures = new ArrayList<>(workerCount);

        try {
            for (int i = 0; i < workerCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return cache.get(12, -7);
                }));
            }

            assertTrue(ready.await(5L, TimeUnit.SECONDS));
            start.countDown();
            for (Future<Double> future : futures) {
                assertEquals(1D, future.get(5L, TimeUnit.SECONDS), 0D);
            }
            assertEquals(1, calls.get());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }
}

package art.arcane.volmlib.util.cache;

import org.junit.Test;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void cachesValuesAcrossSignedChunkBoundaries() {
        int[] coordinates = {-17, -16, -15, -1, 0, 15, 16, 17};
        AtomicInteger calls = new AtomicInteger();
        WorldCache2D<String> cache = new WorldCache2D<>((x, z) -> {
            calls.incrementAndGet();
            return x + ":" + z;
        }, 16, () -> new ChunkCache2D<>("iris"));

        for (int coordinate : coordinates) {
            assertEquals(coordinate + ":-17", cache.get(coordinate, -17));
        }
        for (int coordinate : coordinates) {
            assertEquals(coordinate + ":-17", cache.get(coordinate, -17));
        }

        assertEquals(coordinates.length, calls.get());
    }

    @Test
    public void resolvesSharedCoordinateOnceAcrossThreads() throws Exception {
        int workerCount = 8;
        AtomicInteger calls = new AtomicInteger();
        WorldCache2D<Integer> cache = new WorldCache2D<>((x, z) -> calls.incrementAndGet(),
                16, () -> new ChunkCache2D<>("world-cache-concurrency-test"));
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>(workerCount);

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
            for (Future<Integer> future : futures) {
                assertEquals(1, future.get(5L, TimeUnit.SECONDS).intValue());
            }
            assertEquals(1, calls.get());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }

    @Test
    public void idleWorkerDoesNotRetainAnEvictedChunk() throws Exception {
        ReferenceQueue<ChunkCache2D<Integer>> queue = new ReferenceQueue<>();
        AtomicReference<WeakReference<ChunkCache2D<Integer>>> watched = new AtomicReference<>();
        AtomicReference<Throwable> workerFailure = new AtomicReference<>();
        AtomicInteger suppliedChunks = new AtomicInteger();
        WorldCache2D<Integer> cache = new WorldCache2D<>((x, z) -> x,
                1, () -> {
                    ChunkCache2D<Integer> chunk = new ChunkCache2D<>("world-cache-retention-test");
                    suppliedChunks.incrementAndGet();
                    watched.compareAndSet(null, new WeakReference<>(chunk, queue));
                    return chunk;
                });
        CountDownLatch cached = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            try {
                cache.get(0, 0);
                cached.countDown();
                release.await();
            } catch (Throwable e) {
                workerFailure.set(e);
                cached.countDown();
            }
        }, "world-cache-retention-test");
        Reference<? extends ChunkCache2D<Integer>> collected;

        worker.start();
        try {
            assertTrue(cached.await(5L, TimeUnit.SECONDS));
            assertNull(workerFailure.get());
            assertEquals(16, cache.get(16, 0).intValue());
            assertEquals(2, suppliedChunks.get());
            assertEquals(256L, cache.getSize());
            collected = awaitCollected(queue);
        } finally {
            release.countDown();
            worker.join(5000L);
        }

        assertNull(workerFailure.get());
        assertSame(watched.get(), collected);
    }

    private static <T> Reference<? extends T> awaitCollected(ReferenceQueue<T> queue) throws InterruptedException {
        for (int attempt = 0; attempt < 20; attempt++) {
            System.gc();
            Reference<? extends T> collected = queue.remove(250L);
            if (collected != null) {
                return collected;
            }
        }

        return null;
    }
}

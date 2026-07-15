package art.arcane.volmlib.util.matter;

import art.arcane.volmlib.util.matter.slices.IntMatter;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class MatterConcurrencyTest {
    private static final int CONTENDER_COUNT = 8;

    @Test(timeout = 10_000L)
    public void concurrentFirstAccessPublishesOneSlice() throws Exception {
        SlowMatter matter = new SlowMatter();
        ExecutorService executor = Executors.newFixedThreadPool(CONTENDER_COUNT + 1);
        CountDownLatch contendersReady = new CountDownLatch(CONTENDER_COUNT);
        CountDownLatch begin = new CountDownLatch(1);
        List<Future<MatterSlice<Integer>>> contenders = new ArrayList<>(CONTENDER_COUNT);

        try {
            Future<MatterSlice<Integer>> first = executor.submit(() -> matter.slice(Integer.class));
            assertTrue(matter.firstCreationEntered.await(5L, TimeUnit.SECONDS));

            for (int i = 0; i < CONTENDER_COUNT; i++) {
                contenders.add(executor.submit(() -> {
                    contendersReady.countDown();
                    begin.await(5L, TimeUnit.SECONDS);
                    return matter.slice(Integer.class);
                }));
            }

            assertTrue(contendersReady.await(5L, TimeUnit.SECONDS));
            begin.countDown();
            try {
                assertFalse(matter.additionalCreationEntered.await(500L, TimeUnit.MILLISECONDS));
            } finally {
                matter.releaseCreation.countDown();
            }

            MatterSlice<Integer> expected = first.get(5L, TimeUnit.SECONDS);
            assertEquals(1, matter.creationCount.get());
            for (Future<MatterSlice<Integer>> contender : contenders) {
                assertSame(expected, contender.get(5L, TimeUnit.SECONDS));
            }
        } finally {
            matter.releaseCreation.countDown();
            executor.shutdownNow();
        }
    }

    private static final class SlowMatter implements Matter {
        private final MatterHeader header;
        private final Map<Class<?>, MatterSlice<?>> slices;
        private final AtomicInteger creationCount;
        private final CountDownLatch firstCreationEntered;
        private final CountDownLatch additionalCreationEntered;
        private final CountDownLatch releaseCreation;

        private SlowMatter() {
            header = new MatterHeader();
            slices = new ConcurrentHashMap<>();
            creationCount = new AtomicInteger();
            firstCreationEntered = new CountDownLatch(1);
            additionalCreationEntered = new CountDownLatch(1);
            releaseCreation = new CountDownLatch(1);
        }

        @Override
        public MatterHeader getHeader() {
            return header;
        }

        @Override
        public int getWidth() {
            return 1;
        }

        @Override
        public int getHeight() {
            return 1;
        }

        @Override
        public int getDepth() {
            return 1;
        }

        @Override
        public Map<Class<?>, MatterSlice<?>> getSliceMap() {
            return slices;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> MatterSlice<T> createSlice(Class<T> type, Matter matter) {
            int count = creationCount.incrementAndGet();
            if (count == 1) {
                firstCreationEntered.countDown();
            } else {
                additionalCreationEntered.countDown();
            }

            try {
                if (!releaseCreation.await(5L, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to construct matter slice");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while constructing matter slice", e);
            }

            if (type != Integer.class) {
                return null;
            }

            return (MatterSlice<T>) new IntMatter(1, 1, 1);
        }
    }
}

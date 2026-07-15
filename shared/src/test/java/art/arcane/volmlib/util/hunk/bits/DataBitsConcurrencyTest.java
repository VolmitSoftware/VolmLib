package art.arcane.volmlib.util.hunk.bits;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;

public class DataBitsConcurrencyTest {
    private static final int THREAD_COUNT = 10;
    private static final int ROUND_COUNT = 2_000;

    @Test(timeout = 15_000L)
    public void concurrentSetPreservesPackedValues() throws Exception {
        assertConcurrentWritesPreserved(false);
    }

    @Test(timeout = 15_000L)
    public void concurrentGetAndSetPreservesPackedValues() throws Exception {
        assertConcurrentWritesPreserved(true);
    }

    private void assertConcurrentWritesPreserved(boolean exchange) throws Exception {
        DataBits dataBits = new DataBits(6, THREAD_COUNT);
        CyclicBarrier start = new CyclicBarrier(THREAD_COUNT + 1);
        CyclicBarrier finish = new CyclicBarrier(THREAD_COUNT + 1);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        List<Future<Void>> futures = new ArrayList<>(THREAD_COUNT);
        AtomicBoolean lostWrite = new AtomicBoolean();

        try {
            for (int slot = 0; slot < THREAD_COUNT; slot++) {
                int writeSlot = slot;
                futures.add(executor.submit(() -> {
                    for (int round = 0; round < ROUND_COUNT; round++) {
                        start.await(5L, TimeUnit.SECONDS);
                        if (exchange) {
                            dataBits.getAndSet(writeSlot, writeSlot + 1);
                        } else {
                            dataBits.set(writeSlot, writeSlot + 1);
                        }
                        finish.await(5L, TimeUnit.SECONDS);
                    }
                    return null;
                }));
            }

            for (int round = 0; round < ROUND_COUNT; round++) {
                dataBits.getRaw().set(0, 0L);
                start.await(5L, TimeUnit.SECONDS);
                finish.await(5L, TimeUnit.SECONDS);
                for (int slot = 0; slot < THREAD_COUNT; slot++) {
                    if (dataBits.get(slot) != slot + 1) {
                        lostWrite.set(true);
                    }
                }
            }

            for (Future<Void> future : futures) {
                future.get(5L, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertFalse(lostWrite.get());
    }
}

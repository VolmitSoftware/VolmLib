package art.arcane.volmlib.util.atomics;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;

public class AtomicRollingSequenceTest {
    @Test
    public void firstValueFillsTheWindowAndLaterValuesRollOver() {
        AtomicRollingSequence sequence = new AtomicRollingSequence(4);
        assertEquals(0D, sequence.getAverage(), 0D);
        sequence.put(8D);
        assertEquals(8D, sequence.getAverage(), 0D);
        sequence.put(4D);
        assertEquals(7D, sequence.getAverage(), 0D);
        sequence.put(2D);
        sequence.put(2D);
        sequence.put(0D);
        assertEquals(2D, sequence.getAverage(), 0D);
        sequence.put(6D);
        assertEquals(2.5D, sequence.getAverage(), 0D);
        assertEquals(0D, sequence.getMin(), 0D);
        assertEquals(6D, sequence.getMax(), 0D);
    }

    @Test
    public void concurrentPutsKeepTheAverageEqualToTheWindow() throws InterruptedException {
        AtomicRollingSequence sequence = new AtomicRollingSequence(32);
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();
        for (int thread = 0; thread < threads; thread++) {
            int offset = thread;
            Thread worker = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int put = 0; put < 20_000; put++) {
                    sequence.put((put + offset) % 17);
                }
            });
            worker.start();
            workers.add(worker);
        }
        start.countDown();
        for (Thread worker : workers) {
            worker.join();
        }

        double windowSum = 0D;
        for (int slot = 0; slot < sequence.size(); slot++) {
            windowSum += sequence.values.get(slot);
        }
        assertEquals(windowSum / sequence.size(), sequence.getAverage(), 1.0E-9D);
    }
}

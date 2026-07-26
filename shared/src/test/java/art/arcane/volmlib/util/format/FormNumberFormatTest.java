package art.arcane.volmlib.util.format;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FormNumberFormatTest {
    @Test
    public void groupedIntegerOutputIsUnchanged() {
        assertEquals("-10,334", Form.f(-10334L));
        assertEquals("1,234,567", Form.f(1234567L));
        assertEquals("1,234,567", Form.f(1234567));
        assertEquals("0", Form.f(0L));
    }

    @Test
    public void decimalOutputIsUnchanged() {
        assertEquals("1234.57", Form.f(1234.5678D, 2));
        assertEquals("0.5", Form.f(0.5D, 2));
        assertEquals("2.434300", Form.fd(2.4343D, 6).replace(',', '.'));
        assertEquals("53.2%", Form.pc(0.532D, 1));
    }

    @Test
    public void concurrentFormattingAtDifferentPrecisionsNeverBleedsBetweenThreads() throws InterruptedException {
        int threads = 8;
        int iterations = 4000;
        double sample = 1234.56789D;
        List<String> expected = new ArrayList<>(threads);

        for (int index = 0; index < threads; index++) {
            expected.add(Form.f(sample, index + 1) + "|" + Form.fd(sample, index + 1) + "|" + Form.f(1234567L));
        }

        AtomicReference<String> mismatch = new AtomicReference<>();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Thread> workers = new ArrayList<>(threads);

        for (int index = 0; index < threads; index++) {
            int precision = index + 1;
            String want = expected.get(index);
            Thread worker = new Thread(() -> {
                try {
                    start.await();

                    for (int pass = 0; pass < iterations; pass++) {
                        String got = Form.f(sample, precision) + "|" + Form.fd(sample, precision) + "|" + Form.f(1234567L);

                        if (!want.equals(got)) {
                            mismatch.compareAndSet(null, "expected " + want + " but formatted " + got);
                            return;
                        }
                    }
                } catch (Throwable failure) {
                    mismatch.compareAndSet(null, failure.toString());
                } finally {
                    done.countDown();
                }
            });
            workers.add(worker);
            worker.start();
        }

        start.countDown();
        assertTrue(done.await(60L, TimeUnit.SECONDS));

        for (Thread worker : workers) {
            worker.join();
        }

        assertNull(mismatch.get(), mismatch.get());
    }
}

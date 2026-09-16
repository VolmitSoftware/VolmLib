package art.arcane.volmlib.util.parallel;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class MultiBurstSupportTest {
    @Test
    public void poolFactoryPreservesWorkerSettingsAndRunsAgainAfterReopen() throws Exception {
        List<Throwable> failures = new ArrayList<>();
        RecordingBurst burst = new RecordingBurst(failures);
        try {
            assertTrue(burst.pools.isEmpty());
            Thread firstWorker = burst.submit(Thread::currentThread).get(5, TimeUnit.SECONDS);
            ForkJoinPool first = burst.pools.get(0);
            assertEquals(3, first.getParallelism());
            assertTrue(first.getAsyncMode());
            assertEquals("test-burst 1", firstWorker.getName());
            assertEquals(Thread.NORM_PRIORITY, firstWorker.getPriority());

            burst.close();
            assertTrue(first.isTerminated());
            assertTrue(burst.isShutdown());
            assertSame(Thread.currentThread(), burst.submit(Thread::currentThread).get(5, TimeUnit.SECONDS));
            assertEquals(1, burst.pools.size());

            burst.reopen();
            Thread secondWorker = burst.submit(Thread::currentThread).get(5, TimeUnit.SECONDS);
            assertEquals(2, burst.pools.size());
            assertNotSame(firstWorker, secondWorker);
            assertNotSame(first, burst.pools.get(burst.pools.size() - 1));
            assertFalse(burst.isShutdown());
        } finally {
            burst.close();
        }
        assertTrue(burst.isTerminated());
        assertTrue(failures.isEmpty());
    }

    private static final class RecordingBurst extends MultiBurstSupport {
        private final List<ForkJoinPool> pools = new ArrayList<>();

        private RecordingBurst(List<Throwable> failures) {
            super("test-burst", Thread.NORM_PRIORITY, () -> 3, count -> count,
                    System::currentTimeMillis, failures::add, null, null, 1_000L);
        }

        @Override
        protected ForkJoinPool createPool(
                int parallelism,
                ForkJoinPool.ForkJoinWorkerThreadFactory factory,
                Thread.UncaughtExceptionHandler handler
        ) {
            ForkJoinPool pool = super.createPool(parallelism, factory, handler);
            pools.add(pool);
            return pool;
        }
    }
}

package art.arcane.volmlib.util.parallel;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class BurstExecutorSupportTest {
    @Test
    public void completeRunsEveryQueuedTaskBeforeReturning() {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger completed = new AtomicInteger();
        try {
            BurstExecutorSupport burst = new BurstExecutorSupport(executor, 2);
            burst.queue(completed::incrementAndGet);
            burst.queue(completed::incrementAndGet);

            burst.complete();

            assertEquals(2, completed.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void completeWaitsThroughInterruptionAndRestoresInterruptStatus() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch allowTaskCompletion = new CountDownLatch(1);
        AtomicBoolean taskCompleted = new AtomicBoolean();
        AtomicBoolean completeReturned = new AtomicBoolean();
        AtomicBoolean interruptRestored = new AtomicBoolean();
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        try {
            BurstExecutorSupport burst = new BurstExecutorSupport(executor, 1, errors::add);
            burst.queue(() -> {
                taskStarted.countDown();
                awaitUninterruptibly(allowTaskCompletion);
                taskCompleted.set(true);
            });
            assertTrue(taskStarted.await(1L, TimeUnit.SECONDS));

            Thread completionThread = new Thread(() -> {
                Thread.currentThread().interrupt();
                burst.complete();
                interruptRestored.set(Thread.currentThread().isInterrupted());
                completeReturned.set(true);
            }, "burst-complete-test");
            completionThread.start();
            completionThread.join(100L);

            assertTrue(completionThread.isAlive());
            assertFalse(completeReturned.get());
            allowTaskCompletion.countDown();
            completionThread.join(1_000L);

            assertFalse(completionThread.isAlive());
            assertTrue(taskCompleted.get());
            assertTrue(completeReturned.get());
            assertTrue(interruptRestored.get());
            assertTrue(errors.isEmpty());
        } finally {
            allowTaskCompletion.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void completeReportsTaskFailure() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        IllegalStateException failure = new IllegalStateException("failure");
        try {
            BurstExecutorSupport burst = new BurstExecutorSupport(executor, 1, errors::add);
            burst.queue(() -> {
                throw failure;
            });

            burst.complete();

            assertEquals(1, errors.size());
            assertTrue(errors.get(0) instanceof ExecutionException);
            assertSame(failure, errors.get(0).getCause());
        } finally {
            executor.shutdownNow();
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}

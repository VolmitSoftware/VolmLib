package art.arcane.volmlib.util.scheduling;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CancellableDelayedTaskTest {
    @Test
    public void nativeCancellationRetiresOnceAndSuppressesLateCallbacks() {
        AtomicInteger executed = new AtomicInteger();
        AtomicInteger retired = new AtomicInteger();
        AtomicInteger cancelled = new AtomicInteger();
        CancellableDelayedTask task = new CancellableDelayedTask(
            new FoliaScheduler.DelayedTask(10, executed::incrementAndGet, retired::incrementAndGet));
        task.attach(() -> {
            cancelled.incrementAndGet();
            return true;
        });
        task.cancel();
        task.cancel();
        task.retire();
        task.run();
        assertEquals(1, cancelled.get());
        assertEquals(1, retired.get());
        assertEquals(0, executed.get());
        assertTrue(task.isCancelled());
    }

    @Test
    public void cancellationBeforeAttachmentWaitsForNativeAcknowledgement() {
        AtomicInteger retired = new AtomicInteger();
        CancellableDelayedTask task = new CancellableDelayedTask(
            new FoliaScheduler.DelayedTask(10, () -> { }, retired::incrementAndGet));
        task.cancel();
        assertEquals(0, retired.get());
        assertFalse(task.isCancelled());
        task.attach(() -> true);
        assertEquals(1, retired.get());
        assertTrue(task.isCancelled());
    }

    @Test
    public void aNativeRunningResultWaitsForTheCallbackToDrain() {
        AtomicInteger executed = new AtomicInteger();
        AtomicInteger retired = new AtomicInteger();
        CancellableDelayedTask task = new CancellableDelayedTask(
            new FoliaScheduler.DelayedTask(10, executed::incrementAndGet, retired::incrementAndGet));
        task.attach(() -> false);
        task.cancel();
        assertEquals(0, retired.get());
        task.run();
        assertEquals(0, executed.get());
        assertEquals(1, retired.get());
    }

    @Test
    public void aCancellationFailureKeepsRetirementPendingUntilTheCallbackDrains() {
        AtomicInteger retired = new AtomicInteger();
        CancellableDelayedTask task = new CancellableDelayedTask(
            new FoliaScheduler.DelayedTask(10, () -> { }, retired::incrementAndGet));
        task.attach(() -> { throw new IllegalStateException("native cancellation failed"); });
        assertThrows(IllegalStateException.class, task::cancel);
        assertEquals(0, retired.get());
        assertFalse(task.isCancelled());
        task.run();
        assertEquals(1, retired.get());
    }

    @Test
    public void cancellationCannotRetireAnActionThatIsAlreadyExecuting() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        AtomicInteger retired = new AtomicInteger();
        CancellableDelayedTask task = new CancellableDelayedTask(new FoliaScheduler.DelayedTask(10, () -> {
            entered.countDown();
            try {
                assertTrue(finish.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AssertionError(failure);
            }
        }, retired::incrementAndGet));
        task.attach(() -> true);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> running = executor.submit(task);
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                task.cancel();
                task.retire();
                assertEquals(0, retired.get());
            } finally {
                finish.countDown();
            }
            running.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertFalse(task.isCancelled());
    }
}

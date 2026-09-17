package art.arcane.volmlib.util.parallel;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class HyperLockSupportTest {
    @Test
    public void interruptedWaitCannotRunWithoutOwningTheLock() throws InterruptedException {
        AtomicReference<Throwable> reported = new AtomicReference<>();
        HyperLockSupport lock = new HyperLockSupport(64, false, null, reported::set);
        AtomicBoolean ran = new AtomicBoolean();
        AtomicBoolean interrupted = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        lock.lock(3, 5);
        Thread waiter = new Thread(() -> {
            Thread.currentThread().interrupt();
            try {
                lock.with(3, 5, () -> ran.set(true));
            } catch (Throwable thrown) {
                failure.set(thrown);
            } finally {
                interrupted.set(Thread.currentThread().isInterrupted());
            }
        });
        try {
            waiter.start();
            waiter.join(5000L);
            assertFalse(waiter.isAlive());
            assertFalse(ran.get());
            assertTrue(interrupted.get());
            assertTrue(failure.get() instanceof IllegalStateException);
            assertTrue(reported.get() instanceof InterruptedException);
            assertSame(reported.get(), failure.get().getCause());
        } finally {
            lock.unlock(3, 5);
            waiter.interrupt();
            waiter.join(5000L);
        }
        lock.with(3, 5, () -> ran.set(true));
        assertTrue(ran.get());
    }
}

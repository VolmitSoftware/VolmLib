package art.arcane.volmlib.util.parallel;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

public class SyncExecutorSupport implements Executor, AutoCloseable {
    private final CountDownLatch latch = new CountDownLatch(1);
    private final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Cancellable ticker;
    private final int msPerTick;
    private final LongSupplier millis;

    public SyncExecutorSupport(int msPerTick, LongSupplier millis, TickScheduler scheduler) {
        this.msPerTick = msPerTick;
        this.millis = millis;
        this.ticker = scheduler.schedule(this::tick);
    }

    private void tick() {
        long time = millis.getAsLong() + msPerTick;
        while (time > millis.getAsLong()) {
            Runnable r = queue.poll();
            if (r == null) {
                break;
            }

            r.run();
        }

        if (closed.get() && queue.isEmpty()) {
            ticker.cancel();
            latch.countDown();
        }
    }

    @Override
    public void execute(Runnable command) {
        if (closed.get()) {
            throw new IllegalStateException("Executor is closed!");
        }

        queue.add(command);
    }

    @Override
    public void close() throws Exception {
        closed.set(true);
        latch.await();
    }

    @FunctionalInterface
    public interface TickScheduler {
        Cancellable schedule(Runnable task);
    }

    @FunctionalInterface
    public interface Cancellable {
        void cancel();
    }
}

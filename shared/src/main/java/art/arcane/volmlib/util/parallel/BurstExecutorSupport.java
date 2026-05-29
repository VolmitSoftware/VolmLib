package art.arcane.volmlib.util.parallel;

import art.arcane.volmlib.util.collection.KList;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.Future;
import java.util.function.Consumer;

@SuppressWarnings("ALL")
public class BurstExecutorSupport {
    private final ExecutorService executor;
    private final Consumer<Throwable> errorHandler;
    @Getter
    private final KList<Future<?>> futures;
    @Setter
    private boolean multicore = true;

    public BurstExecutorSupport(ExecutorService executor, int burstSizeEstimate) {
        this(executor, burstSizeEstimate, Throwable::printStackTrace);
    }

    public BurstExecutorSupport(ExecutorService executor, int burstSizeEstimate, Consumer<Throwable> errorHandler) {
        this.executor = executor;
        this.errorHandler = errorHandler == null ? Throwable::printStackTrace : errorHandler;
        futures = new KList<Future<?>>(burstSizeEstimate);
    }

    @SuppressWarnings("UnusedReturnValue")
    public Future<?> queue(Runnable r) {
        if (shouldRunInline()) {
            r.run();
            return CompletableFuture.completedFuture(null);
        }

        synchronized (futures) {
            Future<?> c = executor.submit(r);
            futures.add(c);
            return c;
        }
    }

    public BurstExecutorSupport queue(List<Runnable> r) {
        if (shouldRunInline()) {
            for (Runnable i : new KList<>(r)) {
                i.run();
            }

            return this;
        }

        synchronized (futures) {
            for (Runnable i : new KList<>(r)) {
                queue(i);
            }
        }

        return this;
    }

    public BurstExecutorSupport queue(Runnable[] r) {
        if (shouldRunInline()) {
            for (Runnable i : new KList<>(r)) {
                i.run();
            }

            return this;
        }

        synchronized (futures) {
            for (Runnable i : r) {
                queue(i);
            }
        }

        return this;
    }

    public void complete() {
        if (shouldRunInline()) {
            return;
        }

        List<Future<?>> queued;
        synchronized (futures) {
            if (futures.isEmpty()) {
                return;
            }
            queued = new KList<>(futures);
            futures.clear();
        }

        for (Future<?> i : queued) {
            try {
                i.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                errorHandler.accept(e);
            } catch (ExecutionException e) {
                errorHandler.accept(e);
            }
        }
    }

    private boolean shouldRunInline() {
        if (!multicore) {
            return true;
        }

        if (!(executor instanceof ForkJoinPool)) {
            return false;
        }
        ForkJoinPool pool = (ForkJoinPool) executor;

        Thread thread = Thread.currentThread();
        if (!(thread instanceof ForkJoinWorkerThread)) {
            return false;
        }
        ForkJoinWorkerThread worker = (ForkJoinWorkerThread) thread;

        return worker.getPool() == pool;
    }
}

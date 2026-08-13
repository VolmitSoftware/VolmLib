package art.arcane.volmlib.util.parallel;

import art.arcane.volmlib.util.collection.KList;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

@SuppressWarnings("ALL")
public class BurstExecutorSupport {
    private final Supplier<ExecutorService> executorSource;
    private final Consumer<Throwable> errorHandler;
    @Getter
    private final KList<Future<?>> futures;
    @Setter
    private boolean multicore = true;

    public BurstExecutorSupport(ExecutorService executor, int burstSizeEstimate) {
        this(executor, burstSizeEstimate, Throwable::printStackTrace);
    }

    public BurstExecutorSupport(ExecutorService executor, int burstSizeEstimate, Consumer<Throwable> errorHandler) {
        this(() -> executor, burstSizeEstimate, errorHandler);
    }

    /**
     * Resolves the executor per submission instead of pinning it at construction, so a burst
     * obtained before its owning pool closed degrades to the pool's post-close fallback
     * (inline execution) instead of throwing RejectedExecutionException at the caller.
     */
    public BurstExecutorSupport(Supplier<ExecutorService> executorSource, int burstSizeEstimate, Consumer<Throwable> errorHandler) {
        this.executorSource = executorSource;
        this.errorHandler = errorHandler == null ? Throwable::printStackTrace : errorHandler;
        futures = new KList<Future<?>>(burstSizeEstimate);
    }

    @SuppressWarnings("UnusedReturnValue")
    public Future<?> queue(Runnable r) {
        ExecutorService executor = executorSource.get();
        if (shouldRunInline(executor)) {
            r.run();
            return CompletableFuture.completedFuture(null);
        }

        synchronized (futures) {
            Future<?> c;
            try {
                c = executor.submit(r);
            } catch (RejectedExecutionException rejected) {
                errorHandler.accept(rejected);
                r.run();
                return CompletableFuture.completedFuture(null);
            }
            futures.add(c);
            return c;
        }
    }

    public BurstExecutorSupport queue(List<Runnable> r) {
        if (shouldRunInline(executorSource.get())) {
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
        if (shouldRunInline(executorSource.get())) {
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
        if (shouldRunInline(executorSource.get())) {
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
            } catch (CancellationException e) {
                errorHandler.accept(e);
            } catch (ExecutionException e) {
                errorHandler.accept(e);
            }
        }
    }

    private boolean shouldRunInline(ExecutorService executor) {
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

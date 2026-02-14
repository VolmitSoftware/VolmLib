package art.arcane.volmlib.util.parallel;

import art.arcane.volmlib.util.collection.KList;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;
import java.util.function.LongSupplier;

public class MultiBurstSupport implements ExecutorService {
    private final AtomicLong last;
    private final String name;
    private final int priority;
    private final IntSupplier parallelism;
    private final IntUnaryOperator threadCountResolver;
    private final LongSupplier millis;
    private final Consumer<Throwable> errorHandler;
    private final Consumer<String> infoHandler;
    private final Consumer<String> warnHandler;
    private final long shutdownTimeoutMillis;
    private final Object lock = new Object();
    private volatile ExecutorService service;

    public MultiBurstSupport(String name,
                             int priority,
                             IntSupplier parallelism,
                             IntUnaryOperator threadCountResolver,
                             LongSupplier millis,
                             Consumer<Throwable> errorHandler,
                             Consumer<String> infoHandler,
                             Consumer<String> warnHandler,
                             long shutdownTimeoutMillis) {
        this.name = name;
        this.priority = priority;
        this.parallelism = parallelism;
        this.threadCountResolver = threadCountResolver;
        this.millis = millis;
        this.errorHandler = errorHandler == null ? Throwable::printStackTrace : errorHandler;
        this.infoHandler = infoHandler;
        this.warnHandler = warnHandler;
        this.shutdownTimeoutMillis = shutdownTimeoutMillis;
        this.last = new AtomicLong(millis.getAsLong());
    }

    protected ExecutorService service() {
        return getService();
    }

    public long getLast() {
        return last.get();
    }

    private ExecutorService getService() {
        last.set(millis.getAsLong());
        if (service != null && !service.isShutdown()) {
            return service;
        }

        synchronized (lock) {
            if (service != null && !service.isShutdown()) {
                return service;
            }

            service = new ForkJoinPool(threadCountResolver.applyAsInt(parallelism.getAsInt()),
                    new ForkJoinPool.ForkJoinWorkerThreadFactory() {
                        int m = 0;

                        @Override
                        public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
                            final ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                            worker.setPriority(priority);
                            worker.setName(name + " " + ++m);
                            return worker;
                        }
                    },
                    (t, e) -> this.errorHandler.accept(e),
                    true);
            return service;
        }
    }

    public void burst(Runnable... r) {
        burst(r.length).queue(r).complete();
    }

    public void burst(boolean multicore, Runnable... r) {
        if (multicore) {
            burst(r);
        } else {
            sync(r);
        }
    }

    public void burst(List<Runnable> r) {
        burst(r.size()).queue(r).complete();
    }

    public void burst(boolean multicore, List<Runnable> r) {
        if (multicore) {
            burst(r);
        } else {
            sync(r);
        }
    }

    private void sync(List<Runnable> r) {
        for (Runnable i : new KList<>(r)) {
            i.run();
        }
    }

    public void sync(Runnable... r) {
        for (Runnable i : r) {
            i.run();
        }
    }

    public void sync(KList<Runnable> r) {
        for (Runnable i : r) {
            i.run();
        }
    }

    public BurstExecutorSupport burst(int estimate) {
        return new BurstExecutorSupport(getService(), estimate, errorHandler);
    }

    public BurstExecutorSupport burst() {
        return burst(16);
    }

    public BurstExecutorSupport burst(boolean multicore) {
        BurstExecutorSupport b = burst();
        b.setMulticore(multicore);
        return b;
    }

    public <T> Future<T> lazySubmit(Callable<T> o) {
        return getService().submit(o);
    }

    public void lazy(Runnable o) {
        getService().execute(o);
    }

    public Future<?> future(Runnable o) {
        return getService().submit(o);
    }

    public Future<?> complete(Runnable o) {
        return getService().submit(o);
    }

    public <T> Future<T> completeValue(Callable<T> o) {
        return getService().submit(o);
    }

    public <T> CompletableFuture<T> completableFuture(Callable<T> o) {
        CompletableFuture<T> f = new CompletableFuture<>();
        getService().submit(() -> {
            try {
                f.complete(o.call());
            } catch (Exception e) {
                f.completeExceptionally(e);
            }
        });
        return f;
    }

    @Override
    public void shutdown() {
        close();
    }

    @Override
    public List<Runnable> shutdownNow() {
        close();
        return List.of();
    }

    @Override
    public boolean isShutdown() {
        return service == null || service.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return service == null || service.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return service == null || service.awaitTermination(timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        getService().execute(command);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return getService().submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return getService().submit(task, result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return getService().submit(task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return getService().invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        return getService().invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return getService().invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return getService().invokeAny(tasks, timeout, unit);
    }

    public void close() {
        if (service != null) {
            close(service, millis, infoHandler, warnHandler, errorHandler, shutdownTimeoutMillis);
        }
    }

    public static void close(ExecutorService service,
                             LongSupplier millis,
                             Consumer<String> infoHandler,
                             Consumer<String> warnHandler,
                             Consumer<Throwable> errorHandler,
                             long timeoutMillis) {
        service.shutdown();
        long start = millis.getAsLong();
        try {
            while (!service.awaitTermination(1, TimeUnit.SECONDS)) {
                if (infoHandler != null) {
                    infoHandler.accept("Still waiting to shutdown burster...");
                }

                if (millis.getAsLong() - start > timeoutMillis) {
                    if (warnHandler != null) {
                        warnHandler.accept("Forcing Shutdown...");
                    }

                    try {
                        service.shutdownNow();
                    } catch (Throwable ignored) {
                    }

                    break;
                }
            }
        } catch (Throwable e) {
            if (errorHandler != null) {
                errorHandler.accept(e);
            } else {
                e.printStackTrace();
            }
        }
    }
}

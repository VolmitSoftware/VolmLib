package art.arcane.volmlib.util.scheduling;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public final class KeyedTaskQueue<K> {
    private final Executor executor;
    private final BiConsumer<K, Throwable> errors;
    private final Map<K, ArrayDeque<Task<?>>> queues = new HashMap<>();
    private final CompletableFuture<Void> drained = new CompletableFuture<>();
    private boolean closed;
    private long pending;
    private long failures;

    public KeyedTaskQueue(Executor executor, BiConsumer<K, Throwable> errors) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.errors = Objects.requireNonNull(errors, "errors");
    }

    public synchronized <T> CompletableFuture<T> submit(K key, Supplier<T> operation) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(operation, "operation");
        if (closed) {
            return CompletableFuture.failedFuture(new RejectedExecutionException("Task queue is closed"));
        }
        Task<T> task = new Task<>(operation);
        ArrayDeque<Task<?>> queue = queues.get(key);
        boolean start = queue == null;
        if (start) {
            queue = new ArrayDeque<>();
            queues.put(key, queue);
        }
        queue.add(task);
        pending++;
        if (start) {
            try {
                executor.execute(() -> drain(key));
            } catch (RejectedExecutionException failure) {
                queues.remove(key);
                pending--;
                failures++;
                task.result.completeExceptionally(failure);
                report(key, failure);
            }
        }
        return task.result;
    }

    public CompletableFuture<Void> execute(K key, Runnable operation) {
        return submit(key, () -> {
            operation.run();
            return null;
        });
    }

    public synchronized CompletableFuture<Void> closeAsync() {
        closed = true;
        if (queues.isEmpty()) {
            drained.complete(null);
        }
        return drained.copy();
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(queues.size(), pending, failures, closed);
    }

    private void drain(K key) {
        while (true) {
            Task<?> task;
            synchronized (this) {
                ArrayDeque<Task<?>> queue = queues.get(key);
                task = queue.poll();
                if (task == null) {
                    queues.remove(key);
                    if (closed && queues.isEmpty()) {
                        drained.complete(null);
                    }
                    return;
                }
            }
            try {
                task.run();
            } catch (Throwable failure) {
                synchronized (this) {
                    failures++;
                }
                report(key, failure);
            } finally {
                synchronized (this) {
                    pending--;
                }
            }
        }
    }

    public record Snapshot(int activeKeys, long pendingTasks, long failures, boolean closed) {}

    private void report(K key, Throwable failure) {
        try {
            errors.accept(key, failure);
        } catch (Throwable reportingFailure) {
            if (reportingFailure != failure) {
                failure.addSuppressed(reportingFailure);
            }
        }
    }

    private static final class Task<T> {
        private final Supplier<T> operation;
        private final CompletableFuture<T> result = new CompletableFuture<>();

        private Task(Supplier<T> operation) {
            this.operation = operation;
        }

        private void run() {
            try {
                result.complete(operation.get());
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
                throw failure;
            }
        }
    }
}

package art.arcane.volmlib.util.scheduling;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KeyedTaskQueueTest {
    @Test
    public void sameKeyKeepsOrderAfterFailureAndCloseWaitsForAcceptedTasks() {
        List<Runnable> workers = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        List<Throwable> failures = new ArrayList<>();
        KeyedTaskQueue<String> queue = new KeyedTaskQueue<>(workers::add, (key, failure) -> failures.add(failure));
        queue.execute("player", () -> values.add(1));
        CompletableFuture<Void> failed = queue.execute("player", () -> { throw new IllegalStateException("write failed"); });
        queue.execute("player", () -> values.add(2));
        queue.execute("other", () -> values.add(3));
        CompletableFuture<Void> closed = queue.closeAsync();
        assertFalse(closed.isDone());
        assertEquals(2, workers.size());
        workers.get(1).run();
        workers.get(0).run();
        assertEquals(List.of(3, 1, 2), values);
        assertTrue(failed.isCompletedExceptionally());
        assertEquals(1, failures.size());
        assertTrue(closed.isDone());
        assertEquals(0, queue.snapshot().pendingTasks());
        assertEquals(0, queue.snapshot().activeKeys());
        assertTrue(queue.execute("player", () -> values.add(4)).isCompletedExceptionally());
    }

    @Test
    public void executorRejectionDoesNotStrandShutdown() {
        KeyedTaskQueue<String> queue = new KeyedTaskQueue<>(task -> { throw new RejectedExecutionException(); },
                (key, failure) -> {});
        assertTrue(queue.execute("player", () -> {}).isCompletedExceptionally());
        assertTrue(queue.closeAsync().isDone());
        assertEquals(1, queue.snapshot().failures());
        assertEquals(0, queue.snapshot().pendingTasks());
    }
}

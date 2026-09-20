package art.arcane.volmlib.nativelib.terrain;

import java.util.concurrent.CompletableFuture;

public interface WorldRuntimeExecution {
    boolean regionized();
    boolean primaryThread();
    CompletableFuture<Void> runGlobal(Runnable task);
    CompletableFuture<Void> runAsync(Runnable task);
    void reportFailure(String message, Throwable failure);
}

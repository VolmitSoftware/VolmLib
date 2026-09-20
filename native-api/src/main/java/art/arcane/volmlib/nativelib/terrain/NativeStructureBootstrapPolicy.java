package art.arcane.volmlib.nativelib.terrain;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface NativeStructureBootstrapPolicy {
    CompletableFuture<Void> start(Runnable claim, Supplier<CompletableFuture<Void>> preparation, Runnable activation);
    void failed(Throwable failure);
}

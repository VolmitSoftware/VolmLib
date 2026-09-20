package art.arcane.volmlib.nativelib.terrain;

import art.arcane.volmlib.nativelib.NativeBinding;
import org.bukkit.World;

import java.util.concurrent.CompletableFuture;

@NativeBinding("terrain.NativeWorldRuntimeImpl")
public interface NativeWorldRuntime {
    NativeWorldClock clock();
    NativeWorkerPool workers();
    boolean available();
    String flavor();
    String description();
    boolean supportsRegistryAccess();
    boolean supportsUnloadAsync();
    boolean isGlobalTickThread();
    World create(WorldRuntimeOptions options, WorldRuntimeExecution execution) throws ReflectiveOperationException;
    CompletableFuture<Boolean> unload(World world, boolean save, WorldRuntimeExecution execution);
}

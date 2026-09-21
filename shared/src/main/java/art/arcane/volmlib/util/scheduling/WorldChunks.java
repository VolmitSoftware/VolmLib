package art.arcane.volmlib.util.scheduling;

import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

public final class WorldChunks {
    private WorldChunks() {
    }

    public static CompletableFuture<Boolean> loadExisting(Plugin plugin, World world, int x, int z) {
        try {
            Method method = world.getClass().getMethod("getChunkAtAsync", int.class, int.class, boolean.class);
            Object result = method.invoke(world, x, z, false);
            if (!(result instanceof CompletableFuture<?> future)) {
                return CompletableFuture.failedFuture(new IllegalStateException("getChunkAtAsync did not return a future"));
            }
            return future.thenApply(chunk -> chunk != null);
        } catch (NoSuchMethodException exception) {
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            if (!FoliaScheduler.runRegion(plugin, world, x, z, () -> {
                try {
                    result.complete(world.loadChunk(x, z, false));
                } catch (RuntimeException failure) {
                    result.completeExceptionally(failure);
                }
            })) {
                result.complete(false);
            }
            return result;
        } catch (InvocationTargetException exception) {
            return CompletableFuture.failedFuture(exception.getCause());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }
}

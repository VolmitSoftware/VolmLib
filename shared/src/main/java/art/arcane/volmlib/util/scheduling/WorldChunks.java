package art.arcane.volmlib.util.scheduling;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

public final class WorldChunks {
    private static final ClassValue<AsyncChunkAccess> ASYNC_ACCESS = new ClassValue<>() {
        @Override
        protected AsyncChunkAccess computeValue(Class<?> type) {
            try {
                return new AsyncChunkAccess(type.getMethod("getChunkAtAsync", int.class, int.class, boolean.class));
            } catch (NoSuchMethodException exception) {
                return new AsyncChunkAccess(null);
            }
        }
    };

    private WorldChunks() {
    }

    public static CompletableFuture<Chunk> load(Plugin plugin, World world, int x, int z, boolean generate) {
        Method method = ASYNC_ACCESS.get(world.getClass()).method();
        if (method == null) {
            return loadOnOwner(plugin, world, x, z, generate);
        }
        try {
            Object result = method.invoke(world, x, z, generate);
            if (!(result instanceof CompletableFuture<?> future)) {
                return CompletableFuture.failedFuture(new IllegalStateException("getChunkAtAsync did not return a future"));
            }
            return future.thenApply(chunk -> (Chunk) chunk);
        } catch (InvocationTargetException exception) {
            return CompletableFuture.failedFuture(exception.getCause());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private static CompletableFuture<Chunk> loadOnOwner(Plugin plugin, World world, int x, int z, boolean generate) {
        CompletableFuture<Chunk> result = new CompletableFuture<>();
        try {
            if (!FoliaScheduler.runRegion(plugin, world, x, z, () -> {
                if (result.isDone()) {
                    return;
                }
                try {
                    result.complete(world.loadChunk(x, z, generate) ? world.getChunkAt(x, z) : null);
                } catch (RuntimeException failure) {
                    result.completeExceptionally(failure);
                }
            })) {
                result.completeExceptionally(new RejectedExecutionException("Chunk load scheduling rejected"));
            }
        } catch (RuntimeException failure) {
            result.completeExceptionally(failure);
        }
        return result;
    }

    private record AsyncChunkAccess(Method method) {
    }
}

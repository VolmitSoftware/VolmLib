package art.arcane.volmlib.util.scheduling;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

public final class EntityTeleports {
    private EntityTeleports() {
    }

    public static CompletableFuture<Boolean> teleport(Plugin plugin, Entity entity, Location destination,
                                                       PlayerTeleportEvent.TeleportCause cause) {
        try {
            Method method = entity.getClass().getMethod("teleportAsync", Location.class, PlayerTeleportEvent.TeleportCause.class);
            Object result = method.invoke(entity, destination, cause);
            if (!(result instanceof CompletableFuture<?> future)) {
                return CompletableFuture.failedFuture(new IllegalStateException("teleportAsync did not return a future"));
            }
            return future.thenApply(Boolean.TRUE::equals);
        } catch (NoSuchMethodException exception) {
            if (FoliaScheduler.isFolia(plugin)) {
                return CompletableFuture.failedFuture(new IllegalStateException("Folia requires asynchronous entity teleport support", exception));
            }
            try {
                return CompletableFuture.completedFuture(entity.teleport(destination, cause));
            } catch (RuntimeException failure) {
                return CompletableFuture.failedFuture(failure);
            }
        } catch (InvocationTargetException exception) {
            return CompletableFuture.failedFuture(exception.getCause());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }
}

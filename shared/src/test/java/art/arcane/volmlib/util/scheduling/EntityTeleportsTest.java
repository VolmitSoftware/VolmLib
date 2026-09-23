package art.arcane.volmlib.util.scheduling;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EntityTeleportsTest {
    @Test
    public void asyncCompletionReportsCancellationRatherThanOnlyDispatch() {
        Plugin plugin = mock(Plugin.class);
        AsyncEntity entity = mock(AsyncEntity.class);
        Location location = new Location(null, 0, 64, 0);
        CompletableFuture<Boolean> completion = new CompletableFuture<>();
        when(entity.teleportAsync(location, PlayerTeleportEvent.TeleportCause.PLUGIN)).thenReturn(completion);
        CompletableFuture<Boolean> result = EntityTeleports.teleport(plugin, entity, location, PlayerTeleportEvent.TeleportCause.PLUGIN);
        assertFalse(result.isDone());
        completion.complete(false);
        assertFalse(result.join());
    }

    @Test
    public void propagatesAsyncFailures() {
        Plugin plugin = mock(Plugin.class);
        AsyncEntity entity = mock(AsyncEntity.class);
        Location location = new Location(null, 0, 64, 0);
        when(entity.teleportAsync(location, PlayerTeleportEvent.TeleportCause.PLUGIN))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Teleport failed")));
        assertTrue(EntityTeleports.teleport(plugin, entity, location, PlayerTeleportEvent.TeleportCause.PLUGIN).isCompletedExceptionally());
    }

    public interface AsyncEntity extends Entity {
        CompletableFuture<Boolean> teleportAsync(Location location, PlayerTeleportEvent.TeleportCause cause);
    }
}

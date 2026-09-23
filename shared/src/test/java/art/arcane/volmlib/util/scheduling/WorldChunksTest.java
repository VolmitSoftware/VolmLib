package art.arcane.volmlib.util.scheduling;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WorldChunksTest {
    @Test
    public void waitsForAsyncLoadingAndReportsMissingChunks() {
        Plugin plugin = mock(Plugin.class);
        AsyncWorld world = mock(AsyncWorld.class);
        CompletableFuture<Chunk> loading = new CompletableFuture<>();
        when(world.getChunkAtAsync(2, 3, false)).thenReturn(loading);
        CompletableFuture<Boolean> result = WorldChunks.loadExisting(plugin, world, 2, 3);
        assertFalse(result.isDone());
        loading.complete(null);
        assertFalse(result.join());
    }

    @Test
    public void propagatesAsyncLoadingFailures() {
        Plugin plugin = mock(Plugin.class);
        AsyncWorld world = mock(AsyncWorld.class);
        when(world.getChunkAtAsync(2, 3, false)).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Load failed")));
        assertTrue(WorldChunks.loadExisting(plugin, world, 2, 3).isCompletedExceptionally());
    }

    public interface AsyncWorld extends World {
        CompletableFuture<Chunk> getChunkAtAsync(int x, int z, boolean generate);
    }
}

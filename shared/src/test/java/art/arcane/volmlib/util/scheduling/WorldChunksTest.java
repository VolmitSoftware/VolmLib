package art.arcane.volmlib.util.scheduling;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WorldChunksTest {
    @Test
    public void waitsForAsyncLoadingAndReportsMissingChunksWithoutGeneration() {
        Plugin plugin = mock(Plugin.class);
        AsyncWorld world = mock(AsyncWorld.class);
        CompletableFuture<Chunk> loading = new CompletableFuture<>();
        when(world.getChunkAtAsync(2, 3, false)).thenReturn(loading);
        CompletableFuture<Chunk> result = WorldChunks.load(plugin, world, 2, 3, false);
        assertFalse(result.isDone());
        loading.complete(null);
        assertNull(result.join());
        verify(world).getChunkAtAsync(2, 3, false);
    }

    @Test
    public void preservesGenerationPermissionAndReturnsTheLoadedChunk() {
        Plugin plugin = mock(Plugin.class);
        AsyncWorld world = mock(AsyncWorld.class);
        Chunk chunk = mock(Chunk.class);
        when(world.getChunkAtAsync(2, 3, true)).thenReturn(CompletableFuture.completedFuture(chunk));
        assertSame(chunk, WorldChunks.load(plugin, world, 2, 3, true).join());
    }

    @Test
    public void cancellationDoesNotCancelTheServersLoadFuture() {
        Plugin plugin = mock(Plugin.class);
        AsyncWorld world = mock(AsyncWorld.class);
        CompletableFuture<Chunk> loading = new CompletableFuture<>();
        when(world.getChunkAtAsync(2, 3, true)).thenReturn(loading);
        CompletableFuture<Chunk> result = WorldChunks.load(plugin, world, 2, 3, true);
        result.cancel(false);
        assertFalse(loading.isCancelled());
        assertTrue(loading.complete(mock(Chunk.class)));
        assertTrue(result.isCancelled());
    }

    @Test
    public void propagatesAsyncLoadingFailures() {
        Plugin plugin = mock(Plugin.class);
        AsyncWorld world = mock(AsyncWorld.class);
        when(world.getChunkAtAsync(2, 3, false)).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Load failed")));
        assertTrue(WorldChunks.load(plugin, world, 2, 3, false).isCompletedExceptionally());
    }

    public interface AsyncWorld extends World {
        CompletableFuture<Chunk> getChunkAtAsync(int x, int z, boolean generate);
    }
}

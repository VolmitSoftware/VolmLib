package art.arcane.volmlib.nativelib.terrain;

import org.bukkit.World;

import java.util.concurrent.CompletableFuture;

public interface SnapshotPolicy<T> extends TerrainSnapshotPolicy<T> {
    CompletableFuture<Void> runRegion(Region region, Runnable action);

    record Region(World world, int chunkX, int chunkZ) {
    }
}

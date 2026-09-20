package art.arcane.volmlib.nativelib.terrain;

import art.arcane.volmlib.nativelib.NativeBinding;
import org.bukkit.World;

import java.util.concurrent.CompletableFuture;

@NativeBinding("terrain.NativeTerrainSnapshotsImpl")
public interface NativeTerrainSnapshots {
    <T> CompletableFuture<T> capture(CaptureRequest request, SnapshotPolicy<T> policy);

    CompletableFuture<Void> flush(World world, SnapshotPolicy<?> policy);

    record CaptureRequest(World world, int chunkX, int chunkZ, int minimumY, int height) {
    }
}

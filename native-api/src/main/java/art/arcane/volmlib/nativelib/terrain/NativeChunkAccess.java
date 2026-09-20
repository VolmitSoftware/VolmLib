package art.arcane.volmlib.nativelib.terrain;

import art.arcane.volmlib.nativelib.NativeBinding;
import org.bukkit.Chunk;
import org.bukkit.World;

@NativeBinding("terrain.NativeChunkAccessImpl")
public interface NativeChunkAccess {
    boolean forceEvictChunk(World world, int chunkX, int chunkZ);

    boolean saveAndUnloadChunk(World world, int chunkX, int chunkZ);

    boolean pollChunkTask(World world);

    void flushChunkIO(World world);

    void reconcileNativeStructurePois(Chunk chunk);

    boolean clearChunkBlocks(Chunk chunk);
}

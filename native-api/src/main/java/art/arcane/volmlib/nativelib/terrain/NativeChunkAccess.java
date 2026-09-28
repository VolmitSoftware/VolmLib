package art.arcane.volmlib.nativelib.terrain;

import art.arcane.volmlib.nativelib.NativeBinding;
import org.bukkit.Chunk;
import org.bukkit.World;

@NativeBinding("terrain.NativeChunkAccessImpl")
public interface NativeChunkAccess {
    boolean forceEvictChunk(World world, int chunkX, int chunkZ);

    boolean saveAndUnloadChunk(World world, int chunkX, int chunkZ);

    /**
     * Chebyshev radius of the chunks a FULL chunk request loads around itself, or -1 when
     * {@link #retainChunk} is unsupported.
     */
    int fullChunkDependencyRadius();

    /**
     * Keeps the chunk resident at its current status without requesting more generation than a
     * neighbouring FULL request already does. Idempotent per position.
     */
    boolean retainChunk(World world, int chunkX, int chunkZ);

    boolean releaseChunk(World world, int chunkX, int chunkZ);

    boolean pollChunkTask(World world);

    void flushChunkIO(World world);

    void reconcileNativeStructurePois(Chunk chunk);

    boolean clearChunkBlocks(Chunk chunk);
}

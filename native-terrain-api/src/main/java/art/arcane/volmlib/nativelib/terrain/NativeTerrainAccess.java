package art.arcane.volmlib.nativelib.terrain;

import art.arcane.volmlib.nativelib.NativeBinding;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

@NativeBinding("terrain.NativeTerrainAccessImpl")
public interface NativeTerrainAccess extends NativeBiomeAccess {
    int[] placeStructure(World world, int chunkX, int chunkZ, String structureKey, long seed, int maxSpan);

    boolean applyChunkBlocks(Chunk chunk, BukkitTerrainBuffer data);

    boolean applyChunkDataBlocks(ChunkGenerator.ChunkData chunkData, NativeBlockVolume data);
}

package art.arcane.volmlib.nativelib.terrain;

import org.bukkit.generator.ChunkGenerator.ChunkData;

public interface BukkitTerrainBuffer {
    NativeBiome getBiome(int x, int y, int z);

    void setBiome(int x, int y, int z, NativeBiome biome);

    int getMinHeight();

    int getMaxHeight();

    void setBlock(int x, int y, int z, NativeBlockState blockData);

    void setRegion(int minimumX, int minimumY, int minimumZ,
                   int maximumX, int maximumY, int maximumZ, NativeBlockState blockData);

    NativeBlockState getBlockData(int x, int y, int z);

    ChunkData getChunkData();
}

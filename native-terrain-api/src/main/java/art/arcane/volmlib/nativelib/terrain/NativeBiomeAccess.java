package art.arcane.volmlib.nativelib.terrain;

import art.arcane.volmlib.nativelib.NativeBinding;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.nbt.mca.NBTWorldSupport;
import art.arcane.volmlib.util.nbt.mca.palette.MCABiomeContainer;
import art.arcane.volmlib.util.nbt.mca.palette.MCAPaletteAccess;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

@NativeBinding("terrain.NativeBiomeAccessImpl")
public interface NativeBiomeAccess extends NativeRegistryAccess {
    int getBiomeId(Biome biome);

    int getBiomeId(Location location);

    int getBiomeId(String key);

    boolean hasBiome(String key);

    String getTrueBiomeBaseKey(Location location);

    MCABiomeContainer newBiomeContainer(int minimumY, int maximumY);

    MCABiomeContainer newBiomeContainer(int minimumY, int maximumY, int[] data);

    int countCustomBiomes();

    MCAPaletteAccess createPalette(NBTWorldSupport.BlockStateCodec<BlockData> codec);

    void injectBiomesFromMantle(Chunk chunk, Mantle<Matter> mantle);
}

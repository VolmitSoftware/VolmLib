package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

import java.util.List;

public interface NativeFeatureBiomeSource {
    long packGeneration();
    List<Holder<Biome>> orderedPossibleBiomes();
    Holder<Biome> registeredBiome(String key);
}

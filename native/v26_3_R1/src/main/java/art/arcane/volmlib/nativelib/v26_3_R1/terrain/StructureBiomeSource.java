package art.arcane.volmlib.nativelib.v26_3_R1.terrain;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

import java.util.Set;

public interface StructureBiomeSource {
    Set<Holder<Biome>> possibleStructureBiomes();
}

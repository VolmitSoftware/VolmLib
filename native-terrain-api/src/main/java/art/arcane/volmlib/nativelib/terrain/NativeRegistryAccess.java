package art.arcane.volmlib.nativelib.terrain;

import art.arcane.volmlib.nativelib.NativeBinding;
import art.arcane.volmlib.util.collection.KList;
import org.bukkit.World;
import org.bukkit.block.Biome;

@NativeBinding("terrain.NativeRegistryAccessImpl")
public interface NativeRegistryAccess extends NativeBlockAccess {
    KList<Biome> getBiomes();

    KList<String> getStructureKeys();

    KList<String> getJigsawStructureKeys();

    KList<String> getTemplatePoolKeys();

    JigsawSourceMetadata getJigsawSourceMetadata(String structureKey);

    int getTemplatePoolHorizontalSpan(String templatePoolKey);

    int getJigsawStartPoolHorizontalSpan(String structureKey, String templatePoolKey);

    KList<String> getStructureSetKeys();

    KList<String> getReachableStructureKeys(World world);

    KList<String> getStructureBiomeKeys(String structureKey);

    KList<String> getPossibleBiomeKeys(World world);

    KList<String> getObjectFeatureKeys();

    boolean placeFeature(World world, int x, int y, int z, String featureKey, long seed);
}

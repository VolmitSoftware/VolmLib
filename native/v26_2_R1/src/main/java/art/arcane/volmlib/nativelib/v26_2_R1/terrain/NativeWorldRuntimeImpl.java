package art.arcane.volmlib.nativelib.v26_2_R1.terrain;

import art.arcane.volmlib.nativelib.common.terrain.ReflectiveWorldRuntime;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import org.bukkit.NamespacedKey;

import java.util.List;
import java.util.Optional;

public final class NativeWorldRuntimeImpl extends ReflectiveWorldRuntime {
    public NativeWorldRuntimeImpl() {
    }

    @Override
    protected Object createLevelStem(Object registryAccess, NamespacedKey dimensionTypeKey) {
        RegistryAccess access = (RegistryAccess) registryAccess;
        Identifier key = Identifier.fromNamespaceAndPath(dimensionTypeKey.getNamespace(), dimensionTypeKey.getKey());
        Holder.Reference<DimensionType> dimensionType = access.lookupOrThrow(Registries.DIMENSION_TYPE)
                .getOrThrow(ResourceKey.create(Registries.DIMENSION_TYPE, key));
        FlatLevelGeneratorSettings settings = new FlatLevelGeneratorSettings(Optional.empty(),
                access.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.THE_VOID), List.of());
        settings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.AIR));
        settings.updateLayers();
        return new LevelStem(dimensionType, new FlatLevelSource(settings));
    }
}

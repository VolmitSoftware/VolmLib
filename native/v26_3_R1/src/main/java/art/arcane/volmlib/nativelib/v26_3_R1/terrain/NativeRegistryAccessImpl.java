package art.arcane.volmlib.nativelib.v26_3_R1.terrain;

import art.arcane.volmlib.nativelib.terrain.JigsawSourceMetadata;
import art.arcane.volmlib.nativelib.terrain.NativeRegistryAccess;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeJigsawMetadata;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.VanillaStructureBiomes;
import art.arcane.volmlib.util.collection.KList;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.AbstractHugeMushroomFeature;
import net.minecraft.world.level.levelgen.feature.FallenTreeFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.generator.ChunkGenerator;

public class NativeRegistryAccessImpl extends NativeBlockAccessImpl implements NativeRegistryAccess {
    private static final Logger LOG = Logger.getLogger("VolmLib-Native");

    @Override
    public KList<Biome> getBiomes() {
        KList<Biome> biomes = new KList<>();
        for (Biome biome : org.bukkit.Registry.BIOME) {
            biomes.add(biome);
        }
        return biomes;
    }

    @Override
    public KList<String> getStructureKeys() {
        KList<String> keys = new KList<>();
        try {
            registry().lookupOrThrow(Registries.STRUCTURE).keySet().forEach(k -> keys.add(k.toString()));
        } catch (RuntimeException e) {
            throw new IllegalStateException("Iris failed to read registered structure keys from the Minecraft registry", e);
        }
        return keys;
    }

    @Override
    public KList<String> getJigsawStructureKeys() {
        KList<String> keys = new KList<>();
        try {
            Registry<Structure> structures = registry().lookupOrThrow(Registries.STRUCTURE);
            for (Map.Entry<ResourceKey<Structure>, Structure> entry : structures.entrySet()) {
                if (entry.getValue() instanceof JigsawStructure) {
                    keys.add(entry.getKey().identifier().toString());
                }
            }
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "Iris failed to read registered jigsaw structure keys from the Minecraft registry", e);
        }
        return keys;
    }

    @Override
    public KList<String> getTemplatePoolKeys() {
        KList<String> keys = new KList<>();
        try {
            registry().lookupOrThrow(Registries.TEMPLATE_POOL).keySet()
                    .forEach(key -> keys.add(key.toString()));
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "Iris failed to read registered template pool keys from the Minecraft registry", e);
        }
        return keys;
    }

    @Override
    public JigsawSourceMetadata getJigsawSourceMetadata(String structureKey) {
        try {
            Identifier identifier = Identifier.tryParse(structureKey);
            if (identifier == null) {
                throw new IllegalArgumentException("Invalid registered structure key: " + structureKey);
            }
            Registry<Structure> structures = registry().lookupOrThrow(Registries.STRUCTURE);
            Structure structure = structures.getValue(identifier);
            if (structure == null) {
                throw new IllegalArgumentException("Registered structure does not exist: " + structureKey);
            }
            if (!(structure instanceof JigsawStructure jigsaw)) {
                throw new IllegalArgumentException("Registered structure is not a jigsaw: " + structureKey);
            }
            MinecraftServer server = ((CraftServer) Bukkit.getServer()).getHandle().getServer();
            return NativeJigsawMetadata.sourceMetadata(
                    registry(), server.getStructureTemplateManager(), jigsaw);
        } catch (RuntimeException error) {
            throw new IllegalStateException("Iris failed to resolve live jigsaw metadata for registered structure '"
                    + structureKey + "'", error);
        }
    }

    @Override
    public int getTemplatePoolHorizontalSpan(String templatePoolKey) {
        try {
            MinecraftServer server = ((CraftServer) Bukkit.getServer()).getHandle().getServer();
            return NativeJigsawMetadata.templatePoolHorizontalSpan(
                    registry(), server.getStructureTemplateManager(), templatePoolKey);
        } catch (RuntimeException error) {
            throw new IllegalStateException("Iris failed to resolve the live horizontal span for registered "
                    + "template pool '" + templatePoolKey + "'", error);
        }
    }

    @Override
    public int getJigsawStartPoolHorizontalSpan(String structureKey, String templatePoolKey) {
        try {
            Identifier identifier = Identifier.tryParse(structureKey);
            if (identifier == null) {
                throw new IllegalArgumentException("Invalid registered structure key: " + structureKey);
            }
            Structure structure = registry().lookupOrThrow(Registries.STRUCTURE).getValue(identifier);
            if (!(structure instanceof JigsawStructure jigsaw)) {
                throw new IllegalArgumentException("Registered structure is not a jigsaw: " + structureKey);
            }
            MinecraftServer server = ((CraftServer) Bukkit.getServer()).getHandle().getServer();
            return NativeJigsawMetadata.jigsawStartPoolHorizontalSpan(
                    registry(), server.getStructureTemplateManager(), jigsaw, templatePoolKey);
        } catch (RuntimeException error) {
            throw new IllegalStateException("Iris failed to resolve the effective start-pool span for registered "
                    + "jigsaw structure '" + structureKey + "' and pool '" + templatePoolKey + "'", error);
        }
    }

    @Override
    public KList<String> getStructureSetKeys() {
        KList<String> keys = new KList<>();
        try {
            registry().lookupOrThrow(Registries.STRUCTURE_SET).keySet().forEach(k -> keys.add(k.toString()));
        } catch (RuntimeException e) {
            throw new IllegalStateException("Iris failed to read registered structure-set keys from the Minecraft registry", e);
        }
        return keys;
    }

    @Override
    public KList<String> getReachableStructureKeys(World world) {
        KList<String> keys = new KList<>();
        try {
            ServerLevel level = ((CraftWorld) world).getHandle();
            BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
            keys.addAll(VanillaStructureBiomes.reachableStructureKeys(level, source));
        } catch (RuntimeException e) {
            throw new IllegalStateException("Iris failed to resolve reachable structures for Bukkit world '"
                    + (world == null ? "<null>" : world.getName()) + "'", e);
        }
        return keys;
    }

    @Override
    public KList<String> getStructureBiomeKeys(String structureKey) {
        KList<String> keys = new KList<>();
        try {
            RegistryAccess access = registry();
            if (access == null) {
                throw new IllegalStateException("Minecraft registry access is unavailable");
            }
            keys.addAll(VanillaStructureBiomes.structureBiomeKeys(access, structureKey));
        } catch (RuntimeException e) {
            throw new IllegalStateException("Iris failed to resolve biome keys for registered structure '"
                    + structureKey + "'", e);
        }
        return keys;
    }

    @Override
    public KList<String> getPossibleBiomeKeys(World world) {
        KList<String> keys = new KList<>();
        try {
            ServerLevel level = ((CraftWorld) world).getHandle();
            BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
            keys.addAll(VanillaStructureBiomes.possibleBiomeKeys(source));
        } catch (RuntimeException e) {
            throw new IllegalStateException("Iris failed to resolve possible structure biome keys for Bukkit world '"
                    + (world == null ? "<null>" : world.getName()) + "'", e);
        }
        return keys;
    }

    @Override
    public KList<String> getObjectFeatureKeys() {
        KList<String> keys = new KList<>();
        try {
            Registry<Feature> reg = registry().lookupOrThrow(Registries.FEATURE);
            for (Identifier id : reg.keySet()) {
                Feature cf = reg.getValue(id);
                if (cf == null) {
                    continue;
                }
                String group = classifyFeature(cf);
                if (group == null) {
                    continue;
                }
                keys.add(group + "|" + id);
            }
        } catch (Throwable e) {
            LOG.log(Level.SEVERE, "Native registry operation failed", e);
        }
        return keys;
    }

    private static String classifyFeature(Feature feature) {
        if (feature instanceof TreeFeature) {
            return "trees";
        }
        if (feature instanceof FallenTreeFeature) {
            return "fallen_trees";
        }
        if (feature instanceof AbstractHugeMushroomFeature) {
            return "mushrooms";
        }
        return null;
    }

    @Override
    public boolean placeFeature(World world, int x, int y, int z, String featureKey, long seed) {
        try {
            ServerLevel level = ((CraftWorld) world).getHandle();
            net.minecraft.world.level.chunk.ChunkGenerator generator = level.getChunkSource().getGenerator();
            Registry<Feature> reg = registry().lookupOrThrow(Registries.FEATURE);
            Feature cf = reg.getValue(Identifier.parse(featureKey));
            if (cf == null) {
                return false;
            }
            WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(seed));
            return cf.place(level, generator, random, new BlockPos(x, y, z));
        } catch (Throwable e) {
            LOG.log(Level.SEVERE, "Native registry operation failed", e);
            return false;
        }
    }
}

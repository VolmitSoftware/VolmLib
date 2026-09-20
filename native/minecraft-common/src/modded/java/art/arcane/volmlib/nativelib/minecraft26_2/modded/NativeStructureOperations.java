/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedPlatformWorld;

import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeJigsawMetadata;

import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureGenerationException;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureGenerationKeys;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureFactory;
import art.arcane.volmlib.nativelib.terrain.JigsawSourceMetadata;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.AbstractHugeMushroomFeature;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.FallenTreeFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.Consumer;

public final class NativeStructureOperations {
    private final Options options;

    public NativeStructureOperations(Options options) {
        this.options = Objects.requireNonNull(options);
    }

    public List<String> structureKeys() {
        return registryKeys(Registries.STRUCTURE);
    }

    public List<String> jigsawStructureKeys() {
        List<String> keys = new ArrayList<>();
        MinecraftServer instance = requireServer("read registered jigsaw structures");
        Registry<Structure> structures = instance.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (Map.Entry<ResourceKey<Structure>, Structure> entry : structures.entrySet()) {
            if (entry.getValue() instanceof JigsawStructure) {
                keys.add(entry.getKey().identifier().toString());
            }
        }
        return keys;
    }

    public List<String> templatePoolKeys() {
        return registryKeys(Registries.TEMPLATE_POOL);
    }

    public JigsawSourceMetadata jigsawSourceMetadata(String structureKey) {
        MinecraftServer instance = requireServer("resolve live jigsaw metadata for registered structure '"
                + structureKey + "'");
        try {
            Identifier identifier = Identifier.tryParse(structureKey);
            if (identifier == null) {
                throw new IllegalArgumentException("Invalid registered structure key: " + structureKey);
            }
            Registry<Structure> registry = instance.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            Structure structure = registry.getValue(identifier);
            if (structure == null) {
                throw new IllegalArgumentException("Registered structure does not exist: " + structureKey);
            }
            if (!(structure instanceof JigsawStructure jigsaw)) {
                throw new IllegalArgumentException("Registered structure is not a jigsaw: " + structureKey);
            }
            return NativeJigsawMetadata.sourceMetadata(
                    instance.registryAccess(), instance.getStructureManager(), jigsaw);
        } catch (RuntimeException error) {
            throw new IllegalStateException("Failed to resolve live jigsaw metadata for registered structure '"
                    + structureKey + "' from the modded structure registry", error);
        }
    }

    public int templatePoolHorizontalSpan(String templatePoolKey) {
        MinecraftServer instance = requireServer("resolve the live horizontal span for registered template pool '"
                + templatePoolKey + "'");
        try {
            return NativeJigsawMetadata.templatePoolHorizontalSpan(
                    instance.registryAccess(), instance.getStructureManager(), templatePoolKey);
        } catch (RuntimeException error) {
            throw new IllegalStateException("Failed to resolve the live horizontal span for registered "
                    + "template pool '" + templatePoolKey + "' from the modded template-pool registry", error);
        }
    }

    public int jigsawStartPoolHorizontalSpan(String structureKey, String templatePoolKey) {
        MinecraftServer instance = requireServer("resolve the effective start-pool span for registered jigsaw '"
                + structureKey + "'");
        try {
            Identifier identifier = Identifier.tryParse(structureKey);
            if (identifier == null) {
                throw new IllegalArgumentException("Invalid registered structure key: " + structureKey);
            }
            Structure structure = instance.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                    .getValue(identifier);
            if (!(structure instanceof JigsawStructure jigsaw)) {
                throw new IllegalArgumentException("Registered structure is not a jigsaw: " + structureKey);
            }
            return NativeJigsawMetadata.jigsawStartPoolHorizontalSpan(
                    instance.registryAccess(), instance.getStructureManager(), jigsaw, templatePoolKey);
        } catch (RuntimeException error) {
            throw new IllegalStateException("Failed to resolve the effective start-pool span for registered "
                    + "jigsaw structure '" + structureKey + "' and pool '" + templatePoolKey
                    + "' from the modded registries", error);
        }
    }

    public List<String> structureSetKeys() {
        return registryKeys(Registries.STRUCTURE_SET);
    }

    public List<String> structureBiomeKeys(String structureKey) {
        List<String> keys = new ArrayList<>();
        MinecraftServer instance = requireServer("resolve biome keys for registered structure '" + structureKey + "'");
        try {
            Identifier identifier = Identifier.tryParse(structureKey);
            if (identifier == null) {
                throw new IllegalArgumentException("Registered structure key is invalid: " + structureKey);
            }
            Registry<Structure> registry = instance.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            Structure structure = registry.getValue(identifier);
            if (structure == null) {
                throw new IllegalArgumentException("Registered structure does not exist: " + structureKey);
            }
            boolean hasFilterEntries = false;
            for (Holder<Biome> holder : structure.biomes()) {
                hasFilterEntries = true;
                Optional<ResourceKey<Biome>> key = holder.unwrapKey();
                if (key.isPresent()) {
                    keys.add(key.get().identifier().toString());
                }
            }
            // An empty biome filter is legal datapack content (opt-in structures whose tags only
            // reference absent modded biomes); the structure is unreachable, not an error state.
            if (keys.isEmpty() && hasFilterEntries) {
                throw new IllegalStateException("Registered structure '" + structureKey
                        + "' has biome filter entries but none resolve to registered biome keys");
            }
        } catch (RuntimeException error) {
            throw new IllegalStateException("Failed to resolve biome keys for registered structure '"
                    + structureKey + "' from the modded structure registry", error);
        }
        return keys;
    }

    public List<String> objectFeatureKeys() {
        List<String> keys = new ArrayList<>();
        try {
            NativeModdedServer host = options.server().get();
            MinecraftServer instance = host == null ? null : host.server();
            if (instance == null) {
                return keys;
            }
            Registry<ConfiguredFeature<?, ?>> registry = instance.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE);
            for (Identifier identifier : registry.keySet()) {
                ConfiguredFeature<?, ?> configuredFeature = registry.getValue(identifier);
                if (configuredFeature == null) {
                    continue;
                }
                String group = classifyFeature(configuredFeature.feature());
                if (group != null) {
                    keys.add(group + "|" + identifier);
                }
            }
        } catch (Throwable error) {
            options.errors().accept(error);
        }
        return keys;
    }

    public List<String> reachableStructureKeys(NativeWorld world) {
        ServerLevel level = requireLevel(world, "resolve reachable structures");
        try {
            BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
            Set<String> possibleBiomes = possibleBiomeKeys(source);
            return new ArrayList<>(NativeStructureGenerationKeys.reachable(level, possibleBiomes));
        } catch (RuntimeException error) {
            throw new IllegalStateException("Failed to resolve reachable structures for modded level '"
                    + level.dimension().identifier() + "'", error);
        }
    }

    public List<String> possibleBiomeKeys(NativeWorld world) {
        List<String> keys = new ArrayList<>();
        ServerLevel level = requireLevel(world, "resolve possible structure biome keys");
        try {
            keys.addAll(possibleBiomeKeys(level.getChunkSource().getGenerator().getBiomeSource()));
        } catch (RuntimeException error) {
            throw new IllegalStateException("Failed to resolve possible structure biome keys for modded level '"
                    + level.dimension().identifier() + "'", error);
        }
        return keys;
    }

    /**
     * Swallow contract: a feature that refuses to place, or that throws while placing, is a normal outcome for
     * the importer that drives this - it probes many keys against arbitrary terrain and treats false as "not
     * here". So every Throwable is reported through the configured error sink and answered with false; placement never
     * escalates to the caller. placeStructure below deliberately does the opposite (it rethrows with context)
     * because a failed structure capture means the capture pass is broken, not that the site was unsuitable.
     */
    public boolean placeFeature(NativeWorld world, int x, int y, int z, String featureKey, long seed) {
        try {
            ServerLevel level = level(world);
            Identifier identifier = Identifier.tryParse(featureKey);
            if (level == null || identifier == null) {
                return false;
            }
            ChunkGenerator generator = level.getChunkSource().getGenerator();
            Registry<ConfiguredFeature<?, ?>> registry = level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE);
            ConfiguredFeature<?, ?> configuredFeature = registry.getValue(identifier);
            if (configuredFeature == null) {
                return false;
            }
            WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(seed));
            return configuredFeature.place(level, generator, random, new BlockPos(x, y, z));
        } catch (Throwable error) {
            options.errors().accept(error);
            return false;
        }
    }

    public int[] placeStructure(NativeWorld world, int chunkX, int chunkZ, String structureKey, long seed, int maxSpan) {
        try {
            ServerLevel level = level(world);
            Identifier identifier = Identifier.tryParse(structureKey);
            if (level == null || identifier == null) {
                return null;
            }
            if (!level.getServer().isSameThread()) {
                throw new IllegalStateException("Structure placement loads chunks synchronously and must run on the server thread, not "
                        + Thread.currentThread().getName());
            }
            ChunkGenerator generator = level.getChunkSource().getGenerator();
            Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            Structure structure = registry.getValue(identifier);
            if (structure == null) {
                return null;
            }
            Holder<Structure> holder = registry.wrapAsHolder(structure);
            RandomState randomState = level.getChunkSource().randomState();
            StructureTemplateManager templateManager = level.getStructureManager();
            StructureManager structureManager = level.structureManager();
            BiomeSource biomeSource = generator.getBiomeSource();
            ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
            StructureStart start = structure.generate(
                    holder,
                    level.dimension(),
                    level.registryAccess(),
                    generator,
                    biomeSource,
                    randomState,
                    templateManager,
                    seed,
                    chunkPos,
                    0,
                    level,
                    biome -> true);
            if (start == null || !start.isValid()) {
                return null;
            }
            BoundingBox box = start.getBoundingBox();
            if (!isWithinSpan(box, maxSpan)) {
                return null;
            }
            int placementChunks = chunkGridSize(box);
            if (placementChunks > options.maxPlacementChunks()) {
                options.warnings().accept("Skipped structure capture for " + structureKey + " at " + chunkX + "," + chunkZ
                        + ": bounding box spans " + placementChunks + " chunks, cap is " + options.maxPlacementChunks());
                return null;
            }
            placeChunks(level, structureManager, generator, start, box, seed);
            return bounds(box);
        } catch (RuntimeException error) {
            throw NativeStructureGenerationException.failure(
                    "capture placement", structureKey, chunkX, chunkZ, error);
        }
    }

    public boolean supportsStructurePlacement() {
        return true;
    }

    static String classifyFeature(Feature<?> feature) {
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

    static boolean isWithinSpan(BoundingBox box, int maxSpan) {
        return maxSpan <= 0
                || box.getXSpan() <= maxSpan && box.getYSpan() <= maxSpan && box.getZSpan() <= maxSpan;
    }

    /**
     * Number of chunks placeChunks would load for this bounding box. Computed in long arithmetic because a
     * corrupt box can span the whole coordinate range and the product overflows int.
     */
    static int chunkGridSize(BoundingBox box) {
        long spanX = ((long) (box.maxX() >> 4)) - (box.minX() >> 4) + 1L;
        long spanZ = ((long) (box.maxZ() >> 4)) - (box.minZ() >> 4) + 1L;
        if (spanX <= 0L || spanZ <= 0L) {
            return 0;
        }
        long total = spanX * spanZ;
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    static int[] bounds(BoundingBox box) {
        return new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()};
    }

    private static Set<String> possibleBiomeKeys(BiomeSource source) {
        if (source == null) {
            throw new IllegalStateException("Minecraft chunk generator has no biome source");
        }
        Set<String> keys = new LinkedHashSet<>();
        for (Holder<Biome> holder : source.possibleBiomes()) {
            Optional<ResourceKey<Biome>> key = holder.unwrapKey();
            if (key.isPresent()) {
                keys.add(key.get().identifier().toString());
            }
        }
        if (keys.isEmpty()) {
            throw new IllegalStateException("Minecraft biome source exposes no registered possible biomes");
        }
        return keys;
    }

    private static ServerLevel level(NativeWorld world) {
        return world instanceof ModdedPlatformWorld moddedWorld ? moddedWorld.level() : null;
    }

    private static void placeChunks(ServerLevel level, StructureManager structureManager, ChunkGenerator generator,
                                    StructureStart start, BoundingBox box, long seed) {
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(seed));
        int minChunkX = box.minX() >> 4;
        int maxChunkX = box.maxX() >> 4;
        int minChunkZ = box.minZ() >> 4;
        int maxChunkZ = box.maxZ() >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
                ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
                BoundingBox chunkBox = new BoundingBox(
                        chunkPos.getMinBlockX(), box.minY(), chunkPos.getMinBlockZ(),
                        chunkPos.getMaxBlockX(), box.maxY(), chunkPos.getMaxBlockZ());
                start.placeInChunk(level, structureManager, generator, random, chunkBox, chunkPos);
            }
        }
    }

    private <T> List<String> registryKeys(ResourceKey<Registry<T>> registryKey) {
        List<String> keys = new ArrayList<>();
        MinecraftServer instance = requireServer("read registry '" + registryKey.identifier() + "'");
        try {
            Registry<T> registry = instance.registryAccess().lookupOrThrow(registryKey);
            for (Identifier identifier : registry.keySet()) {
                keys.add(identifier.toString());
            }
        } catch (RuntimeException error) {
            throw new IllegalStateException("Failed to read modded registry '"
                    + registryKey.identifier() + "'", error);
        }
        return keys;
    }

    private MinecraftServer requireServer(String operation) {
        MinecraftServer instance;
        try {
            NativeModdedServer host = options.server().get();
            instance = host == null ? null : host.server();
        } catch (RuntimeException error) {
            throw new IllegalStateException("Failed to access the Minecraft server to " + operation, error);
        }
        if (instance == null) {
            throw new IllegalStateException("Cannot " + operation + " before the Minecraft server is available");
        }
        return instance;
    }

    private static ServerLevel requireLevel(NativeWorld world, String operation) {
        ServerLevel level = level(world);
        if (level == null) {
            throw new IllegalStateException("Cannot " + operation
                    + " without a bound modded ServerLevel");
        }
        return level;
    }
    public record Options(Supplier<NativeModdedServer> server, int maxPlacementChunks,
                          Consumer<Throwable> errors, Consumer<String> warnings) {
        public Options {
            Objects.requireNonNull(server);
            Objects.requireNonNull(errors);
            Objects.requireNonNull(warnings);
            if (maxPlacementChunks <= 0) {
                throw new IllegalArgumentException("Placement chunk limit must be positive");
            }
        }
    }

}

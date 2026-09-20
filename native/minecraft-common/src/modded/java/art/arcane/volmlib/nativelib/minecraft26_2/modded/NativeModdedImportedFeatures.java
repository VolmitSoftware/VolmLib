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

import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntBinaryOperator;

import art.arcane.volmlib.nativelib.terrain.feature.NativeFeatureTable;
import art.arcane.volmlib.nativelib.terrain.feature.NativeImportedFeatureControl;
import art.arcane.volmlib.nativelib.terrain.feature.NativeImportedFeaturePolicy;
import net.minecraft.world.level.chunk.ChunkGenerator;
import java.util.Objects;

public final class NativeModdedImportedFeatures {
    private static final String CYCLE_MARKER = "Feature order cycle found";
    private final NativeFeatureBiomeSource biomeSource;
    private final PlacementObserver observer;

    public NativeModdedImportedFeatures(NativeFeatureBiomeSource biomeSource, PlacementObserver observer) {
        this.biomeSource = Objects.requireNonNull(biomeSource, "biomeSource");
        this.observer = Objects.requireNonNull(observer, "observer");
    }

    public BiomeGenerationSettings generationSettings(Holder<Biome> biome, NativeFeatureTable table) {
        return table == null ? biome.value().getGenerationSettings()
                : settingsFor(biome, ((FeatureTable) table).derivatives());
    }

    public NativeFeatureTable buildTable(NativeImportedFeaturePolicy<?> policy, NativeImportedFeatureControl control, long generation) {
        // Registry-ordered biome list. FeatureSorter's cycle detection walks it, so an unordered list makes
        // detection depend on JVM hash order and turns a real cycle into an intermittent one.
        Set<String> hostBiomeKeys = new LinkedHashSet<>();
        for (String key : policy.visibleBiomeKeys()) {
            addKey(hostBiomeKeys, key);
        }
        List<Holder<Biome>> biomes = new ArrayList<>();
        for (Holder<Biome> biome : biomeSource.orderedPossibleBiomes()) {
            String key = holderKey(biome);
            if (key != null && hostBiomeKeys.contains(key)) {
                biomes.add(biome);
            }
        }
        if (biomes.isEmpty()) {
            policy.error("importedFeatures is on but " + policy.dimensionKey() + " exposes no biomes; features off", null);
            return null;
        }
        Map<String, Holder<Biome>> byKey = new HashMap<>(biomes.size());
        for (Holder<Biome> biome : biomes) {
            String key = holderKey(biome);
            if (key != null) {
                byKey.put(key, biome);
            }
        }
        Map<String, Holder<Biome>> derivatives = customBiomeDerivatives(policy, byKey);
        List<FeatureSorter.StepFeatureData> steps;
        try {
            // Same inputs as vanilla's own memo, built here so it can be keyed on the Iris pack generation and
            // so the cycle failure lands at bind time.
            steps = FeatureSorter.buildFeaturesPerStep(biomes,
                    (Holder<Biome> biome) -> settingsFor(biome, derivatives).features(), true);
        } catch (IllegalStateException error) {
            String message = error.getMessage();
            if (message == null || !message.contains(CYCLE_MARKER)) {
                throw error;
            }
            policy.error("importedFeatures is off for " + policy.dimensionKey()
                    + ": the registered placed features cannot be ordered. " + message
                    + ". Remove or reorder the conflicting content, or leave importedFeatures.enabled false.", error);
            return null;
        }
        boolean filtered = control.hasFeatureFilter();
        return new FeatureTable(generation, policy, control, List.copyOf(biomes), Set.copyOf(biomes),
                Map.copyOf(byKey), steps, Map.copyOf(derivatives), filtered);
    }

    /**
     * Maps every generated Iris custom biome key onto the registry holder of its Iris biome's vanilla
     * derivative. The custom biome's own datapack JSON carries no features by design; this is the only place
     * the vanilla feature set enters.
     */
    private Map<String, Holder<Biome>> customBiomeDerivatives(NativeImportedFeaturePolicy<?> policy,
                                                            Map<String, Holder<Biome>> byKey) {
        Map<String, Holder<Biome>> derivatives = new HashMap<>();
        for (NativeImportedFeaturePolicy.DerivativeGroup group : policy.customBiomeDerivatives()) {
            String derivativeKey = normalizeKey(group.derivativeKey());
            Holder<Biome> derivative = byKey.get(derivativeKey);
            if (derivative == null) {
                derivative = biomeSource.registeredBiome(derivativeKey);
            }
            if (derivative == null) {
                policy.warn("importedFeatures: vanilla derivative " + derivativeKey + " of biome " + group.source()
                        + " is not registered; its custom biomes generate no imported features");
                continue;
            }
            for (String customKey : group.customKeys()) {
                derivatives.put(customKey, derivative);
            }
        }
        return derivatives;
    }

    private static BiomeGenerationSettings settingsFor(Holder<Biome> biome,
                                                       Map<String, Holder<Biome>> derivatives) {
        Holder<Biome> mapped = derivatives.get(holderKey(biome));
        return mapped == null
                ? biome.value().getGenerationSettings()
                : mapped.value().getGenerationSettings();
    }

    /**
     * Runs the vanilla placed-feature pass for one chunk, on the calling worldgen thread. A no-op while the
     * control is disabled or the table degraded.
     */
    public void run(WorldGenLevel level, ChunkAccess chunk, ChunkGenerator owner, NativeFeatureTable nativeTable) {
        FeatureTable table = (FeatureTable) nativeTable;
        ChunkPos centerPos = chunk.getPos();
        SectionPos sectionPos = SectionPos.of(centerPos, level.getMinSectionY());
        BlockPos origin = sectionPos.origin();
        Registry<PlacedFeature> featureRegistry = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
        List<FeatureSorter.StepFeatureData> steps = table.steps();
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        long decorationSeed = random.setDecorationSeed(level.getSeed(), origin.getX(), origin.getZ());
        NativeImportedFeaturePolicy.Placement placement = table.policy().placement();
        Set<Holder<Biome>> chunkBiomes = chunkBiomes(level, sectionPos, table, placement);
        IntBinaryOperator surfaceFirstFreeY = placement.stacked()
                ? placement.surfaceFirstFreeY()
                : (x, z) -> level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        IntBinaryOperator floorFirstFreeY = placement.stacked()
                ? placement.floorFirstFreeY()
                : (x, z) -> level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
        WorldGenLevel placementLevel = !placement.guarded() ? level : ModdedNativeStructureWorldgenAccess.create(
                level, centerPos, surfaceFirstFreeY, floorFirstFreeY, placement.stacked(),
                position -> placement.protectedPosition().test(position.getX(), position.getY(), position.getZ()));

        try {
            for (int stepIndex = 0; stepIndex < steps.size(); stepIndex++) {
                if (!table.control().shouldGenerateStepOrdinal(stepIndex)) {
                    continue;
                }
                placeStep(placementLevel, table, steps.get(stepIndex), featureRegistry, chunkBiomes, owner,
                        random, decorationSeed, origin, stepIndex);
            }
        } catch (Throwable error) {
            observer.failed(centerPos.x(), centerPos.z(), error);
            throw new IllegalStateException("Imported feature placement failed for chunk "
                    + centerPos.x() + "," + centerPos.z(), error);
        } finally {
            level.setCurrentlyGenerating(null);
        }
        observer.completed();
    }

    private static void addKey(Set<String> keys, String key) {
        String normalized = normalizeKey(key);
        if (normalized != null) {
            keys.add(normalized);
        }
    }

    private void placeStep(WorldGenLevel level, FeatureTable table, FeatureSorter.StepFeatureData stepData,
                           Registry<PlacedFeature> featureRegistry, Set<Holder<Biome>> chunkBiomes,
                           ChunkGenerator owner, WorldgenRandom random, long decorationSeed,
                           BlockPos origin, int stepIndex) {
        IntSet stepFeatures = new IntArraySet();
        for (Holder<Biome> biome : chunkBiomes) {
            List<HolderSet<PlacedFeature>> biomeFeatures = settingsFor(biome, table.derivatives()).features();
            if (stepIndex >= biomeFeatures.size()) {
                continue;
            }
            for (Holder<PlacedFeature> feature : biomeFeatures.get(stepIndex)) {
                stepFeatures.add(stepData.indexMapping().applyAsInt(feature.value()));
            }
        }
        if (stepFeatures.isEmpty()) {
            return;
        }
        // Sorted global indices: identical ordering to vanilla, and each feature's seed comes from its own
        // global index, so denying one feature never shifts another.
        int[] featureIndices = stepFeatures.toIntArray();
        Arrays.sort(featureIndices);
        for (int globalIndex : featureIndices) {
            PlacedFeature feature = stepData.features().get(globalIndex);
            if (table.filtered()) {
                Identifier featureId = featureRegistry.getKey(feature);
                if (featureId != null && !table.control().shouldGenerate(featureId.toString())) {
                    continue;
                }
            }
            random.setFeatureSeed(decorationSeed, globalIndex, stepIndex);
            level.setCurrentlyGenerating(() -> describeFeature(featureRegistry, feature));
            feature.placeWithBiomeCheck(level, owner, random, origin);
        }
    }

    private static String describeFeature(Registry<PlacedFeature> registry, PlacedFeature feature) {
        Identifier id = registry.getKey(feature);
        return id == null ? feature.toString() : id.toString();
    }

    /**
     * Biomes actually present in the 3x3 chunk neighbourhood, intersected with the dimension's biome set. Same
     * shape as vanilla, which drops any section biome its biome source does not claim. Holders are canonicalised
     * back onto the table's own holders so the index mapping cannot be handed a holder it never saw.
     */
    private Set<Holder<Biome>> chunkBiomes(
            WorldGenLevel level,
            SectionPos sectionPos,
            FeatureTable table,
            NativeImportedFeaturePolicy.Placement placement
    ) {
        List<Holder<Biome>> collected = new ArrayList<>();
        ChunkPos.rangeClosed(sectionPos.chunk(), 1).forEach((ChunkPos chunkPos) -> {
            ChunkAccess neighbour = level.getChunk(chunkPos.x(), chunkPos.z());
            LevelChunkSection[] sections = neighbour.getSections();
            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                LevelChunkSection section = sections[sectionIndex];
                if (!placement.stacked()) {
                    section.getBiomes().getAll(collected::add);
                    continue;
                }
                int sectionMinimumY = neighbour.getSectionYFromSectionIndex(sectionIndex) << 4;
                for (int quartY = 0; quartY < 4; quartY++) {
                    int internalY = sectionMinimumY + (quartY << 2) - level.getMinY();
                    for (int quartX = 0; quartX < 4; quartX++) {
                        int blockX = chunkPos.getMinBlockX() + (quartX << 2);
                        for (int quartZ = 0; quartZ < 4; quartZ++) {
                            int blockZ = chunkPos.getMinBlockZ() + (quartZ << 2);
                            if (placement.acceptedBiomePosition().test(blockX, internalY, blockZ)) {
                                collected.add(section.getBiomes().get(quartX, quartY, quartZ));
                            }
                        }
                    }
                }
            }
        });
        Set<Holder<Biome>> present = new LinkedHashSet<>();
        for (Holder<Biome> biome : collected) {
            if (table.biomeSet().contains(biome)) {
                present.add(biome);
                continue;
            }
            String key = holderKey(biome);
            Holder<Biome> canonical = key == null ? null : table.byKey().get(key);
            if (canonical != null) {
                present.add(canonical);
            }
        }
        return present;
    }

    private static String holderKey(Holder<Biome> holder) {
        return holder.unwrapKey()
                .map(key -> key.identifier().toString().toLowerCase(Locale.ROOT))
                .orElse(null);
    }

    private static String normalizeKey(String key) {
        Identifier identifier = key == null ? null : Identifier.tryParse(key);
        return identifier == null ? null : identifier.toString().toLowerCase(Locale.ROOT);
    }

    private record FeatureTable(long generation, NativeImportedFeaturePolicy<?> policy, NativeImportedFeatureControl control,
                                List<Holder<Biome>> biomes, Set<Holder<Biome>> biomeSet,
                                Map<String, Holder<Biome>> byKey,
                                List<FeatureSorter.StepFeatureData> steps,
                                Map<String, Holder<Biome>> derivatives, boolean filtered) implements NativeFeatureTable {
        @Override
        public int biomeCount() {
            return biomes.size();
        }

        @Override
        public int stepCount() {
            return steps.size();
        }

        @Override
        public int derivativeCount() {
            return derivatives.size();
        }
    }

    public interface PlacementObserver {
        void completed();
        void failed(int chunkX, int chunkZ, Throwable error);
    }
}

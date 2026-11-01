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



import art.arcane.volmlib.nativelib.terrain.structure.StructureStagePolicy;
import art.arcane.volmlib.nativelib.terrain.structure.StructureStagePolicy.Footprint;
import art.arcane.volmlib.nativelib.terrain.structure.StructureStagePolicy.LocateRequest;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedNativeStructureWorldgenAccess;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeGenerationWriteGuard;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureGenerationException;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureLocatePersistence;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureLocateResults;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureOwnershipRecovery;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructurePostProcessor;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureReferenceEnvelope;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureSurfaceFitter;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureTerrainIntegrator;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureVanillaLocator;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureVegetationClearer;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureVerticalPlacer;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureVolumeSource;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.WorldgenTerrainHeightmaps;
import art.arcane.volmlib.nativelib.terrain.NativeBlockChangeSet;
import art.arcane.volmlib.nativelib.terrain.NativeBlockPositionPredicate;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.StructureLocateProbe;
import art.arcane.volmlib.nativelib.terrain.structure.StructureLocateCandidate;
import art.arcane.volmlib.nativelib.terrain.structure.StructureLocateSearchAccess;
import art.arcane.volmlib.nativelib.terrain.structure.StructureOwnershipRecordView;
import art.arcane.volmlib.nativelib.terrain.structure.StructurePalette;
import art.arcane.volmlib.nativelib.terrain.structure.StructurePlacementDecision;
import art.arcane.volmlib.nativelib.terrain.structure.StructureReferencePolicy;
import art.arcane.volmlib.nativelib.terrain.structure.StructureSeaSurface;
import art.arcane.volmlib.nativelib.terrain.structure.StructureStartPlan;
import art.arcane.volmlib.nativelib.terrain.structure.StructureVolumePolicy;
import art.arcane.volmlib.util.math.RNG;
import com.mojang.datafixers.util.Pair;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class NativeModdedStructureStage<C, P extends StructureStartPlan, O extends StructureOwnershipRecordView<O>> {
    private static final Comparator<StructureStart> START_ORDER = Comparator
            .comparingInt((StructureStart start) -> start.getChunkPos().x)
            .thenComparingInt(start -> start.getChunkPos().z);
    private final ChunkGenerator generator;
    private final Supplier<? extends NativeModdedBiomeSource<?>> biomeSource;
    private final Policy<C, P, O> policy;
    private volatile StructureStepCache structureStepCache;

    public NativeModdedStructureStage(Options<C, P, O> options) {
        generator = options.generator();
        biomeSource = options.biomeSource();
        policy = options.policy();
    }

    public void installVolumeIndex(ServerLevel level, C engine) {
        WeakReference<ServerLevel> levelReference = new WeakReference<>(level);
        WeakReference<ChunkGenerator> generatorReference = new WeakReference<>(generator);
        WeakReference<BiomeSource> biomeSourceReference = new WeakReference<>(biomeSource.get());
        policy.installVolumeSource(engine, new NativeStructureVolumeSource<>(
                new NativeStructureVolumeSource.Context(
                level.registryAccess(),
                level.getServer().getStructureManager(),
                level.dimension(),
                LevelHeightAccessor.create(level.getMinBuildHeight(), level.getHeight()),
                generatorReference::get,
                biomeSourceReference::get,
                () -> {
                    ServerLevel active = levelReference.get();
                    return active == null || !active.structureManager().shouldGenerateStructures()
                            ? null : active.getChunkSource().getGeneratorState();
                }),
                policy.volumePolicy()));
    }

    public Pair<BlockPos, Holder<Structure>> findNearestStructure(ServerLevel level,
                                                              HolderSet<Structure> holders,
                                                              BlockPos pos, int radius, boolean findUnexplored,
                                                              C current,
                                                              NativeStructureVanillaLocator.Candidate nativeCandidate) {
        Pair<BlockPos, Holder<Structure>> nativeLocated =
                nativeCandidate == null ? null : nativeCandidate.result();
        Runnable nativeReference = () -> {
            if (nativeCandidate != null) {
                nativeCandidate.reference(level.structureManager());
            }
        };
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<NativeLocateSearch<O>> searches = new ArrayList<>(holders.size());
        NativeStructureLocatePersistence.ProbeBudget budget = NativeStructureLocatePersistence.probeBudget();
        for (Holder<Structure> holder : holders) {
            ResourceLocation id = registry.getKey(holder.value());
            if (id == null) {
                throw new IllegalStateException("Native structure locate received an unregistered structure holder");
            }
            String structureId = id.toString();
            if (!policy.hasPlacement(current, structureId)) {
                continue;
            }
            NativeStructureLocatePersistence.Probe probe = NativeStructureLocatePersistence.probe(
                    level, holder.value(), findUnexplored, budget);
            searches.add(new NativeLocateSearch<>(
                    holder, structureId, policy.locateSearch(current, new LocateRequest<>(
                    structureId, pos.getX(), pos.getZ(), radius, probe,
                    start -> NativeStructureOwnershipRecovery.resolve(
                            policy.structurePolicy(current), level, structureId, holder.value(), start)))));
        }
        searches.sort(Comparator.comparing(NativeLocateSearch::structureId));
        List<NativeLocateSearch<O>> retries = new ArrayList<>();
        Pair<BlockPos, Holder<Structure>> irisLocated = null;
        NativeLocateSearch<O> selectedSearch = null;
        StructureLocateSearchAccess.Verified<StructureStart, O> selectedStart = null;
        long selectedDistance = Long.MAX_VALUE;
        int attempts = 0;
        while (attempts < StructureLocateSearchAccess.MAX_SELECTED_CANDIDATE_RETRIES) {
            if (searches.isEmpty()) {
                searches.addAll(retries);
                retries.clear();
            }
            NativeLocateSearch<O> bestSearch = null;
            StructureLocateCandidate bestResult = null;
            long bestDistance = Long.MAX_VALUE;
            Iterator<NativeLocateSearch<O>> iterator = searches.iterator();
            while (iterator.hasNext()) {
                NativeLocateSearch<O> search = iterator.next();
                StructureLocateCandidate result = search.search().predict();
                if (!result.found()) {
                    iterator.remove();
                    continue;
                }
                long dx = (long) result.originX() - pos.getX();
                long dz = (long) result.originZ() - pos.getZ();
                long distance = dx * dx + dz * dz;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestSearch = search;
                    bestResult = result;
                }
            }
            if (bestSearch == null) {
                if (retries.isEmpty()) {
                    break;
                }
                continue;
            }
            searches.remove(bestSearch);
            attempts++;
            StructureLocateSearchAccess.Verified<StructureStart, O> verified =
                    bestSearch.search().verify(bestResult);
            if (verified == null) {
                bestSearch.search().reject(bestResult);
                retries.add(bestSearch);
                continue;
            }
            BlockPos pieceLocator = verified.start().getPieces().getFirst().getLocatorPosition();
            BlockPos located = new BlockPos(
                    pieceLocator.getX(), policy.locatorY(verified.ownership()), pieceLocator.getZ());
            long dx = (long) located.getX() - pos.getX();
            long dz = (long) located.getZ() - pos.getZ();
            long distance = dx * dx + dz * dz;
            if (distance < selectedDistance) {
                irisLocated = Pair.of(located, bestSearch.holder());
                selectedSearch = bestSearch;
                selectedStart = verified;
                selectedDistance = distance;
            }
        }
        NativeLocateSearch<O> referenceSearch = selectedSearch;
        StructureLocateSearchAccess.Verified<StructureStart, O> referenceStart = selectedStart;
        return NativeStructureLocateResults.selectAndReference(
                pos, irisLocated, () -> referenceSearch.search().reference(referenceStart),
                nativeLocated, nativeReference);
    }

    public HolderSet<Structure> filterReachableNativeStructures(ServerLevel level, HolderSet<Structure> holders,
                                                        C current) {
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<Holder<Structure>> kept = new ArrayList<>(holders.size());
        for (Holder<Structure> holder : holders) {
            ResourceLocation id = registry.getKey(holder.value());
            if (id == null) {
                throw new IllegalStateException("Native structure filtering received an unregistered structure holder");
            }
            String key = id.toString();
            StructurePlacementDecision decision = policy.structurePolicy(current).resolve(
                    key, NativeStructureVegetationClearer.isUndergroundStep(holder.value().step()));
            if (!decision.generate() || !biomeSource.get().isStructureReachable(holder)) {
                continue;
            }
            kept.add(holder);
        }
        return kept.size() == holders.size() ? holders : HolderSet.direct(kept);
    }

    public void adjustGeneratedStructures(RegistryAccess registryAccess, ChunkAccess chunk,
                                   Map<Structure, StructureStart> previousStarts,
                                   Map<Structure, P> configuredStarts,
                                   C current,
                                   StructureTemplateManager templateManager) {
        Registry<Structure> registry = registryAccess.registryOrThrow(Registries.STRUCTURE);
        ChunkPos chunkPos = chunk.getPos();
        for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
            Structure structure = entry.getKey();
            StructureStart start = entry.getValue();
            if (!start.isValid() || previousStarts.get(structure) == start) {
                continue;
            }
            ResourceLocation id = registry.getKey(structure);
            String structureId = id == null ? null : id.toString();
            if (structureId == null) {
                throw NativeStructureGenerationException.failure(
                        "resolution", null, chunkPos.x, chunkPos.z);
            }
            BoundingBox footprint = start.getBoundingBox();
            if (!policy.allowsFootprint(current, new Footprint(footprint.minX(), footprint.minZ(), footprint.maxX(), footprint.maxZ()))) {
                chunk.setStartForStructure(structure, StructureStart.INVALID_START);
                if (configuredStarts.containsKey(structure)) {
                    policy.structurePolicy(current).discard(structureId, chunkPos.x, chunkPos.z);
                }
                continue;
            }
            if (configuredStarts.containsKey(structure)) {
                recordWorldCheckStructureShift(
                        configuredStarts.get(structure).structureKey(), start.getChunkPos(), 0);
                continue;
            }
            boolean undergroundStep = NativeStructureVegetationClearer.isUndergroundStep(structure.step());
            StructurePlacementDecision decision;
            try {
                decision = policy.structurePolicy(current).resolve(
                        structureId, undergroundStep);
            } catch (Throwable error) {
                throw NativeStructureGenerationException.failure(
                        "policy resolution", structureId, chunkPos.x, chunkPos.z, error);
            }
            if (!decision.generate()) {
                chunk.setStartForStructure(structure, StructureStart.INVALID_START);
                continue;
            }
            int offsetY;
            try {
                offsetY = NativeStructureVerticalPlacer.applyVerticalPlacement(
                        start,
                        structureId,
                        decision.yShift(),
                        generator.getSeaLevel(),
                        chunk.getMinBuildHeight(),
                        chunk.getMinBuildHeight() + chunk.getHeight(),
                        undergroundStep,
                        decision.preserveSourceY(),
                        decision.yBand(),
                        policy.surfaceHeight(current));
                StructureStart wrapped = NativeStructureReferenceEnvelope.wrapForPublication(
                        start, structure, start.getReferences(),
                        NativeStructureTerrainIntegrator.resolveNativeTerrain(start, decision.terrain()),
                        structureId);
                chunk.setStartForStructure(structure, wrapped);
                if (!wrapped.isValid()) {
                    continue;
                }
            } catch (Throwable error) {
                throw NativeStructureGenerationException.failure(
                        "vertical adjustment", structureId, chunkPos.x, chunkPos.z, error);
            }
            recordWorldCheckStructureShift(structureId, start.getChunkPos(), offsetY);
        }
    }

    public void placeVanillaStructures(WorldGenLevel world, ChunkAccess chunk, StructureManager structureManager) {
        if (!structureManager.shouldGenerateStructures()) {
            return;
        }
        ChunkPos chunkPos = chunk.getPos();
        SectionPos sectionPos = SectionPos.of(chunkPos, world.getMinSection());
        BlockPos origin = sectionPos.origin();
        Registry<Structure> registry = world.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<List<Structure>> byStep = structuresByStep(registry);
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        long decorationSeed = random.setDecorationSeed(world.getSeed(), origin.getX(), origin.getZ());
        BoundingBox area = writableArea(chunk);
        int steps = GenerationStep.Decoration.values().length;
        C current = policy.current();
        List<NativePlacementGroup> placementGroups = new ArrayList<>();
        List<StructureStart> heightmapStarts = new ArrayList<>();
        List<StructureStart> vegetationTargets = new ArrayList<>();
        List<NativeStructureTerrainIntegrator.TerrainTarget> terrainTargets = new ArrayList<>();
        for (int step = 0; step < steps; step++) {
            int index = 0;
            for (Structure structure : byStep.get(step)) {
                ResourceLocation id = registry.getKey(structure);
                String structureId = id == null ? null : id.toString();
                if (structureId == null) {
                    throw NativeStructureGenerationException.failure(
                            "resolution", null, chunkPos.x, chunkPos.z);
                }
                try {
                    StructurePlacementDecision sourceDecision = policy.structurePolicy(current).resolve(
                            structureId, NativeStructureVegetationClearer.isUndergroundStep(structure.step()));
                    List<StructureStart> starts = orderedStarts(structureManager.startsForStructure(sectionPos, structure));
                    List<NativePlacement> resolvedPlacements = new ArrayList<>(starts.size());
                    for (StructureStart start : starts) {
                        BoundingBox footprint = start.getBoundingBox();
                        if (!isHistoricalStructureStart(current, world, start)
                                && !policy.allowsFootprint(current, new Footprint(footprint.minX(), footprint.minZ(), footprint.maxX(), footprint.maxZ()))) {
                            continue;
                        }
                        O ownership =
                                NativeStructureOwnershipRecovery.resolve(
                    policy.structurePolicy(current), world.getLevel(), structureId, structure, start);
                        StructurePlacementDecision decision =
                                ownership == null ? sourceDecision : policy.restoredDecision(ownership);
                        if (!decision.generate()) {
                            continue;
                        }
                        resolvedPlacements.add(new NativePlacement(start, decision));
                        heightmapStarts.add(start);
                        terrainTargets.add(new NativeStructureTerrainIntegrator.TerrainTarget(
                                structureId, start,
                                NativeStructureTerrainIntegrator.resolveNativeTerrain(
                                        start, decision.terrain())));
                        vegetationTargets.add(start);
                    }
                    if (!resolvedPlacements.isEmpty()) {
                        placementGroups.add(new NativePlacementGroup(
                                structureId, index, step, List.copyOf(resolvedPlacements)));
                    }
                } catch (Throwable error) {
                    throw NativeStructureGenerationException.failure(
                            "resolution", structureId, chunkPos.x, chunkPos.z, error);
                }
                index++;
            }
        }
        if (!placementGroups.isEmpty()) {
            ServerLevel level = world.getLevel();
            visitExistingPois(chunk, (position, state) -> level.onBlockStateChange(
                    position, Blocks.AIR.defaultBlockState(), state));
        }
        try {
            int runtimeMinY = world.getMinBuildHeight();
            WorldgenTerrainHeightmaps.primeStructurePlacement(
                    world, chunkPos, heightmapStarts,
                    worldgenSurfaceHeight(current, runtimeMinY),
                    worldgenFloorHeight(current, runtimeMinY));
        } catch (Throwable error) {
            throw NativeStructureGenerationException.failure(
                    "heightmap priming", nativeStructureBatchContext(placementGroups),
                    chunkPos.x, chunkPos.z, error);
        }
        Predicate<BlockPos> protectedPosition = NativeGenerationWriteGuard.protectedPositions(policy.protectedPositions(current));
        NativeBlockChangeSet blockChanges;
        try {
            blockChanges = trackBlockChanges(current, world, chunkPos, placementGroups);
        } catch (Throwable error) {
            throw NativeStructureGenerationException.failure(
                    "block change tracking", nativeStructureBatchContext(placementGroups),
                    chunkPos.x, chunkPos.z, error);
        }
        ModdedNativeStructureWorldgenAccess boundedWorld = ModdedNativeStructureWorldgenAccess.create(
                world, chunkPos, worldgenSurfaceHeight(current, world.getMinBuildHeight()), worldgenFloorHeight(current, world.getMinBuildHeight()),
                policy.stacked(current), true,
                protectedPosition, blockChanges);
        Throwable placementFailure = null;
        try {
            try {
                NativeStructureVegetationClearer.clearIntersectingVegetation(
                        boundedWorld, chunk, area, vegetationTargets);
            } catch (Throwable error) {
                throw NativeStructureGenerationException.failure(
                        "vegetation cleanup", nativeStructureBatchContext(placementGroups),
                        chunkPos.x, chunkPos.z, error);
            }
            NativeStructureSurfaceFitter.SurfaceTerrainPlan surfaceTerrainPlan;
            try {
                surfaceTerrainPlan = NativeStructureSurfaceFitter.prepareSurfaceStructures(
                        boundedWorld, area, terrainTargets,
                        policy.surfaceHeight(current), resolveSeaSurface(current));
                surfaceTerrainPlan.primeHeightmaps(chunk);
            } catch (Throwable error) {
                throw NativeStructureGenerationException.failure(
                        "terrain integration", nativeStructureBatchContext(placementGroups),
                        chunkPos.x, chunkPos.z, error);
            }
            try {
                NativeStructurePostProcessor.prepareTerrain(
                        boundedWorld, area, terrainTargets, this::resolvePaletteBlock);
            } catch (Throwable error) {
                throw NativeStructureGenerationException.failure(
                        "terrain preparation", nativeStructureBatchContext(placementGroups),
                        chunkPos.x, chunkPos.z, error);
            }
            for (NativePlacementGroup group : placementGroups) {
                random.setFeatureSeed(decorationSeed, group.featureIndex(), group.step());
                try {
                    for (NativePlacement placement : group.placements()) {
                        placeVanillaStructure(boundedWorld, structureManager, random, area, chunkPos,
                                group.structureId(), placement.start(), placement.decision());
                    }
                } catch (Throwable error) {
                    throw NativeStructureGenerationException.failure(
                            "placement", group.structureId(), chunkPos.x, chunkPos.z, error);
                }
            }
            try {
                NativeStructureSurfaceFitter.repairVacuumFoundations(
                        boundedWorld, area, surfaceTerrainPlan);
            } catch (Throwable error) {
                throw NativeStructureGenerationException.failure(
                        "foundation repair", nativeStructureBatchContext(placementGroups),
                        chunkPos.x, chunkPos.z, error);
            }
        } catch (Throwable error) {
            placementFailure = error;
            throw error;
        } finally {
            try {
                policy.invalidateBlockChanges(current, blockChanges);
            } catch (Throwable error) {
                NativeStructureGenerationException invalidationFailure = NativeStructureGenerationException.failure(
                        "deferred record invalidation", nativeStructureBatchContext(placementGroups),
                        chunkPos.x, chunkPos.z, error);
                if (placementFailure == null) {
                    throw invalidationFailure;
                }
                placementFailure.addSuppressed(invalidationFailure);
            }
        }
    }

    private NativeBlockChangeSet trackBlockChanges(C current, WorldGenLevel world, ChunkPos chunkPos,
                                                   List<NativePlacementGroup> placementGroups) {
        if (placementGroups.isEmpty()) {
            return NativeBlockChangeSet.disabled();
        }
        return policy.trackBlockChanges(current, ModdedNativeStructureWorldgenAccess.changeBounds(world, chunkPos));
    }

    public static void visitExistingPois(ChunkAccess chunk, BiConsumer<BlockPos, BlockState> visitor) {
        chunk.findBlocks(PoiTypes::hasPoi, visitor);
    }

    private static String nativeStructureBatchContext(List<NativePlacementGroup> placementGroups) {
        if (placementGroups.isEmpty()) {
            return "<no resolved native structures>";
        }
        StringBuilder context = new StringBuilder("[");
        for (int i = 0; i < placementGroups.size(); i++) {
            if (i > 0) {
                context.append(", ");
            }
            context.append(placementGroups.get(i).structureId());
        }
        return context.append(']').toString();
    }

    static List<StructureStart> orderedStarts(List<StructureStart> starts) {
        if (starts.size() < 2) {
            return starts;
        }
        List<StructureStart> ordered = new ArrayList<>(starts);
        ordered.sort(START_ORDER);
        return ordered;
    }

    private void placeVanillaStructure(ModdedNativeStructureWorldgenAccess world, StructureManager structureManager,
                                       WorldgenRandom random, BoundingBox area, ChunkPos chunkPos,
                                       String structureId, StructureStart start,
                                       StructurePlacementDecision decision) {
        C current = policy.current();
        world.setCurrentlyGenerating(() -> policy.generationLabel(structureId));
        try {
            NativeStructurePostProcessor.place(
                    world, structureManager, generator, random, area, chunkPos,
                    structureId, start, decision, this::resolvePaletteBlock,
                    policy.surfaceHeight(current));
        } finally {
            world.setCurrentlyGenerating(null);
        }
    }

    private List<List<Structure>> structuresByStep(Registry<Structure> registry) {
        StructureStepCache cached = structureStepCache;
        if (cached != null && cached.registry() == registry) {
            return cached.structures();
        }
        synchronized (generator) {
            cached = structureStepCache;
            if (cached != null && cached.registry() == registry) {
                return cached.structures();
            }
            int steps = GenerationStep.Decoration.values().length;
            List<List<Structure>> grouped = new ArrayList<>(steps);
            for (int step = 0; step < steps; step++) {
                grouped.add(new ArrayList<>());
            }
            for (Structure structure : registry) {
                grouped.get(structure.step().ordinal()).add(structure);
            }
            for (int step = 0; step < steps; step++) {
                grouped.set(step, List.copyOf(grouped.get(step)));
            }
            List<List<Structure>> resolved = List.copyOf(grouped);
            structureStepCache = new StructureStepCache(registry, resolved);
            return resolved;
        }
    }

    private void recordWorldCheckStructureShift(String structureId, ChunkPos startChunk, int offsetY) {
        policy.recordShift(structureId, startChunk.toLong(), offsetY);
    }

    public Integer worldCheckStructureShift(String structureId, ChunkPos startChunk) {
        return structureId == null || startChunk == null ? null : policy.recordedShift(structureId, startChunk.toLong());
    }

    public void clearWorldCheckStructureShifts() {
        policy.clearShifts();
    }

    private BlockState resolvePaletteBlock(StructurePalette source, RNG rng,
                                          int x, int y, int z) {
        NativeBlockState platformState = policy.paletteBlock(source, rng, x, y, z);
        if (platformState == null || !(platformState.nativeHandle() instanceof BlockState blockState)) {
            throw new IllegalStateException("Configured native structure palette did not resolve a Minecraft block at "
                    + x + "," + y + "," + z);
        }
        return blockState;
    }

    private NativeStructureSurfaceFitter.SeaSurface resolveSeaSurface(C current) {
        StructureSeaSurface sea = Objects.requireNonNull(policy.seaSurface(current),
                "Native structure stage policy did not supply a sea surface");
        if (sea.fluid().nativeHandle() instanceof BlockState fluid) {
            return new NativeStructureSurfaceFitter.SeaSurface(sea.topY(), fluid);
        }
        throw new IllegalStateException("Native structure sea surface did not resolve a Minecraft block for "
                + sea.fluid().key());
    }

    private boolean isHistoricalStructureStart(C current, WorldGenLevel world, StructureStart start) {
        ChunkPos origin = start.getChunkPos();
        if (!policy.allowsChunkWrite(current, origin.x, origin.z)) {
            return true;
        }
        ChunkAccess source = world.getChunk(origin.x, origin.z, ChunkStatus.EMPTY, false);
        return source != null && policy.historicalStructure(current, NativeTerrainReceipts.structureActivation(source));
    }

    private BoundingBox writableArea(ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int minY = chunk.getMinBuildHeight() + 1;
        int maxY = chunk.getMinBuildHeight() + chunk.getHeight() - 1;
        return new BoundingBox(minX, minY, minZ, minX + 15, maxY, minZ + 15);
    }

    private IntBinaryOperator worldgenSurfaceHeight(C generationEngine, int runtimeMinY) {
        return policy.worldgenHeight(generationEngine, runtimeMinY, false);
    }

    private IntBinaryOperator worldgenFloorHeight(C generationEngine, int runtimeMinY) {
        return policy.worldgenHeight(generationEngine, runtimeMinY, true);
    }

    private record NativePlacement(StructureStart start, StructurePlacementDecision decision) {
    }

    private record NativePlacementGroup(String structureId, int featureIndex, int step,
                                        List<NativePlacement> placements) {
    }

    private record NativeLocateSearch<O>(Holder<Structure> holder, String structureId,
                                          StructureLocateSearchAccess<StructureStart, O> search) {
    }

    private record StructureStepCache(Registry<Structure> registry, List<List<Structure>> structures) {
    }

    public record Options<C, P extends StructureStartPlan, O extends StructureOwnershipRecordView<O>>(
            ChunkGenerator generator, Supplier<? extends NativeModdedBiomeSource<?>> biomeSource,
            Policy<C, P, O> policy) {
    }

    public interface Policy<C, P extends StructureStartPlan, O extends StructureOwnershipRecordView<O>>
            extends StructureStagePolicy<C, P, O> {
        StructureVolumePolicy<C, P> volumePolicy();
        void installVolumeSource(C context, NativeStructureVolumeSource<C, P> source);
        void recordShift(String key, long chunk, int offsetY);
        Integer recordedShift(String key, long chunk);
        void clearShifts();
    }
}

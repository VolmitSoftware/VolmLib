package art.arcane.volmlib.nativelib.v26_3_R1.terrain;

import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureStateLifecycle;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeTerrainPipeline;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureVolumeSource;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeImportedFeatureStage;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeBiomeSourceImpl;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeBiomeRegistryImpl;
import art.arcane.volmlib.nativelib.terrain.structure.StructurePalette;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.InitialMobSpawner;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureWorldgenAccess;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.VanillaStructureBiomes;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeTerrainColumns;
import java.util.Optional;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeGenerationWriteGuard;
import java.util.function.LongPredicate;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureGenerationException;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureStartInjector;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureReferenceEnvelope;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureLocateResults;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureLocatePersistence;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureOwnershipRecovery;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructurePostProcessor;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureReferenceRepair;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureSurfaceFitter;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureTerrainIntegrator;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureVegetationClearer;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureVerticalPlacer;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureVanillaLocator;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.WorldgenTerrainHeightmaps;
import art.arcane.volmlib.nativelib.terrain.NativePlacementFailures;
import art.arcane.volmlib.nativelib.terrain.NativeBlockChangeSet;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.math.RNG;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.generator.CustomChunkGenerator;
import org.bukkit.block.data.BlockData;
import org.spigotmc.SpigotWorldConfig;
import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;
import art.arcane.volmlib.nativelib.terrain.structure.StructureSeaSurface;
import art.arcane.volmlib.nativelib.terrain.structure.StructureWaterRules;
import art.arcane.volmlib.nativelib.terrain.structure.StructureStagePolicy;
import art.arcane.volmlib.nativelib.terrain.structure.StructureCachePolicy;
import art.arcane.volmlib.nativelib.terrain.structure.StructurePlacementDecision;
import art.arcane.volmlib.nativelib.terrain.structure.StructureStartPlan;
import art.arcane.volmlib.nativelib.terrain.structure.StructureOwnershipRecordView;
import art.arcane.volmlib.nativelib.terrain.structure.StructureLocateSearchAccess;
import art.arcane.volmlib.nativelib.terrain.structure.StructureLocateCandidate;
import art.arcane.volmlib.nativelib.terrain.structure.StructureStagePolicy.Footprint;
import art.arcane.volmlib.nativelib.terrain.structure.StructureStagePolicy.LocateRequest;

public final class NativeBukkitStructureStage<C, D, P extends StructureStartPlan, O extends StructureOwnershipRecordView<O>> {
    private static final Comparator<StructureStart> START_ORDER = Comparator
            .comparingInt((StructureStart start) -> start.getChunkPos().x())
            .thenComparingInt(start -> start.getChunkPos().z());
    private final ChunkGenerator generator;
    private final NativeBiomeSourceImpl customBiomeSource;
    private final int runtimeMinY;
    private final StructureStagePolicy<C, P, O> policy;
    private final StructureCachePolicy<D> cache;
    private final NamespacedKey activationKey;
    private volatile ReachableStructureCache<D> reachableStructureCache;
    private volatile StructureStepCache structureStepCache;

    public NativeBukkitStructureStage(Configuration<C, D, P, O> configuration) {
        generator = configuration.generator();
        customBiomeSource = configuration.biomes();
        runtimeMinY = configuration.minimumY();
        policy = configuration.policy();
        cache = configuration.cache();
        activationKey = configuration.activationKey();
    }

    public void evictRuntime(int runtimeId) {
        ReachableStructureCache<D> reachable = reachableStructureCache;
        if (reachable != null && reachable.runtimeId() == runtimeId) {
            reachableStructureCache = null;
        }
        StructureStepCache steps = structureStepCache;
        if (steps != null && steps.runtimeId() == runtimeId) {
            structureStepCache = null;
        }
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
        Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        List<NativeLocateSearch<O>> searches = new ArrayList<>(holders.size());
        NativeStructureLocatePersistence.ProbeBudget budget = NativeStructureLocatePersistence.probeBudget();
        for (Holder<Structure> holder : holders) {
            Identifier id = registry.getKey(holder.value());
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

    public HolderSet<Structure> filterReachableStructures(ServerLevel level, HolderSet<Structure> holders) {
        Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        List<NativeLocateCandidate> candidates = new ArrayList<>(holders.size());
        for (Holder<Structure> holder : holders) {
            Object id = registry.getKey(holder.value());
            if (id == null) {
                throw new IllegalStateException("Native structure filtering received an unregistered structure holder");
            }
            String key = id.toString();
            StructurePlacementDecision decision = policy.structurePolicy(policy.current()).resolve(
                    key, NativeStructureVegetationClearer.isUndergroundStep(holder.value().step()));
            if (!decision.generate()) {
                continue;
            }
            candidates.add(new NativeLocateCandidate(holder, key));
        }
        if (candidates.isEmpty()) {
            return HolderSet.direct(List.of());
        }
        Set<String> reachable = reachableStructureKeys(level);
        List<Holder<Structure>> kept = new ArrayList<>(candidates.size());
        for (NativeLocateCandidate candidate : candidates) {
            if (reachable.contains(candidate.key())) {
                kept.add(candidate.holder());
            }
        }
        if (kept.size() == holders.size()) {
            return holders;
        }
        return HolderSet.direct(kept);
    }

    private Set<String> reachableStructureKeys(ServerLevel level) {
        D dimension = cache.dimension();
        int runtimeId = cache.runtimeId();
        ReachableStructureCache<D> cached = reachableStructureCache;
        if (cached != null && cached.dimension() == dimension && cached.runtimeId() == runtimeId) {
            return cached.keys();
        }
        synchronized (this) {
            cached = reachableStructureCache;
            if (cached != null && cached.dimension() == dimension && cached.runtimeId() == runtimeId) {
                return cached.keys();
            }
            Set<String> reachable = Set.copyOf(
                    VanillaStructureBiomes.reachableStructureKeys(level, customBiomeSource));
            reachableStructureCache = new ReachableStructureCache<>(dimension, runtimeId, reachable);
            return reachable;
        }
    }

    public void adjustGeneratedStructures(RegistryAccess registryAccess, ChunkAccess access,
                                           Map<Structure, StructureStart> previousStarts,
                                           Map<Structure, P> configuredStarts,
                                           StructureTemplateManager templateManager) {
        Registry<Structure> registry = registryAccess.lookupOrThrow(Registries.STRUCTURE);
        ChunkPos chunkPos = access.getPos();
        for (Map.Entry<Structure, StructureStart> entry : access.getAllStarts().entrySet()) {
            Structure structure = entry.getKey();
            StructureStart start = entry.getValue();
            if (!start.isValid() || previousStarts.get(structure) == start) {
                continue;
            }
            Identifier id = registry.getKey(structure);
            String structureId = id == null ? null : id.toString();
            if (structureId == null) {
                throw NativeStructureGenerationException.failure(
                        "resolution", null, chunkPos.x(), chunkPos.z());
            }
            BoundingBox footprint = start.getBoundingBox();
            if (!policy.allowsFootprint(policy.current(), new Footprint(footprint.minX(), footprint.minZ(), footprint.maxX(), footprint.maxZ()))
                    || !StructureWaterRules.admitsStart(configuredStarts.get(structure), policy.water(policy.current()),
                            NativeStructureTerrainIntegrator.pieceFootprints(start))) {
                access.setStartForStructure(structure, StructureStart.INVALID_START);
                if (configuredStarts.containsKey(structure)) {
                    policy.structurePolicy(policy.current()).discard(structureId, chunkPos.x(), chunkPos.z());
                }
                continue;
            }
            if (configuredStarts.containsKey(structure)) {
                continue;
            }
            boolean undergroundStep = NativeStructureVegetationClearer.isUndergroundStep(structure.step());
            StructurePlacementDecision decision;
            try {
                decision = policy.structurePolicy(policy.current()).resolve(
                        structureId, undergroundStep);
            } catch (Throwable error) {
                throw NativeStructureGenerationException.failure(
                        "policy resolution", structureId, chunkPos.x(), chunkPos.z(), error);
            }
            if (!decision.generate()) {
                access.setStartForStructure(structure, StructureStart.INVALID_START);
                continue;
            }
            try {
                NativeStructureVerticalPlacer.applyVerticalPlacement(
                        start,
                        structureId,
                        decision.yShift(),
                        generator.getSeaLevel(),
                        access.getMinY(),
                        access.getMinY() + access.getHeight(),
                        undergroundStep,
                        decision.preserveSourceY(),
                        decision.yBand(),
                        policy.surfaceHeight(policy.current()));
                StructureStart wrapped = NativeStructureReferenceEnvelope.wrapForPublication(
                        start, structure, start.getReferences(),
                        NativeStructureTerrainIntegrator.resolveNativeTerrain(start, decision.terrain()),
                        structureId);
                access.setStartForStructure(structure, wrapped);
            } catch (Throwable error) {
                throw NativeStructureGenerationException.failure(
                        "vertical adjustment", structureId, chunkPos.x(), chunkPos.z(), error);
            }
        }
    }

    public void placeVanillaStructures(WorldGenLevel world, ChunkAccess chunk, StructureManager structureManager) {
        if (!structureManager.shouldGenerateStructures()) {
            return;
        }
        ChunkPos chunkPos = chunk.getPos();
        SectionPos sectionPos = SectionPos.of(chunkPos, world.getMinSectionY());
        BlockPos origin = sectionPos.origin();
        Registry<Structure> registry = world.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        List<List<Structure>> byStep = structuresByStep(registry);
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        long decoSeed = random.setDecorationSeed(world.getSeed(), origin.getX(), origin.getZ());
        BoundingBox area = writableArea(chunk);
        int steps = GenerationStep.Decoration.values().length;
        List<NativePlacementGroup> placementGroups = new ArrayList<>();
        List<StructureStart> heightmapStarts = new ArrayList<>();
        List<StructureStart> vegetationTargets = new ArrayList<>();
        List<NativeStructureTerrainIntegrator.TerrainTarget> terrainTargets = new ArrayList<>();
        for (int step = 0; step < steps; step++) {
            int index = 0;
            for (Structure structure : byStep.get(step)) {
                Object id = registry.getKey(structure);
                String structureId = id == null ? null : id.toString();
                if (structureId == null) {
                    throw NativeStructureGenerationException.failure(
                            "resolution", null, chunkPos.x(), chunkPos.z());
                }
                try {
                    StructurePlacementDecision sourceDecision = policy.structurePolicy(policy.current()).resolve(
                            structureId, NativeStructureVegetationClearer.isUndergroundStep(structure.step()));
                    List<StructureStart> starts = orderedStarts(structureManager.startsForStructure(sectionPos.x(), sectionPos.z(), structure));
                    List<NativePlacement> resolvedPlacements = new ArrayList<>(starts.size());
                    for (StructureStart start : starts) {
                        BoundingBox footprint = start.getBoundingBox();
                        if (!isHistoricalStructureStart(world, start)
                                && !policy.allowsFootprint(policy.current(), new Footprint(footprint.minX(), footprint.minZ(), footprint.maxX(), footprint.maxZ()))) {
                            continue;
                        }
                        O ownership =
                                NativeStructureOwnershipRecovery.resolve(
                    policy.structurePolicy(policy.current()), world.getLevel(), structureId, structure, start);
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
                            "resolution", structureId, chunkPos.x(), chunkPos.z(), error);
                }
                index++;
            }
        }
        try {
            WorldgenTerrainHeightmaps.primeStructurePlacement(
                    world, chunkPos, heightmapStarts,
                    hostWorldgenSurfaceHeight(), hostWorldgenFloorHeight());
        } catch (Throwable error) {
            throw NativeStructureGenerationException.failure(
                    "heightmap priming", nativeStructureBatchContext(placementGroups),
                    chunkPos.x(), chunkPos.z(), error);
        }
        Predicate<BlockPos> protectedPosition = NativeGenerationWriteGuard.protectedPositions(
                policy.protectedPositions(policy.current()));
        NativeBlockChangeSet blockChanges;
        try {
            blockChanges = trackBlockChanges(world, chunkPos, placementGroups);
        } catch (Throwable error) {
            throw NativeStructureGenerationException.failure(
                    "block change tracking", nativeStructureBatchContext(placementGroups),
                    chunkPos.x(), chunkPos.z(), error);
        }
        NativeStructureWorldgenAccess boundedWorld = NativeStructureWorldgenAccess.create(
                world, chunkPos, hostWorldgenSurfaceHeight(), hostWorldgenFloorHeight(),
                policy.stacked(policy.current()), true,
                protectedPosition, blockChanges);
        Throwable placementFailure = null;
        try {
            try {
                NativeStructureVegetationClearer.clearIntersectingVegetation(
                        boundedWorld, chunk, area, vegetationTargets);
            } catch (Throwable error) {
                containStructure(error, "structure vegetation", nativeStructureBatchContext(placementGroups), chunkPos);
            }
            NativeStructureSurfaceFitter.SurfaceStrata surfaceStrata = NativeStructureSurfaceFitter.SurfaceStrata.of(
                    Objects.requireNonNull(policy.strata(policy.current()),
                            "Native structure stage policy did not supply structure strata"),
                    this::strataBlock);
            NativeStructureSurfaceFitter.SurfaceTerrainPlan surfaceTerrainPlan = null;
            try {
                surfaceTerrainPlan = NativeStructureSurfaceFitter.prepareSurfaceStructures(
                        boundedWorld, area, terrainTargets,
                        policy.surfaceHeight(policy.current()), resolveSeaSurface(),
                        Objects.requireNonNull(policy.water(policy.current()),
                                "Native structure stage policy did not supply structure water"),
                        surfaceStrata);
                surfaceTerrainPlan.primeHeightmaps(chunk);
            } catch (Throwable error) {
                containStructure(error, "structure surface", nativeStructureBatchContext(placementGroups), chunkPos);
            }
            try {
                NativeStructurePostProcessor.prepareTerrain(
                        boundedWorld, area, terrainTargets, this::resolvePaletteBlock,
                        Objects.requireNonNull(policy.water(policy.current()),
                                "Native structure stage policy did not supply structure water"));
            } catch (Throwable error) {
                containStructure(error, "structure terrain", nativeStructureBatchContext(placementGroups), chunkPos);
            }
            for (NativePlacementGroup group : placementGroups) {
                random.setFeatureSeed(decoSeed, group.featureIndex(), group.step());
                for (NativePlacement placement : group.placements()) {
                    try {
                        placeVanillaStructure(boundedWorld, structureManager, random, area, chunkPos,
                                group.structureId(), placement.start(), placement.decision());
                    } catch (Throwable error) {
                        containStructure(error, "vanilla structure", group.structureId(), chunkPos);
                    }
                }
            }
            try {
                NativeStructureSurfaceFitter.repairVacuumFoundations(
                        boundedWorld, area, surfaceTerrainPlan, surfaceStrata);
            } catch (Throwable error) {
                containStructure(error, "structure foundation", nativeStructureBatchContext(placementGroups), chunkPos);
            }
        } catch (Throwable error) {
            placementFailure = error;
            throw error;
        } finally {
            try {
                policy.invalidateBlockChanges(policy.current(), blockChanges);
            } catch (Throwable error) {
                NativeStructureGenerationException invalidationFailure = NativeStructureGenerationException.failure(
                        "deferred record invalidation", nativeStructureBatchContext(placementGroups),
                        chunkPos.x(), chunkPos.z(), error);
                if (placementFailure == null) {
                    throw invalidationFailure;
                }
                placementFailure.addSuppressed(invalidationFailure);
            }
        }
    }

    private static void containStructure(Throwable failure, String kind, String structures, ChunkPos chunkPos) {
        try {
            NativePlacementFailures.skip(failure, kind, structures, chunkPos.getMinBlockX(), chunkPos.getMinBlockZ());
        } catch (Throwable stopped) {
            throw NativeStructureGenerationException.failure(kind, structures, chunkPos.x(), chunkPos.z(), stopped);
        }
    }

    private NativeBlockChangeSet trackBlockChanges(WorldGenLevel world, ChunkPos chunkPos,
                                                   List<NativePlacementGroup> placementGroups) {
        if (placementGroups.isEmpty()) {
            return NativeBlockChangeSet.disabled();
        }
        return policy.trackBlockChanges(policy.current(),
                NativeStructureWorldgenAccess.changeBounds(world, chunkPos));
    }

    static List<StructureStart> orderedStarts(List<StructureStart> starts) {
        if (starts.size() < 2) {
            return starts;
        }
        List<StructureStart> ordered = new ArrayList<>(starts);
        ordered.sort(START_ORDER);
        return ordered;
    }

    private boolean isHistoricalStructureStart(WorldGenLevel world, StructureStart start) {
        ChunkPos origin = start.getChunkPos();
        if (!policy.allowsChunkWrite(policy.current(), origin.x(), origin.z())) {
            return true;
        }
        ChunkAccess source = world.getChunk(origin.x(), origin.z(), ChunkStatus.EMPTY, false);
        if (source == null) {
            return false;
        }
        long activation = source.persistentDataContainer.getOrDefault(
                activationKey, PersistentDataType.LONG, 0L);
        return policy.historicalStructure(policy.current(), activation);
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

    private void placeVanillaStructure(NativeStructureWorldgenAccess world, StructureManager structureManager,
                                       WorldgenRandom random, BoundingBox area, ChunkPos chunkPos,
                                       String structureId, StructureStart start,
                                       StructurePlacementDecision decision) {
        world.setCurrentlyGenerating(() -> policy.generationLabel(structureId));
        try {
            NativeStructurePostProcessor.place(world, structureManager, generator, random, area, chunkPos,
                    structureId, start, decision, this::resolvePaletteBlock,
                    policy.surfaceHeight(policy.current()));
        } finally {
            world.setCurrentlyGenerating(null);
        }
    }

    private List<List<Structure>> structuresByStep(Registry<Structure> registry) {
        int runtimeId = cache.runtimeId();
        StructureStepCache cached = structureStepCache;
        if (cached != null && cached.runtimeId() == runtimeId && cached.registry() == registry) {
            return cached.structures();
        }
        synchronized (this) {
            cached = structureStepCache;
            if (cached != null && cached.runtimeId() == runtimeId && cached.registry() == registry) {
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
            structureStepCache = new StructureStepCache(runtimeId, registry, resolved);
            return resolved;
        }
    }

    private BlockState strataBlock(NativeBlockState platformState) {
        if (platformState != null && platformState.placementHandle() instanceof CraftBlockData craftBlockData) {
            return craftBlockData.getState();
        }
        throw new IllegalStateException("Structure strata resolved no Bukkit block: " + platformState);
    }

    private BlockState resolvePaletteBlock(StructurePalette source, RNG rng, int x, int y, int z) {
        NativeBlockState platformState = policy.paletteBlock(source, rng, x, y, z);
        if (platformState == null || !(platformState.placementHandle() instanceof BlockData blockData)) {
            throw new IllegalStateException("Configured native structure palette did not resolve a Bukkit block at "
                    + x + "," + y + "," + z);
        }
        if (blockData instanceof CraftBlockData craftBlockData) {
            return craftBlockData.getState();
        }
        throw new IllegalStateException("Configured native structure palette resolved unsupported Bukkit block data "
                + blockData.getClass().getName() + " at " + x + "," + y + "," + z);
    }

    private NativeStructureSurfaceFitter.SeaSurface resolveSeaSurface() {
        StructureSeaSurface sea = Objects.requireNonNull(policy.seaSurface(policy.current()),
                "Native structure stage policy did not supply a sea surface");
        if (sea.fluid() == null) {
            return NativeStructureSurfaceFitter.SeaSurface.NONE;
        }
        if (sea.fluid().placementHandle() instanceof CraftBlockData craftBlockData) {
            return new NativeStructureSurfaceFitter.SeaSurface(sea.topY(), craftBlockData.getState());
        }
        throw new IllegalStateException("Native structure sea surface resolved unsupported Bukkit block data "
                + sea.fluid().placementHandle().getClass().getName() + " for " + sea.fluid().key());
    }

    private BoundingBox writableArea(ChunkAccess chunk) {
        ChunkPos cp = chunk.getPos();
        int i = cp.getMinBlockX();
        int j = cp.getMinBlockZ();
        int minY = chunk.getMinY() + 1;
        int maxY = chunk.getMinY() + chunk.getHeight() - 1;
        return new BoundingBox(i, minY, j, i + 15, maxY, j + 15);
    }

    private IntBinaryOperator hostWorldgenSurfaceHeight() {
        return policy.worldgenHeight(policy.current(), runtimeMinY, false);
    }

    private IntBinaryOperator hostWorldgenFloorHeight() {
        return policy.worldgenHeight(policy.current(), runtimeMinY, true);
    }

    public record Configuration<C, D, P extends StructureStartPlan, O extends StructureOwnershipRecordView<O>>(
            ChunkGenerator generator, NativeBiomeSourceImpl biomes, int minimumY,
            StructureStagePolicy<C, P, O> policy, StructureCachePolicy<D> cache, NamespacedKey activationKey) {
    }

    private record ReachableStructureCache<D>(D dimension, int runtimeId, Set<String> keys) {}
    private record StructureStepCache(int runtimeId, Registry<Structure> registry, List<List<Structure>> structures) {}
    private record NativePlacement(StructureStart start, StructurePlacementDecision decision) {}
    private record NativePlacementGroup(String structureId, int featureIndex, int step, List<NativePlacement> placements) {}
    private record NativeLocateCandidate(Holder<Structure> holder, String key) {}
    private record NativeLocateSearch<O>(Holder<Structure> holder, String structureId, StructureLocateSearchAccess<StructureStart, O> search) {}
}

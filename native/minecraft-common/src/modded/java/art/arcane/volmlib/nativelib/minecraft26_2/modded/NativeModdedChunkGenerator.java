package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeStructureReachability;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedPlatformWorld;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeGeneratorContext;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedStructureStage;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedBiomeSource;
import art.arcane.volmlib.nativelib.terrain.feature.NativeFeatureTable;
import art.arcane.volmlib.nativelib.terrain.NativeChunkWritePolicy;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedHeightmaps;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeTransitionColumn;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeTerrainHeightCache;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeGenerationWriteGuard;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureStartInjector;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureReferenceRepair;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureVanillaLocator;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.BiomeManager;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeBiomeResolver;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.IntBinaryOperator;
import art.arcane.volmlib.nativelib.terrain.NativeModdedBiomePolicy;
import art.arcane.volmlib.nativelib.terrain.NativeGenerationScope;
import art.arcane.volmlib.nativelib.terrain.NativeGenerationRoute;
import art.arcane.volmlib.nativelib.terrain.NativeGenerationLease;
import art.arcane.volmlib.nativelib.terrain.NativeSpawnSelection;
import art.arcane.volmlib.nativelib.terrain.NativeBlockColumn;
import art.arcane.volmlib.nativelib.terrain.structure.StructureStartPlan;
import art.arcane.volmlib.nativelib.terrain.structure.StructureOwnershipRecordView;

public final class NativeModdedChunkGenerator<C, P extends StructureStartPlan, O extends StructureOwnershipRecordView<O>>
        extends ChunkGenerator implements NativeChunkWritePolicy, NativeStructureReachability, NativeGeneratorHandle {
    private final NativeModdedGeneratorPolicy<C, P, O> policy;
    private final NativeModdedBiomeSource<NativeModdedBiomePolicy<Holder<Biome>, Climate.Sampler>> structureBiomeSource;
    private final NativeModdedGeneratorPolicy.FeatureStage<C> importedFeatures;
    private final NativeModdedStructureStage<C, P, O> nativeStructures;
    private final NativeSpawnBiomeTables<C> spawnTables;
    private final NativeTerrainHeightCache terrainHeights = new NativeTerrainHeightCache();
    private final NativeGeneratorContext context;
    private final NativeGeneratorOwner owner;
    private volatile NativeWorld generationWorld;
    private final MapCodec<? extends ChunkGenerator> codec;

    public NativeModdedChunkGenerator(Options<C, P, O> options) {
        this(options, components(options));
    }

    private NativeModdedChunkGenerator(Options<C, P, O> options, Components<C> components) {
        super(components.biomes(), biome -> components.features().nativeFeatures().generationSettings(biome, components.features().currentTable()));
        this.context = options.context();
        this.codec = options.definition().codec();
        this.owner = options.owner();
        this.policy = options.policy();
        this.structureBiomeSource = components.biomes();
        this.importedFeatures = components.features();
        this.nativeStructures = new NativeModdedStructureStage<>(new NativeModdedStructureStage.Options<>(
                this, () -> structureBiomeSource, policy.structures()));
        this.spawnTables = new NativeSpawnBiomeTables<>(policy.spawns(), owner);
    }

    private static <C, P extends StructureStartPlan, O extends StructureOwnershipRecordView<O>> Components<C> components(Options<C, P, O> options) {
        NativeModdedGeneratorPolicy<C, P, O> policy = options.policy();
        NativeModdedBiomeSource<NativeModdedBiomePolicy<Holder<Biome>, Climate.Sampler>> biomes =
                new NativeModdedBiomeSource<>(new NativeModdedBiomeSource.Options<>(
                        options.context().biomeSource(), () -> nativeServer(policy), policy::biomePolicy));
        return new Components<>(biomes, policy.featureStage(biomes));
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSets, RandomState randomState, long seed) {
        ChunkGeneratorStructureState state = ChunkGeneratorStructureState.createForNormal(
                randomState, seed, structureBiomeSource.forStructureState(structureSets), structureSets);
        return NativeStructureSetFrequencyOverrides.apply(state, policy.structureFrequencies());
    }

    private static MinecraftServer nativeServer(NativeModdedGeneratorPolicy<?, ?, ?> policy) {
        NativeModdedServer server = policy.server();
        return server == null ? null : server.server();
    }
    public NativeGeneratorOwner owner() {
        return owner;
    }
    public NativeGeneratorContext context() {
        return context;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return codec;
    }
    public boolean represents(NativeWorld world) {
        return world != null && ((ServerLevel) world.nativeHandle()).getChunkSource().getGenerator() == this;
    }
    public void installVolumeIndex(NativeWorld world, C context) {
        nativeStructures.installVolumeIndex((ServerLevel) world.nativeHandle(), context);
    }
    public void clearCaches() {
        generationWorld = null;
        structureBiomeSource.clearCaches();
        nativeStructures.clearWorldCheckStructureShifts();
        spawnTables.resetVanillaSpawnBiomes();
    }
    public void evictRuntime(int runtimeId) {
        terrainHeights.evictRuntime(runtimeId);
        structureBiomeSource.evictRuntime(runtimeId);
        spawnTables.evictRuntime(runtimeId);
    }
    public NativeBiomeResolver biomeResolver() {
        return new NativeBiomeResolver(structureBiomeSource::getVisibleNoiseBiome);
    }
    private C engine() {
        return policy.current();
    }
    private C engine(ServerLevel level) {
        NativeWorld world = generationWorld;
        if (world == null || world.nativeHandle() != level) {
            world = new ModdedPlatformWorld(level);
            generationWorld = world;
        }
        return policy.current(world);
    }
    private C engine(ResourceKey<Level> key) {
        return policy.current(key.identifier().toString());
    }
    private NativeGenerationRoute openHistoryRoute(C current, int x, int z, String operation) {
        return policy.route(current, new NativeModdedGeneratorPolicy.Position(x, z), operation);
    }
    private NativeGenerationScope openHistoryCoordinateScope(C current, int x, int z, String operation) {
        return policy.coordinateScope(current, new NativeModdedGeneratorPolicy.Position(x, z), operation);
    }
    private static NativeGenerationScope openHistoryRuntimeScope(NativeGenerationRoute route) {
        return route == null ? null : route.openRuntimeScope();
    }

    @Override
    public int getGenDepth() {
        return policy.depth();
    }

    @Override
    public int getMinY() {
        return policy.minimumY();
    }

    @Override
    public int getSeaLevel() {
        return policy.seaLevel();
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor height) {
        return policy.spawnHeight(height.getMinY(), height.getHeight());
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState state, BlockPos pos) {
        policy.addDebugInformation(info);
    }

    @Override
    public boolean allowsNativeChunkWrite(int x, int z) {
        return policy.allowsNativeChunkWrite(x, z);
    }

    private ChunkAccess generateTerrain(ChunkAccess chunk, C current, ChunkPos pos, NativeGenerationRoute route) {
        try (NativeGenerationScope scope = openHistoryRuntimeScope(route);
             NativeGenerationLease lease = policy.terrainLease(current);
             NativeGenerationScope context = policy.context(current, lease.sessionId())) {
            NativeModdedGeneratorPolicy.Terrain terrain = policy.generate(current, pos.x(), pos.z());
            writeBlocks(chunk, terrain.blocks(), terrain.minimumY(), terrain.height());
            if (route != null) {
                BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
                route.claimGeneratedSemantics((x, y, z) -> {
                    BlockState state = chunk.getBlockState(position.set(x, terrain.minimumY() + y, z));
                    return state.isAir() || state.liquid();
                });
                byte[] receipt = policy.terrainReceipt(route);
                if (receipt != null) {
                    NativeTerrainReceipts.persist(chunk, receipt);
                }
            }
            writeTerrainHeightmaps(chunk, current, pos, terrain.height());
            Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES));
            policy.generated(current, pos.x(), pos.z());
            return chunk;
        } catch (Throwable failure) {
            throw policy.generationFailure(current, new NativeModdedGeneratorPolicy.Position(pos.x(), pos.z()), failure);
        }
    }

    @Override
    public Pair<BlockPos, Holder<Structure>> findNearestMapStructure(ServerLevel level, HolderSet<Structure> holders,
                                                                     BlockPos pos, int radius,
                                                                     boolean findUnexplored) {
        C current = engine();
        int chunkX = Math.floorDiv(pos.getX(), 16);
        int chunkZ = Math.floorDiv(pos.getZ(), 16);
        try (NativeGenerationRoute route = openHistoryRoute(
                     current, chunkX, chunkZ, "modded_structure_locate");
             NativeGenerationScope runtimeScope = openHistoryRuntimeScope(route);
             NativeGenerationLease lease = policy.lease(current, "modded_structure_locate");
             NativeGenerationScope ignored = policy.context(current, lease.sessionId())) {
            HolderSet<Structure> reachable = nativeStructures.filterReachableNativeStructures(
                    level, holders, current);
            NativeStructureVanillaLocator.Candidate nativeCandidate =
                    reachable.size() == 0 ? null
                            : NativeStructureVanillaLocator.predict(
                                    level, reachable, pos, radius, findUnexplored);
            return nativeStructures.findNearestStructure(
                    level, holders, pos, Math.max(0, radius), findUnexplored, current, nativeCandidate);
        }
    }

    @Override
    public boolean isNativeStructureReachable(Holder<Structure> structure) {
        return structure != null && structureBiomeSource.isStructureReachable(structure);
    }

    @Override
    public WeightedList<MobSpawnSettings.SpawnerData> getMobsAt(
            Holder<Biome> biome, StructureManager structureManager, MobCategory category, BlockPos pos) {
        C current = engine();
        NativeSpawnSelection selection = policy.spawnSelection(current, new NativeModdedGeneratorPolicy.SpawnQuery(pos.getX(), pos.getY(), pos.getZ(),
                biome.unwrapKey().map(key -> key.identifier().toString()).orElse("")));
        if (selection.mode() == NativeSpawnSelection.Mode.LOADING) {
            return WeightedList.of(List.of());
        }
        try (NativeGenerationScope historyScope = openHistoryCoordinateScope(
                     current, pos.getX(), pos.getZ(), "modded_mob_spawn_table");
             NativeGenerationLease lease = policy.lease(current, "modded_mob_spawn_table");
             NativeGenerationScope ignored = policy.context(current, lease.sessionId())) {
            WeightedList<MobSpawnSettings.SpawnerData> explicitSpawns =
                    biome.value().getMobSettings().getMobs(category);
            WeightedList<MobSpawnSettings.SpawnerData> resolvedSpawns = super.getMobsAt(
                    biome, structureManager, category, pos);
            if (resolvedSpawns != explicitSpawns) {
                return resolvedSpawns;
            }

            Registry<Biome> registry = structureManager.registryAccess().lookupOrThrow(Registries.BIOME);
            Holder<Biome> vanillaSpawnBiome;
            if (selection.mode() == NativeSpawnSelection.Mode.RETAINED) {
                vanillaSpawnBiome = spawnTables.resolveBiomeHolder(registry, selection.derivativeKey());
            } else if (selection.mode() == NativeSpawnSelection.Mode.CURRENT) {
                spawnTables.initializeVanillaSpawnBiomes(registry);
                vanillaSpawnBiome = spawnTables.vanillaSpawnBiome(biome.value());
            } else {
                vanillaSpawnBiome = null;
            }
            if (vanillaSpawnBiome == null) {
                return explicitSpawns;
            }

            WeightedList<MobSpawnSettings.SpawnerData> vanillaSpawns =
                    vanillaSpawnBiome.value().getMobSettings().getMobs(category);
            if (explicitSpawns.isEmpty()) {
                return vanillaSpawns;
            }
            if (vanillaSpawns.isEmpty()) {
                return explicitSpawns;
            }

            return spawnTables.mergedSpawnTable(
                    policy.runtimeId(current),
                    biome.value(),
                    vanillaSpawnBiome.value(),
                    category,
                    vanillaSpawns,
                    explicitSpawns
            );
        }
    }

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(RandomState randomState, Blender blender,
                                                       StructureManager structureManager, ChunkAccess chunk) {
        C current = engine();
        ChunkPos chunkPos = chunk.getPos();
        try (NativeGenerationRoute route = openHistoryRoute(
                     current, chunkPos.x(), chunkPos.z(), "modded_create_biomes");
             NativeGenerationScope runtimeScope = openHistoryRuntimeScope(route);
             NativeGenerationLease lease = policy.lease(current, "modded_create_biomes");
             NativeGenerationScope ignored = policy.context(current, lease.sessionId())) {
            chunk.fillBiomesFromNoise(structureBiomeSource::getVisibleNoiseBiome, randomState.sampler());
            return CompletableFuture.completedFuture(chunk);
        }
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        C generationEngine = engine();
        ChunkPos pos = chunk.getPos();
        policy.generating(pos.x(), pos.z());
        NativeGenerationRoute route = openHistoryRoute(
                generationEngine, pos.x(), pos.z(), "modded_chunk_pipeline");

        try {
            if (policy.parallelChunkSystem()) {
                try {
                    return CompletableFuture.completedFuture(
                            generateTerrain(chunk, generationEngine, pos, route));
                } finally {
                    if (route != null) {
                        route.close();
                    }
                }
            }
            CompletableFuture<ChunkAccess> pipeline = CompletableFuture.supplyAsync(
                    () -> generateTerrain(chunk, generationEngine, pos, route),
                    policy.executor());
            return closeRouteOnCompletion(pipeline, route);
        } catch (RuntimeException | Error failure) {
            closeHistoryRoute(route, failure);
            throw failure;
        }
    }

    private static CompletableFuture<ChunkAccess> closeRouteOnCompletion(
            CompletableFuture<ChunkAccess> pipeline,
            NativeGenerationRoute route
    ) {
        if (route == null) {
            return pipeline;
        }
        CompletableFuture<ChunkAccess> completion = new CompletableFuture<>();
        pipeline.whenComplete((ChunkAccess chunk, Throwable failure) -> {
            boolean cancelled = isCancellationFailure(failure);
            Throwable completionFailure = failure;
            try {
                route.close();
            } catch (Throwable closeFailure) {
                completionFailure = appendFailure(completionFailure, closeFailure);
            }
            if (completionFailure == null) {
                completion.complete(chunk);
            } else if (cancelled) {
                completion.cancel(false);
            } else {
                completion.completeExceptionally(completionFailure);
            }
        });
        route.detachThread();
        return completion;
    }

    private static void closeHistoryRoute(
            NativeGenerationRoute route,
            Throwable failure
    ) {
        if (route == null) {
            return;
        }
        try {
            route.close();
        } catch (Throwable closeFailure) {
            if (failure != closeFailure) {
                failure.addSuppressed(closeFailure);
            }
        }
    }

    private static Throwable appendFailure(Throwable failure, Throwable closeFailure) {
        if (failure == null) {
            return closeFailure;
        }
        if (failure != closeFailure) {
            failure.addSuppressed(closeFailure);
        }
        return failure;
    }

    private static boolean isCancellationFailure(Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current instanceof CancellationException;
    }

    private void writeTerrainHeightmaps(ChunkAccess chunk, C generationEngine, ChunkPos pos, int height) {
        int baseX = pos.getMinBlockX();
        int baseZ = pos.getMinBlockZ();
        writeTerrainHeightmap(chunk, Heightmap.Types.WORLD_SURFACE_WG, height,
                (x, z) -> policy.terrainHeight(generationEngine, baseX + x, baseZ + z, false, false) + 1);
        writeTerrainHeightmap(chunk, Heightmap.Types.OCEAN_FLOOR_WG, height,
                (x, z) -> policy.terrainHeight(generationEngine, baseX + x, baseZ + z, true, false) + 1);
    }

    private void writeTerrainHeightmap(ChunkAccess chunk, Heightmap.Types type, int height,
                                       IntBinaryOperator heightResolver) {
        Heightmap heightmap = chunk.getOrCreateHeightmapUnprimed(type);
        heightmap.setRawData(chunk, type, ModdedHeightmaps.terrainRawData(height, heightResolver));
    }

    private void writeBlocks(ChunkAccess chunk, NativeModdedGeneratorPolicy.BlockBuffer blocks, int dimMinY, int height) {
        int chunkMinY = chunk.getMinY();
        int chunkMaxY = chunkMinY + chunk.getHeight();
        int from = Math.max(dimMinY, chunkMinY);
        int to = Math.min(dimMinY + height, chunkMaxY);
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();

        for (int y = from; y < to; ) {
            int sectionIndex = chunk.getSectionIndex(y);
            LevelChunkSection section = chunk.getSection(sectionIndex);
            int sectionMinY = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
            int sectionEnd = Math.min(sectionMinY + 16, to);
            section.acquire();
            try {
                for (int blockY = y; blockY < sectionEnd; blockY++) {
                    int bufferY = blockY - dimMinY;
                    int localY = blockY & 15;
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            NativeBlockState state = blocks.rawOrNull(x, bufferY, z);
                            if (state == null) {
                                continue;
                            }
                            BlockState blockState = (BlockState) state.nativeHandle();
                            section.setBlockState(x, localY, z, blockState, false);
                            if (blockState.hasBlockEntity()) {
                                createDefaultBlockEntity(chunk, new BlockPos(baseX + x, blockY, baseZ + z), blockState);
                            }
                        }
                    }
                }
            } finally {
                section.release();
            }
            y = sectionEnd;
        }
    }

    public static void createDefaultBlockEntity(ChunkAccess chunk, BlockPos position, BlockState state) {
        if (!(state.getBlock() instanceof EntityBlock entityBlock)) {
            return;
        }
        BlockEntity blockEntity = entityBlock.newBlockEntity(position, state);
        if (blockEntity != null) {
            chunk.setBlockEntity(blockEntity);
        }
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk) {
        C current = engine(region.getLevel());
        ChunkPos chunkPos = chunk.getPos();
        try (NativeGenerationRoute route = openHistoryRoute(
                     current, chunkPos.x(), chunkPos.z(), "modded_apply_carvers");
             NativeGenerationScope runtimeScope = openHistoryRuntimeScope(route);
             NativeGenerationLease lease = policy.lease(current, "modded_apply_carvers");
             NativeGenerationScope ignored = policy.context(current, lease.sessionId())) {
        }
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
        C current = engine(region.getLevel());
        ChunkPos chunkPos = chunk.getPos();
        try (NativeGenerationRoute route = openHistoryRoute(
                     current, chunkPos.x(), chunkPos.z(), "modded_build_surface");
             NativeGenerationScope runtimeScope = openHistoryRuntimeScope(route);
             NativeGenerationLease lease = policy.lease(current, "modded_build_surface");
             NativeGenerationScope ignored = policy.context(current, lease.sessionId())) {
        }
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        C current = engine(level.getLevel());
        ChunkPos chunkPos = chunk.getPos();
        try (NativeGenerationRoute route = openHistoryRoute(
                     current, chunkPos.x(), chunkPos.z(), "modded_biome_decoration");
             NativeGenerationScope runtimeScope = openHistoryRuntimeScope(route);
             NativeGenerationLease lease = policy.lease(current, "modded_biome_decoration");
             NativeGenerationScope ignored = policy.context(current, lease.sessionId())) {
            if (chunk.getPersistedStatus().isOrAfter(ChunkStatus.FEATURES)) {
                return;
            }
            nativeStructures.placeVanillaStructures(level, chunk, structureManager);
            if (!allowsRoutedDiscreteGeneration(
                    current,
                    route,
                    chunkPos,
                    chunk,
                    ChunkStatus.FEATURES
            )) {
                return;
            }
            if (!NativeGenerationWriteGuard.allowsDecoration(policy.chunkWrites(current), level, chunkPos)) {
                return;
            }
            importedFeatures.prepare(current);
            // Vanilla's placed-feature pass, on THIS thread and never on ModdedGenPool: the FEATURES chunk
            // step writes into the eight neighbouring chunks and is not parallel-safe. Inert unless the
            // dimension set importedFeatures.enabled.
            NativeFeatureTable featureTable = importedFeatures.placementTable(current);
            if (featureTable != null) {
                importedFeatures.nativeFeatures().run(level, chunk, this, featureTable);
            }
        }
    }

    @Override
    public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState structureState, StructureManager structureManager, ChunkAccess chunk, StructureTemplateManager templateManager, ResourceKey<Level> levelKey) {
        C current = engine(levelKey);
        ChunkPos chunkPos = chunk.getPos();
        try (NativeGenerationRoute route = openHistoryRoute(
                     current, chunkPos.x(), chunkPos.z(), "modded_create_structures");
             NativeGenerationScope runtimeScope = openHistoryRuntimeScope(route);
             NativeGenerationLease lease = policy.lease(current, "modded_create_structures");
             NativeGenerationScope ignored = policy.context(current, lease.sessionId())) {
            if (!allowsRoutedDiscreteGeneration(
                    current,
                    route,
                    chunkPos,
                    chunk,
                    ChunkStatus.STRUCTURE_STARTS
            )) {
                return;
            }
            Map<Structure, StructureStart> previousStarts = new HashMap<>(chunk.getAllStarts());
            super.createStructures(registryAccess, structureState, structureManager, chunk, templateManager, levelKey);
            Map<Structure, P> configuredStarts = NativeStructureStartInjector.inject(
                    new NativeStructureStartInjector.InjectionContext<>(
                            policy.injectionPolicy(current),
                            registryAccess,
                            structureState,
                            structureManager,
                            chunk,
                            templateManager,
                            levelKey,
                            this,
                            structureBiomeSource
                    ));
            nativeStructures.adjustGeneratedStructures(
                    registryAccess, chunk, previousStarts, configuredStarts, current, templateManager);
            if (route != null) {
                NativeTerrainReceipts.persistStructureActivation(chunk, policy.structureActivation(route));
            }
        }
    }

    @Override
    public void createReferences(WorldGenLevel level, StructureManager structureManager, ChunkAccess chunk) {
        C current = engine(level.getLevel());
        ChunkPos chunkPos = chunk.getPos();
        try (NativeGenerationRoute route = openHistoryRoute(
                     current, chunkPos.x(), chunkPos.z(), "modded_create_references");
             NativeGenerationScope runtimeScope = openHistoryRuntimeScope(route);
             NativeGenerationLease lease = policy.lease(current, "modded_create_references");
             NativeGenerationScope ignored = policy.context(current, lease.sessionId())) {
            NativeStructureReferenceRepair.createReferences(
                    policy.structurePolicy(current), level, structureManager, chunk);
        }
    }

    public Integer worldCheckStructureShift(String structureId, int chunkX, int chunkZ) {
        return nativeStructures.worldCheckStructureShift(structureId, new ChunkPos(chunkX, chunkZ));
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        ChunkPos center = region.getCenter();
        C current = engine(region.getLevel());
        try (NativeGenerationRoute route = openHistoryRoute(
                     current, center.x(), center.z(), "modded_spawn_original_mobs");
             NativeGenerationScope runtimeScope = openHistoryRuntimeScope(route);
             NativeGenerationLease lease = policy.lease(current, "modded_spawn_original_mobs");
             NativeGenerationScope ignored = policy.context(current, lease.sessionId())) {
            ChunkAccess centerChunk = region.getChunk(center.x(), center.z());
            if (!allowsRoutedDiscreteGeneration(
                    current,
                    route,
                    center,
                    centerChunk,
                    ChunkStatus.SPAWN
            )) {
                return;
            }
            Registry<Biome> registry = region.registryAccess().lookupOrThrow(Registries.BIOME);
            spawnTables.initializeVanillaSpawnBiomes(registry);
            Holder<Biome> visibleBiome;
            if (!policy.stacked(current)) {
                visibleBiome = region.getBiome(center.getWorldPosition().atY(region.getMaxY()));
            } else {
                visibleBiome = structureBiomeSource.getVisibleSurfaceBiome(
                        center.getMinBlockX() + 8,
                        center.getMinBlockZ() + 8);
                if (visibleBiome == null) {
                    visibleBiome = region.getBiome(center.getWorldPosition().atY(region.getMaxY()));
                }
            }
            Holder<Biome> vanillaBiome = spawnTables.vanillaSpawnBiome(visibleBiome.value());
            WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
            random.setDecorationSeed(region.getSeed(), center.getMinBlockX(), center.getMinBlockZ());
            NaturalSpawner.spawnMobsForChunkGeneration(
                    region, vanillaBiome == null ? visibleBiome : vanillaBiome, center, random);
        }
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState) {
        C current = policy.queryCurrent("base height");
        try (NativeGenerationScope historyScope = openHistoryCoordinateScope(
                     current, x, z, "modded_base_height");
             NativeGenerationLease lease = policy.queryLease(current, "base height");
             NativeGenerationScope ignored = policy.context(current, lease.sessionId())) {
            NativeTerrainHeightCache.Query query = new NativeTerrainHeightCache.Query(
                    policy.runtimeId(current), x, z, type, heightAccessor.getMinY(), heightAccessor.getHeight());
            OptionalInt resolved = terrainHeights.resolvedHeight(query,
                    () -> resolvedBaseHeight(current, x, z, type, heightAccessor));
            if (resolved.isPresent()) {
                return resolved.getAsInt();
            }
            boolean ignoreFluid = !type.isOpaque().test(Blocks.WATER.defaultBlockState());
            int height = policy.terrainHeight(current, x, z, ignoreFluid, true);
            return heightAccessor.getMinY() + height + 1;
        } catch (Throwable failure) {
            throw policy.queryFailure("base height", failure);
        }
    }

    private OptionalInt resolvedBaseHeight(C current, int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor) {
        NativeBlockColumn resolved = policy.resolvedColumn(current, x, z);
        return resolved != null
                ? OptionalInt.of(NativeTransitionColumn.height(resolved, type, heightAccessor, policy::placementKey))
                : OptionalInt.empty();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
        int minY = heightAccessor.getMinY();
        C current = policy.queryCurrent("base column");
        try (NativeGenerationScope historyScope = openHistoryCoordinateScope(
                     current, x, z, "modded_base_column");
             NativeGenerationLease lease = policy.queryLease(current, "base column");
             NativeGenerationScope ignored = policy.context(current, lease.sessionId())) {
            NativeBlockColumn resolved = policy.resolvedColumn(current, x, z);
            if (resolved != null) {
                return NativeTransitionColumn.column(resolved, heightAccessor, policy::placementKey);
            }
            BlockState[] states = new BlockState[heightAccessor.getHeight()];
            BlockState airState = Blocks.AIR.defaultBlockState();
            int surface = policy.terrainHeight(current, x, z, true, true);
            int fluid = policy.terrainHeight(current, x, z, false, true);
            BlockState stone = Blocks.STONE.defaultBlockState();
            BlockState water = Blocks.WATER.defaultBlockState();
            int solidEnd = Math.max(0, Math.min(states.length, surface + 1));
            int fluidEnd = Math.max(solidEnd, Math.max(0, Math.min(states.length, fluid + 1)));
            Arrays.fill(states, 0, solidEnd, stone);
            Arrays.fill(states, solidEnd, fluidEnd, water);
            Arrays.fill(states, fluidEnd, states.length, airState);
            return new NoiseColumn(minY, states);
        } catch (Throwable failure) {
            throw policy.queryFailure("base column", failure);
        }
    }

    private boolean allowsRoutedDiscreteGeneration(
            C current,
            NativeGenerationRoute route,
            ChunkPos chunkPos,
            ChunkAccess chunk,
            ChunkStatus stage
    ) {
        if (chunk.getPersistedStatus().isOrAfter(stage)) {
            return false;
        }
        if (route == null) {
            return policy.historyBypass(current);
        }
        return NativeGenerationWriteGuard.allowsPendingStage(policy.chunkWrites(current), chunk, stage)
                || policy.allowsNewGeneration(current, chunkPos.x(), chunkPos.z());
    }

    public record Options<C, P extends StructureStartPlan, O extends StructureOwnershipRecordView<O>>(
            NativeGeneratorContext context, NativeGeneratorOwner owner, NativeModdedGeneratorPolicy<C, P, O> policy,
            NativeChunkGeneratorDefinition definition) {
        public Options {
            Objects.requireNonNull(context);
            Objects.requireNonNull(owner);
            Objects.requireNonNull(policy);
            Objects.requireNonNull(definition);
        }
    }

    private record Components<C>(NativeModdedBiomeSource<NativeModdedBiomePolicy<Holder<Biome>, Climate.Sampler>> biomes,
                                 NativeModdedGeneratorPolicy.FeatureStage<C> features) {}
}

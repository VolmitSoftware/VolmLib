package art.arcane.volmlib.nativelib.v26_2_R1.terrain;

import art.arcane.volmlib.nativelib.terrain.NativeBukkitGeneratorContext;
import art.arcane.volmlib.nativelib.terrain.NativeBiomeSourcePolicy;
import art.arcane.volmlib.nativelib.terrain.NativeBiomeRegistry;
import art.arcane.volmlib.nativelib.terrain.NativeGenerationRoute;
import art.arcane.volmlib.nativelib.terrain.NativeGenerationLease;
import art.arcane.volmlib.nativelib.terrain.NativeGenerationScope;
import art.arcane.volmlib.nativelib.terrain.NativeSpawnSelection;
import art.arcane.volmlib.nativelib.terrain.structure.StructureStartPlan;
import art.arcane.volmlib.nativelib.terrain.structure.StructureOwnershipRecordView;
import java.lang.reflect.InvocationTargetException;
import art.arcane.volmlib.nativelib.v26_2_R1.terrain.NativeBukkitStructureStage;
import art.arcane.volmlib.nativelib.v26_2_R1.terrain.NativeStructureStateLifecycle;
import art.arcane.volmlib.nativelib.v26_2_R1.terrain.NativeTerrainPipeline;



import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureVolumeSource;



import art.arcane.volmlib.nativelib.v26_2_R1.terrain.NativeImportedFeatureStage;

import art.arcane.volmlib.nativelib.v26_2_R1.terrain.NativeBiomeSourceImpl;
import art.arcane.volmlib.nativelib.v26_2_R1.terrain.NativeBiomeRegistryImpl;

import art.arcane.volmlib.nativelib.terrain.structure.StructurePalette;

import art.arcane.volmlib.nativelib.v26_2_R1.terrain.NativeStructureWorldgenAccess;

import art.arcane.volmlib.nativelib.v26_2_R1.terrain.VanillaStructureBiomes;

import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeTerrainColumns;
import java.util.Optional;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeGenerationWriteGuard;
import java.util.function.LongPredicate;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureGenerationException;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureStartInjector;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureReferenceEnvelope;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureLocateResults;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureLocatePersistence;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureOwnershipRecovery;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructurePostProcessor;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureReferenceRepair;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureSurfaceFitter;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureTerrainIntegrator;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureVegetationClearer;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureVerticalPlacer;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureVanillaLocator;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.WorldgenTerrainHeightmaps;
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

public class NativeChunkGenerator<C, D, P extends StructureStartPlan, O extends StructureOwnershipRecordView<O>, R extends NativeGenerationRoute> extends CustomChunkGenerator implements LongPredicate {

    private static final Field BIOME_SOURCE;
    private static final Method SET_HEIGHT;
    private final ChunkGenerator delegate;
    private final NativeBukkitGeneratorContext<C, D, P, O, R> context;
    private final NativeBiomeSourceImpl customBiomeSource;
    private final NativeBiomeSourcePolicy<Holder<Biome>> biomePolicy;
    private final ServerLevel runtimeLevel;
    private final int runtimeMinY;
    private final int runtimeHeight;
    private final int runtimeSeaLevel;
    private final ConcurrentHashMap<SpawnTableKey, WeightedList<MobSpawnSettings.SpawnerData>> mergedSpawnTables = new ConcurrentHashMap<>();
    private final NativeImportedFeatureStage<D> importedFeatures;
    private final NativeTerrainColumns terrainColumns;
    private final NativeTerrainPipeline<R> terrainPipeline;
    private final NativeStructureStateLifecycle structureStateLifecycle;
    private final NativeBukkitStructureStage<C, D, P, O> nativeStructures;

    public NativeChunkGenerator(Configuration<C, D, P, O, R> configuration) {
        this(configuration, createBiomeRuntime(configuration.context()));
    }

    private NativeChunkGenerator(Configuration<C, D, P, O, R> configuration, BiomeRuntime biomes) {
        super(((CraftWorld) configuration.world()).getHandle(), edit(configuration.delegate(), biomes.source()), configuration.world().getGenerator());
        this.delegate = configuration.delegate();
        this.context = configuration.context();
        this.customBiomeSource = biomes.source();
        this.biomePolicy = biomes.policy();
        this.importedFeatures = new NativeImportedFeatureStage<>(context.importedFeatures());
        ServerLevel level = ((CraftWorld) configuration.world()).getHandle();
        this.runtimeLevel = level;
        this.structureStateLifecycle = new NativeStructureStateLifecycle(new NativeStructureStateLifecycle.Configuration(
                level, this, context.bootstrap()));
        this.terrainColumns = new NativeTerrainColumns(context.columns());
        this.runtimeMinY = level.getMinY();
        this.runtimeHeight = level.getHeight();
        this.runtimeSeaLevel = runtimeMinY + context.fluidHeight();
        this.nativeStructures = new NativeBukkitStructureStage<>(new NativeBukkitStructureStage.Configuration<>(
                this, customBiomeSource, runtimeMinY, context.structures(), context.structureCache(), context.structureActivationKey()));
        this.terrainPipeline = new NativeTerrainPipeline<>(new NativeTerrainPipeline.Configuration<>(
                delegate, context, biomePolicy, runtimeMinY));
        context.onRetirement(this::evictRuntimeCaches);
        installNativeStructureVolumeIndex(level);
    }

    private void evictRuntimeCaches(int runtimeId) {
        terrainColumns.evictRuntime(runtimeId);
        importedFeatures.evictRuntime(runtimeId);
        mergedSpawnTables.keySet().removeIf(key -> key.runtimeId() == runtimeId);
        nativeStructures.evictRuntime(runtimeId);
    }

    private void installNativeStructureVolumeIndex(ServerLevel level) {
        WeakReference<ServerLevel> levelReference = new WeakReference<>(level);
        WeakReference<ChunkGenerator> generatorReference = new WeakReference<>(this);
        WeakReference<BiomeSource> biomeSourceReference = new WeakReference<>(customBiomeSource);
        context.installVolumes( new NativeStructureVolumeSource<>(
                new NativeStructureVolumeSource.Context(
                level.registryAccess(),
                level.getServer().getStructureManager(),
                level.dimension(),
                LevelHeightAccessor.create(runtimeMinY, runtimeHeight),
                generatorReference::get,
                biomeSourceReference::get,
                () -> {
                    ServerLevel active = levelReference.get();
                    return active == null ? null : active.getChunkSource().getGeneratorState();
                }),
                context.volumes())::volumesAt);
    }

    @Override
    public @Nullable Pair<BlockPos, Holder<Structure>> findNearestMapStructure(ServerLevel level, HolderSet<Structure> holders, BlockPos pos, int radius, boolean findUnexplored) {
        if (!context.generateStructures()) {
            return null;
        }
        if (level != runtimeLevel || level.getChunkSource().getGenerator() != this) {
            return null;
        }
        try (R route = context.openRoute(
                     SectionPos.blockToSectionCoord(pos.getX()),
                     SectionPos.blockToSectionCoord(pos.getZ()),
                     "bukkit_nms_structure_locate");
             NativeGenerationScope runtimeScope = openHistoryRuntimeScope(route);
             NativeGenerationLease lease = context.acquireLease("bukkit_nms_structure_locate");
             NativeGenerationScope ignored = context.openContext(lease)) {
            HolderSet<Structure> reachable = nativeStructures.filterReachableStructures(level, holders);
            NativeStructureVanillaLocator.Candidate nativeCandidate =
                    reachable == null || reachable.size() == 0 ? null
                            : NativeStructureVanillaLocator.predict(
                                    level, reachable, pos, radius, findUnexplored);
            return nativeStructures.findNearestStructure(
                    level, holders, pos, Math.max(0, radius),
                    findUnexplored, context.current(), nativeCandidate);
        }
    }







    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return MapCodec.unit(null);
    }

    @Override
    public ChunkGenerator getDelegate() {
        if (delegate instanceof CustomChunkGenerator chunkGenerator)
            return chunkGenerator.getDelegate();
        return delegate;
    }

    @Override
    public int getMinY() {
        return runtimeMinY;
    }

    @Override
    public int getSeaLevel() {
        return runtimeSeaLevel;
    }

    @Override
    public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState structureState, StructureManager structureManager, ChunkAccess access, StructureTemplateManager templateManager, ResourceKey<Level> levelKey) {
        if (!context.generateStructures()) {
            return;
        }
        if (runtimeLevel.getChunkSource().getGenerator() != this
                || runtimeLevel.getChunkSource().getGeneratorState() != structureState) {
            return;
        }
        ChunkPos chunkPos = access.getPos();
        try (NativeGenerationScope stage = context.acquireStage("bukkit_nms_create_structures");
             R route = context.openRoute(
                     chunkPos.x(), chunkPos.z(), "bukkit_nms_create_structures");
             NativeGenerationScope runtimeScope = openHistoryRuntimeScope(route);
             NativeGenerationLease lease = context.acquireLease("bukkit_nms_create_structures");
             NativeGenerationScope ignored = context.openContext(lease)) {
            if (!allowsRoutedDiscreteGeneration(access, ChunkStatus.STRUCTURE_STARTS)) {
                return;
            }
            Map<Structure, StructureStart> previousStarts = new HashMap<>(access.getAllStarts());
            super.createStructures(registryAccess, structureState, structureManager, access, templateManager, levelKey);
            Map<Structure, P> configuredStarts = NativeStructureStartInjector.inject(
                    new NativeStructureStartInjector.InjectionContext<>(
                            context.injection(),
                            registryAccess,
                            structureState,
                            structureManager,
                            access,
                            templateManager,
                            levelKey,
                            this,
                            customBiomeSource
                    ));
            nativeStructures.adjustGeneratedStructures(
                    registryAccess, access, previousStarts, configuredStarts, templateManager);
            if (route != null) {
                access.persistentDataContainer.set(context.structureActivationKey(),
                        PersistentDataType.LONG, context.activationId(route));
                access.markUnsaved();
            }
        }
    }



    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> holderlookup, RandomState randomstate, long i, SpigotWorldConfig conf) {
        return delegate.createState(holderlookup, randomstate, i, conf);
    }

    public NativeStructureStateLifecycle structureStateLifecycle() {
        return structureStateLifecycle;
    }

    @Override
    public void createReferences(WorldGenLevel generatoraccessseed, StructureManager structuremanager, ChunkAccess ichunkaccess) {
        if (!context.generateStructures()) {
            return;
        }
        if (runtimeLevel.getChunkSource().getGenerator() != this) {
            return;
        }
        ChunkPos chunkPos = ichunkaccess.getPos();
        try (NativeGenerationScope stage = context.acquireStage("bukkit_nms_create_references");
             R route = context.openRoute(
                     chunkPos.x(), chunkPos.z(), "bukkit_nms_create_references");
             NativeGenerationScope runtimeScope = openHistoryRuntimeScope(route);
             NativeGenerationLease lease = context.acquireLease("bukkit_nms_create_references");
             NativeGenerationScope ignored = context.openContext(lease)) {
            NativeStructureReferenceRepair.createReferences(
                    context.structures().structurePolicy(context.current()), generatoraccessseed, structuremanager, ichunkaccess);
        }
    }

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(RandomState randomstate, Blender blender, StructureManager structuremanager, ChunkAccess ichunkaccess) {
        return terrainPipeline.createBiomes(randomstate, blender, structuremanager, ichunkaccess);
    }

    @Override
    public void buildSurface(WorldGenRegion regionlimitedworldaccess, StructureManager structuremanager, RandomState randomstate, ChunkAccess ichunkaccess) {
        terrainPipeline.buildSurface(regionlimitedworldaccess, structuremanager, randomstate, ichunkaccess);
    }

    @Override
    public void applyCarvers(WorldGenRegion regionlimitedworldaccess, long seed, RandomState randomstate, BiomeManager biomemanager, StructureManager structuremanager, ChunkAccess ichunkaccess) {
        terrainPipeline.applyCarvers(regionlimitedworldaccess, seed, randomstate, biomemanager, structuremanager, ichunkaccess);
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomstate, StructureManager structuremanager, ChunkAccess ichunkaccess) {
        return terrainPipeline.fillFromNoise(blender, randomstate, structuremanager, ichunkaccess);
    }





    @Override
    public WeightedList<MobSpawnSettings.SpawnerData> getMobsAt(Holder<Biome> holder, StructureManager structuremanager, MobCategory enumcreaturetype, BlockPos blockposition) {
        return context.admittedSpawns(
                () -> getAdmittedMobsAt(holder, structuremanager, enumcreaturetype, blockposition),
                WeightedList.of(List.of()));
    }

    private WeightedList<MobSpawnSettings.SpawnerData> getAdmittedMobsAt(
            Holder<Biome> holder, StructureManager structuremanager, MobCategory enumcreaturetype, BlockPos blockposition) {
        NativeSpawnSelection selection = context.spawnSelection( blockposition.getX(), blockposition.getY(), blockposition.getZ(),
                holder.unwrapKey().map(key -> key.identifier().toString()).orElse(""));
        if (selection.mode() == NativeSpawnSelection.Mode.LOADING) {
            return WeightedList.of(List.of());
        }
        try (NativeGenerationScope route = context.openCoordinateScope(
                     blockposition.getX(), blockposition.getZ(), "bukkit_nms_mob_spawns")) {
            return getMobsAtWithActiveRuntime(holder, structuremanager, enumcreaturetype, blockposition, selection);
        }
    }

    private WeightedList<MobSpawnSettings.SpawnerData> getMobsAtWithActiveRuntime(
            Holder<Biome> holder,
            StructureManager structuremanager,
            MobCategory enumcreaturetype,
            BlockPos blockposition,
            NativeSpawnSelection selection
    ) {
        Holder<Biome> vanillaSpawnBiome = switch (selection.mode()) {
            case RETAINED -> customBiomeSource.getRetainedVanillaSpawnBiome(selection.derivativeKey());
            case CURRENT -> customBiomeSource.getVanillaSpawnBiome(holder);
            case NONE, LOADING -> null;
        };
        if (vanillaSpawnBiome == null) {
            return delegate.getMobsAt(holder, structuremanager, enumcreaturetype, blockposition);
        }

        WeightedList<MobSpawnSettings.SpawnerData> vanillaSpawns = vanillaSpawnBiome.value().getMobSettings().getMobs(enumcreaturetype);
        WeightedList<MobSpawnSettings.SpawnerData> resolvedSpawns = delegate.getMobsAt(
                vanillaSpawnBiome, structuremanager, enumcreaturetype, blockposition);
        if (resolvedSpawns != vanillaSpawns) {
            return resolvedSpawns;
        }

        WeightedList<MobSpawnSettings.SpawnerData> explicitSpawns = holder.value().getMobSettings().getMobs(enumcreaturetype);
        if (explicitSpawns.isEmpty()) {
            return vanillaSpawns;
        }
        if (vanillaSpawns.isEmpty()) {
            return explicitSpawns;
        }

        int spawnRuntimeId = context.runtimeId();
        SpawnTableKey key = new SpawnTableKey(spawnRuntimeId, holder.value(), vanillaSpawnBiome.value(), enumcreaturetype);
        return mergedSpawnTables.computeIfAbsent(key, ignored -> mergeSpawnTables(vanillaSpawns, explicitSpawns));
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel generatoraccessseed, ChunkAccess ichunkaccess, StructureManager structuremanager) {
        applyBiomeDecoration(generatoraccessseed, ichunkaccess, structuremanager, true);
    }

    @Override
    public void addDebugScreenInfo(List<String> list, RandomState randomstate, BlockPos blockposition) {
        delegate.addDebugScreenInfo(list, randomstate, blockposition);
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel generatoraccessseed, ChunkAccess ichunkaccess, StructureManager structuremanager, boolean vanilla) {
        ChunkPos chunkPos = ichunkaccess.getPos();
        try (NativeGenerationScope stage = context.acquireStage("bukkit_nms_biome_decoration");
             R route = context.openRoute(
                     chunkPos.x(), chunkPos.z(), "bukkit_nms_biome_decoration");
             NativeGenerationScope runtimeScope = openHistoryRuntimeScope(route);
             NativeGenerationLease lease = context.acquireLease("bukkit_nms_biome_decoration");
            NativeGenerationScope ignored = context.openContext(lease)) {
            boolean flatStudioTerrain = context.usesFlatTerrain();
            if (!flatStudioTerrain && !ichunkaccess.getPersistedStatus().isOrAfter(ChunkStatus.FEATURES)) {
                nativeStructures.placeVanillaStructures(generatoraccessseed, ichunkaccess, structuremanager);
            }
            if (allowsRoutedDiscreteGeneration(ichunkaccess, ChunkStatus.FEATURES)
                    && NativeGenerationWriteGuard.allowsDecoration(context::allowsChunkWrite, generatoraccessseed, chunkPos)) {
                if (!flatStudioTerrain) {
                    // Bind-time equivalent for Bukkit: the table is built on the first decorated chunk, which is where a
                    // feature-order cycle is reported once and degraded to features-off.
                    importedFeatures.prepare(generatoraccessseed);
                }
                addVanillaDecorations(generatoraccessseed, ichunkaccess, structuremanager);
                if (!flatStudioTerrain) {
                    // Vanilla's placed-feature pass, on THIS thread. The delegate is still called with
                    // addVanillaDecorations=false below, so the vanilla half never runs twice. Inert unless the
                    // dimension set importedFeatures.enabled.
                    importedFeatures.run(generatoraccessseed, ichunkaccess, this);
                }
                delegate.applyBiomeDecoration(generatoraccessseed, ichunkaccess, structuremanager, false);
            }
            NativeTerrainPipeline.claimGeneratedSemantics(route, ichunkaccess, context.minimumY());
        }
    }

    /**
     * Iris custom biomes carry no features in their datapack JSON by design; when importedFeatures is on they
     * inherit the generation settings of the vanilla biome their Iris biome derives from. This is the gate
     * {@code BiomeFilter} consults, so it has to agree with the feature pass. With the control off this is
     * exactly the inherited behaviour.
     */
    @Override
    public BiomeGenerationSettings getBiomeGenerationSettings(Holder<Biome> holder) {
        BiomeGenerationSettings imported = importedFeatures.generationSettings(holder);
        return imported == null ? super.getBiomeGenerationSettings(holder) : imported;
    }















    @Override
    public void addVanillaDecorations(WorldGenLevel level, ChunkAccess chunkAccess, StructureManager structureManager) {
        ChunkPos chunkPos = chunkAccess.getPos();
        try (NativeGenerationScope historyScope = context.openCoordinateScope(
                     chunkPos.getMinBlockX(), chunkPos.getMinBlockZ(), "bukkit_nms_heightmaps");
             NativeGenerationLease lease = context.acquireLease("bukkit_nms_heightmaps");
             NativeGenerationScope ignored = context.openContext(lease)) {
            SectionPos sectionPos = SectionPos.of(chunkAccess.getPos(), level.getMinSectionY());
            BlockPos blockPos = sectionPos.origin();

            terrainPipeline.primeHeightmaps(chunkAccess);
            if (context.usesFlatTerrain()) {
                return;
            }

            Heightmap motion = chunkAccess.getOrCreateHeightmapUnprimed(Heightmap.Types.MOTION_BLOCKING);
            Heightmap motionNoLeaves = chunkAccess.getOrCreateHeightmapUnprimed(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES);
            int minHeight = context.minimumY();

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int wX = x + blockPos.getX();
                    int wZ = z + blockPos.getZ();

                    int terrainTop = context.terrainHeight(wX, wZ, false) + minHeight + 1;
                    setHeight(motion, x, z, terrainTop);
                    setHeight(motionNoLeaves, x, z, terrainTop);
                }
            }

            Heightmap.primeHeightmaps(chunkAccess, ChunkStatus.FINAL_HEIGHTMAPS);
        }
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        ChunkPos center = region.getCenter();
        try (NativeGenerationScope stage = context.acquireStage("bukkit_nms_spawn_original_mobs");
             R route = context.openRoute(
                     center.x(), center.z(), "bukkit_nms_spawn_original_mobs");
             NativeGenerationScope runtimeScope = openHistoryRuntimeScope(route);
             NativeGenerationLease lease = context.acquireLease("bukkit_nms_spawn_original_mobs");
             NativeGenerationScope ignored = context.openContext(lease)) {
            if (!allowsRoutedDiscreteGeneration(region.getChunk(center.x(), center.z()), ChunkStatus.SPAWN)) {
                return;
            }
            Holder<Biome> visibleBiome;
            if (!context.stacked()) {
                visibleBiome = region.getBiome(center.getWorldPosition().atY(region.getMaxY()));
            } else {
                visibleBiome = customBiomeSource.getVisibleSurfaceBiome(
                        center.getMinBlockX() + 8,
                        center.getMinBlockZ() + 8);
                if (visibleBiome == null) {
                    visibleBiome = region.getBiome(center.getWorldPosition().atY(region.getMaxY()));
                }
            }
            Holder<Biome> vanillaBiome = customBiomeSource.getVanillaSpawnBiome(visibleBiome);
            WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
            random.setDecorationSeed(region.getSeed(), center.getMinBlockX(), center.getMinBlockZ());
            NaturalSpawner.spawnMobsForChunkGeneration(
                    region, vanillaBiome == null ? visibleBiome : vanillaBiome, center, random);
        }
    }

    @Override
    public boolean test(long packedChunk) {
        return context.active()
                && context.allowsChunkWrite(ChunkPos.getX(packedChunk), ChunkPos.getZ(packedChunk));
    }

    private boolean allowsRoutedDiscreteGeneration(ChunkAccess chunk, ChunkStatus stage) {
        if (chunk.getPersistedStatus().isOrAfter(stage)) {
            return false;
        }
        ChunkPos chunkPos = chunk.getPos();
        return NativeGenerationWriteGuard.allowsPendingStage(context::allowsChunkWrite, chunk, stage)
                || context.allowsNewChunk(chunkPos.x(), chunkPos.z());
    }

    private static WeightedList<MobSpawnSettings.SpawnerData> mergeSpawnTables(
            WeightedList<MobSpawnSettings.SpawnerData> vanillaSpawns,
            WeightedList<MobSpawnSettings.SpawnerData> explicitSpawns) {
        List<Weighted<MobSpawnSettings.SpawnerData>> entries = new ArrayList<>(
                vanillaSpawns.unwrap().size() + explicitSpawns.unwrap().size());
        Set<EntityType<?>> explicitTypes = new HashSet<>();
        for (Weighted<MobSpawnSettings.SpawnerData> entry : explicitSpawns.unwrap()) {
            explicitTypes.add(entry.value().type());
        }
        for (Weighted<MobSpawnSettings.SpawnerData> entry : vanillaSpawns.unwrap()) {
            if (!explicitTypes.contains(entry.value().type())) {
                entries.add(entry);
            }
        }
        entries.addAll(explicitSpawns.unwrap());
        return WeightedList.of(entries);
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor levelheightaccessor) {
        return delegate.getSpawnHeight(levelheightaccessor);
    }

    @Override
    public int getGenDepth() {
        return runtimeHeight;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor height, RandomState random) {
        return terrainColumns.baseHeight(x, z, type, height);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        return terrainColumns.baseColumn(x, z, height);
    }







    private static NativeGenerationScope openHistoryRuntimeScope(
            NativeGenerationRoute route
    ) {
        return route == null ? null : route.openRuntimeScope();
    }



    @Override
    public Optional<Identifier> getTypeNameForDataFixer() {
        return delegate.getTypeNameForDataFixer();
    }

    @Override
    public void validate() {
        delegate.validate();
    }

    static {
        List<Field> biomeSources = new ArrayList<>(1);
        for (Field field : ChunkGenerator.class.getDeclaredFields()) {
            if (!field.getType().equals(BiomeSource.class))
                continue;
            biomeSources.add(field);
        }
        if (biomeSources.size() != 1)
            throw new IllegalStateException("Expected exactly one BiomeSource field in ChunkGenerator, found "
                    + biomeSources.size() + " " + biomeSources.stream().map(Field::getName).toList());
        Field biomeSource = biomeSources.getFirst();

        List<Method> setHeights = new ArrayList<>(1);
        for (Method method : Heightmap.class.getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (!method.getName().equals("setHeight")
                    || !Arrays.equals(types, new Class<?>[]{int.class, int.class, int.class})
                    || !method.getReturnType().equals(void.class))
                continue;
            setHeights.add(method);
        }
        if (setHeights.size() != 1)
            throw new IllegalStateException("Expected exactly one Heightmap.setHeight(int,int,int) method, found "
                    + setHeights.size());
        Method setHeight = setHeights.getFirst();

        biomeSource.setAccessible(true);
        BIOME_SOURCE = biomeSource;
        setHeight.setAccessible(true);
        SET_HEIGHT = setHeight;
    }

    private static ChunkGenerator edit(ChunkGenerator generator, BiomeSource source) {
        try {
            BIOME_SOURCE.set(generator, source);
            if (generator instanceof CustomChunkGenerator custom)
                BIOME_SOURCE.set(custom.getDelegate(), source);

            return generator;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private record SpawnTableKey(int runtimeId, Biome biome, Biome vanillaBiome, MobCategory category) {
    }

    private static BiomeRuntime createBiomeRuntime(NativeBukkitGeneratorContext<?, ?, ?, ?, ?> context) {
        NativeBiomeSourcePolicy<Holder<Biome>> policy = context.biomes(new NativeBiomeRegistryImpl());
        return new BiomeRuntime(new NativeBiomeSourceImpl(policy), policy);
    }

    private void setHeight(Heightmap heightmap, int x, int z, int height) {
        try {
            SET_HEIGHT.invoke(heightmap, x, z, height);
        } catch (InvocationTargetException | IllegalAccessException failure) {
            context.failed("Native heightmap publication failed", failure);
        }
    }

    public record Configuration<C, D, P extends StructureStartPlan, O extends StructureOwnershipRecordView<O>, R extends NativeGenerationRoute>(
            ChunkGenerator delegate, World world, NativeBukkitGeneratorContext<C, D, P, O, R> context) {
    }


    private record BiomeRuntime(NativeBiomeSourceImpl source, NativeBiomeSourcePolicy<Holder<Biome>> policy) {
    }
}

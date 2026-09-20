package art.arcane.volmlib.nativelib.v26_3_R1.terrain;

import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeChunkGenerator;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureStateLifecycle;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureSetFilter;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeWorldLifecycle;
import art.arcane.volmlib.nativelib.terrain.NativeTerrainSnapshots;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeTerrainSnapshotsImpl;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeGenerationRegistryImpl;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeJigsawMetadata;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeTerrainAccessImpl;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructurePoiUpdates;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.VanillaStructureBiomes;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.util.ARGB;
import org.joml.Vector3fc;
import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import art.arcane.volmlib.nativelib.terrain.ServerShutdownBoundary;
import art.arcane.volmlib.nativelib.terrain.BiomeColor;
import art.arcane.volmlib.nativelib.terrain.BlockProperty;
import art.arcane.volmlib.util.cache.AtomicCache;
import art.arcane.volmlib.nativelib.terrain.structure.NativeStructureVolume;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureFactory;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureGenerationException;
import art.arcane.volmlib.nativelib.terrain.JigsawSourceMetadata;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.json.JSONObject;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.math.Vector3d;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import art.arcane.volmlib.util.matter.MatterBiomeInject;
import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import art.arcane.volmlib.util.nbt.mca.palette.MCABiomeContainer;
import art.arcane.volmlib.util.nbt.mca.palette.MCAChunkBiomeContainer;
import art.arcane.volmlib.util.nbt.mca.palette.MCAGlobalPalette;
import art.arcane.volmlib.util.nbt.mca.palette.MCAIdMap;
import art.arcane.volmlib.util.nbt.mca.palette.MCAIdMapper;
import art.arcane.volmlib.util.nbt.mca.palette.MCAPalette;
import art.arcane.volmlib.util.nbt.mca.palette.MCAPaletteAccess;
import art.arcane.volmlib.util.nbt.mca.palette.MCAPalettedContainer;
import art.arcane.volmlib.util.nbt.mca.palette.MCAWrappedPalettedContainer;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.data.BlockDataAccessor;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.tags.TagKey;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.feature.AbstractHugeMushroomFeature;
import net.minecraft.world.level.levelgen.feature.FallenTreeFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.block.CraftBlockStates;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.generator.CraftChunkData;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import java.awt.Color;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.level.chunk.ChunkGenerator;
import art.arcane.volmlib.nativelib.terrain.NativeWorldGeneration;
import art.arcane.volmlib.nativelib.terrain.NativeBukkitGeneratorContext;
import art.arcane.volmlib.nativelib.terrain.NativeGenerationRoute;
import art.arcane.volmlib.nativelib.terrain.NativeWorldLifecyclePolicy;
import art.arcane.volmlib.nativelib.terrain.NativeWorldLifecycleFactory;
import art.arcane.volmlib.nativelib.terrain.structure.StructureStartPlan;
import art.arcane.volmlib.nativelib.terrain.structure.StructureOwnershipRecordView;

public final class NativeWorldGenerationImpl implements NativeWorldGeneration {
    public <C, D, P extends StructureStartPlan, O extends StructureOwnershipRecordView<O>, R extends NativeGenerationRoute>
    void inject(World world, NativeBukkitGeneratorContext<C, D, P, O, R> context) throws NoSuchFieldException, IllegalAccessException {
        ServerLevel level = ((CraftWorld) world).getHandle();

        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        Field worldGenContextField = getField(chunkMap.getClass(), WorldGenContext.class);
        worldGenContextField.setAccessible(true);
        WorldGenContext worldGenContext = (WorldGenContext) worldGenContextField.get(chunkMap);

        NativeChunkGenerator<C, D, P, O, R> nativeGenerator = new NativeChunkGenerator<>(new NativeChunkGenerator.Configuration<>(worldGenContext.generator(), world,
                context));
        WorldGenContext newContext = new WorldGenContext(
                worldGenContext.level(), nativeGenerator,
                worldGenContext.structureManager(), worldGenContext.lightEngine(), worldGenContext.mainThreadExecutor(), worldGenContext.unsavedListener());

        worldGenContextField.set(chunkMap, newContext);
        ChunkGenerator activeGenerator = level.getChunkSource().getGenerator();
        if (activeGenerator != nativeGenerator) {
            throw new IllegalStateException("Native generator injection did not become the active Paper chunk generator; active="
                    + activeGenerator.getClass().getName());
        }
        retargetStructureCheck(level, nativeGenerator);
    }

    @Override
    public ScopeResult scope(ScopeRequest request) throws NoSuchFieldException, IllegalAccessException {
        World world = request.world();
        ServerLevel level = ((CraftWorld) world).getHandle();
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        ChunkGeneratorStructureState currentState = level.getChunkSource().getGeneratorState();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        ChunkGeneratorStructureState scopedState = createStructureState(level, generator, currentState);
        Registry<StructureSet> structureSetRegistry =
                level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        NativeStructureSetFilter.Selection selection = NativeStructureSetFilter.filter(
                scopedState.possibleStructureSets(),
                request.scope(),
                request.declaredSources(),
                request.control(),
                NativeStructureSetFilter.keyIndex(
                        structureSetRegistry.listElements().toList()));

        Field possibleSetsField = getField(scopedState.getClass(), List.class);
        possibleSetsField.setAccessible(true);
        possibleSetsField.set(scopedState, selection.structureSets());

        Field stateField = getField(chunkMap.getClass(), ChunkGeneratorStructureState.class);
        stateField.setAccessible(true);
        boolean studioBootstrap = request.mode() == ScopeMode.RETAINED;
        boolean authoringStudio = request.mode() == ScopeMode.AUTHORING;
        if (authoringStudio) {
            requireNativeGenerator(generator);
            if (currentState.possibleStructureSets().isEmpty()) {
                currentState.ensureStructuresGenerated();
            } else {
                ChunkGeneratorStructureState bootstrapState = createStructureState(level, generator, currentState);
                Field bootstrapSetsField = getField(bootstrapState.getClass(), List.class);
                bootstrapSetsField.setAccessible(true);
                bootstrapSetsField.set(bootstrapState, List.of());
                bootstrapState.ensureStructuresGenerated();
                stateField.set(chunkMap, bootstrapState);
            }
        } else if (studioBootstrap) {
            NativeChunkGenerator<?, ?, ?, ?, ?> nativeGenerator = requireNativeGenerator(generator);
            nativeGenerator.structureStateLifecycle().retainState(level, chunkMap, scopedState);
            try {
                stateField.set(chunkMap, scopedState);
            } catch (IllegalAccessException | RuntimeException | Error failure) {
                nativeGenerator.structureStateLifecycle().abandonRetainedState();
                throw failure;
            }
        } else {
            initializeAndPublishStructureState(
                    generator,
                    scopedState,
                    () -> stateField.set(chunkMap, scopedState));
        }
        return new ScopeResult(
                selection.retainedManagedSets(),
                selection.excludedManagedSets());
    }

    @Override
    public CompletableFuture<Void> completeBootstrap(World world) throws NoSuchFieldException, IllegalAccessException {
        ServerLevel level = ((CraftWorld) world).getHandle();
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        NativeChunkGenerator<?, ?, ?, ?, ?> generator = requireNativeGenerator(level.getChunkSource().getGenerator());
        NativeStructureStateLifecycle.RetainedState retained =
                generator.structureStateLifecycle().retainedState(level, chunkMap);
        if (retained == null) {
            return CompletableFuture.completedFuture(null);
        }
        return generator.structureStateLifecycle().activateRetainedState(retained);
    }

    @Override
    public void abandonBootstrap(World world) {
        ServerLevel level = ((CraftWorld) world).getHandle();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (generator instanceof NativeChunkGenerator<?, ?, ?, ?, ?> nativeGenerator) {
            nativeGenerator.structureStateLifecycle().abandonRetainedState();
        }
    }

    private ChunkGeneratorStructureState createStructureState(
            ServerLevel level,
            ChunkGenerator generator,
            ChunkGeneratorStructureState currentState
    ) {
        return generator.createState(
                level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET),
                currentState.randomState(),
                currentState.getLevelSeed(),
                level.spigotConfig);
    }

    private void initializeAndPublishStructureState(
            ChunkGenerator generator,
            ChunkGeneratorStructureState structureState,
            NativeStructureStateLifecycle.StructureStatePublisher publisher
    ) throws IllegalAccessException {
        if (generator instanceof NativeChunkGenerator<?, ?, ?, ?, ?> nativeGenerator) {
            nativeGenerator.structureStateLifecycle().initializeAndPublishStructureState(structureState, publisher);
            return;
        }
        structureState.ensureStructuresGenerated();
        publisher.publish();
    }

    private NativeChunkGenerator<?, ?, ?, ?, ?> requireNativeGenerator(
            ChunkGenerator generator
    ) {
        if (generator instanceof NativeChunkGenerator<?, ?, ?, ?, ?> nativeGenerator) {
            return nativeGenerator;
        }
        throw new IllegalStateException("Studio native structure state is not owned by the active native generator.");
    }


    private static void retargetStructureCheck(ServerLevel level, NativeChunkGenerator<?, ?, ?, ?, ?> generator) throws NoSuchFieldException, IllegalAccessException {
        Field structureCheckField = getField(level.getClass(), StructureCheck.class);
        structureCheckField.setAccessible(true);
        Object structureCheck = structureCheckField.get(level);
        if (structureCheck == null) {
            return;
        }
        Field generatorField = getField(structureCheck.getClass(), ChunkGenerator.class);
        generatorField.setAccessible(true);
        generatorField.set(structureCheck, generator);
        Field biomeSourceField = getField(structureCheck.getClass(), BiomeSource.class);
        biomeSourceField.setAccessible(true);
        biomeSourceField.set(structureCheck, generator.getBiomeSource());
    }

    private static Field getField(Class<?> clazz, Class<?> fieldType) throws NoSuchFieldException {
        try {
            for (Field f : clazz.getDeclaredFields()) {
                if (f.getType().equals(fieldType))
                    return f;
            }
            throw new NoSuchFieldException(fieldType.getName());
        } catch (NoSuchFieldException var4) {
            Class<?> superClass = clazz.getSuperclass();
            if (superClass == null) {
                throw var4;
            } else {
                return getField(superClass, fieldType);
            }
        }
    }


    @Override
    public Dimension dimension(World world) {
        ServerLevel level = ((CraftWorld) world).getHandle();
        DimensionType type = level.dimensionType();
        String key = level.dimensionTypeRegistration().unwrapKey().map(value -> value.identifier().toString()).orElse("<unregistered>");
        return new Dimension(key, type.minY(), type.height(), type.logicalHeight(), level.getMinY(), level.getHeight());
    }

    @Override
    public NativeWorldLifecycleFactory.Controller lifecycle(NativeWorldLifecyclePolicy policy) {
        return new NativeWorldLifecycle(policy, NativeChunkGenerator.class.getName());
    }

    @Override
    public boolean missingDimensionTypes(List<NamespacedKey> keys) {
        Registry<DimensionType> types = ((CraftServer) Bukkit.getServer()).getServer().registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE);
        for (NamespacedKey key : keys) {
            if (!types.containsKey(Identifier.fromNamespaceAndPath(key.getNamespace(), key.getKey()))) { return true; }
        }
        return false;
    }

    @Override
    public String generationRendererIdentity() {
        return "bukkit-v26_3_R1-generated-registry-json-v1";
    }
}

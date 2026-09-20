package art.arcane.volmlib.nativelib.v26_2_R1.terrain;

import art.arcane.volmlib.nativelib.terrain.NativeGenerationLease;
import art.arcane.volmlib.nativelib.terrain.NativeGenerationRoute;
import art.arcane.volmlib.nativelib.terrain.NativeGenerationScope;
import art.arcane.volmlib.nativelib.terrain.NativeTerrainPipelinePolicy;
import art.arcane.volmlib.nativelib.terrain.NativeBiomeSourcePolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CancellationException;
import java.util.function.IntBinaryOperator;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.WorldgenTerrainHeightmaps;

public final class NativeTerrainPipeline<R extends NativeGenerationRoute> {
    private static final Set<Heightmap.Types> AUTHORING_HEIGHTMAPS = Set.of(
            Heightmap.Types.WORLD_SURFACE_WG,
            Heightmap.Types.OCEAN_FLOOR_WG,
            Heightmap.Types.WORLD_SURFACE,
            Heightmap.Types.OCEAN_FLOOR,
            Heightmap.Types.MOTION_BLOCKING,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES);

    private final ChunkGenerator delegate;
    private final NativeTerrainPipelinePolicy<R> policy;
    private final NativeBiomeSourcePolicy<Holder<Biome>> biomes;
    private final int minimumY;
    private final NamespacedKey naturalTerrainKey;

    public NativeTerrainPipeline(Configuration<R> configuration) {
        this.delegate = configuration.delegate();
        this.policy = configuration.policy();
        this.biomes = configuration.biomes();
        this.minimumY = configuration.minimumY();
        this.naturalTerrainKey = policy.naturalTerrainKey();
    }

    public CompletableFuture<ChunkAccess> createBiomes(RandomState randomstate, Blender blender, StructureManager structuremanager, ChunkAccess ichunkaccess) {
        ChunkPos chunkPos = ichunkaccess.getPos();
        try (NativeGenerationScope stage = policy.acquireStage("bukkit_nms_create_biomes");
             R route = policy.openRoute(
                     chunkPos.x(), chunkPos.z(), "bukkit_nms_create_biomes");
             NativeGenerationScope runtimeScope = openHistoryRuntimeScope(route);
             NativeGenerationLease lease = policy.acquireLease("bukkit_nms_create_biomes");
             NativeGenerationScope ignored = policy.openContext(lease)) {
            biomes.prepareVisibleBiomeBatch();
            NativeBiomeSourcePolicy.VisibleResolver<Holder<Biome>> resolver = biomes.visibleResolver();
            ichunkaccess.fillBiomesFromNoise(
                    (x, y, z, sampler) -> resolver.biome(x, y, z),
                    randomstate.sampler());
            return CompletableFuture.completedFuture(ichunkaccess);
        }
    }

    public void buildSurface(WorldGenRegion regionlimitedworldaccess, StructureManager structuremanager, RandomState randomstate, ChunkAccess ichunkaccess) {
        ChunkPos chunkPos = ichunkaccess.getPos();
        try (NativeGenerationScope stage = policy.acquireStage("bukkit_nms_build_surface");
             R route = policy.openRoute(
                     chunkPos.x(), chunkPos.z(), "bukkit_nms_build_surface");
             NativeGenerationScope runtimeScope = openHistoryRuntimeScope(route);
             NativeGenerationLease lease = policy.acquireLease("bukkit_nms_build_surface");
             NativeGenerationScope ignored = policy.openContext(lease)) {
            if (!policy.allowsChunkWrite(chunkPos.x(), chunkPos.z())) {
                return;
            }
            delegate.buildSurface(regionlimitedworldaccess, structuremanager, randomstate, ichunkaccess);
        }
    }

    public void applyCarvers(WorldGenRegion regionlimitedworldaccess, long seed, RandomState randomstate, BiomeManager biomemanager, StructureManager structuremanager, ChunkAccess ichunkaccess) {
        ChunkPos chunkPos = ichunkaccess.getPos();
        try (NativeGenerationScope stage = policy.acquireStage("bukkit_nms_apply_carvers");
             R route = policy.openRoute(
                     chunkPos.x(), chunkPos.z(), "bukkit_nms_apply_carvers");
             NativeGenerationScope runtimeScope = openHistoryRuntimeScope(route);
             NativeGenerationLease lease = policy.acquireLease("bukkit_nms_apply_carvers");
             NativeGenerationScope ignored = policy.openContext(lease)) {
            if (!policy.allowsChunkWrite(chunkPos.x(), chunkPos.z())) {
                return;
            }
            delegate.applyCarvers(regionlimitedworldaccess, seed, randomstate, biomemanager, structuremanager, ichunkaccess);
        }
    }

    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomstate, StructureManager structuremanager, ChunkAccess ichunkaccess) {
        ChunkPos chunkPos = ichunkaccess.getPos();
        NativeGenerationScope stage = policy.acquireStage("bukkit_nms_chunk_pipeline");
        R route;
        try {
            route = policy.openRoute(chunkPos.x(), chunkPos.z(), "bukkit_nms_chunk_pipeline");
        } catch (RuntimeException | Error failure) {
            stage.close();
            throw failure;
        }
        NativeGenerationLease lease;
        try {
            lease = policy.acquireLease("bukkit_nms_chunk_pipeline");
        } catch (RuntimeException | Error failure) {
            closeHistoryRoute(route, failure);
            stage.close();
            throw failure;
        }
        try {
            CompletableFuture<ChunkAccess> delegatePipeline;
            try (NativeGenerationScope runtimeScope = openHistoryRuntimeScope(route);
                 NativeGenerationScope ignored = policy.openContext(lease)) {
                delegatePipeline = delegate.fillFromNoise(
                        blender, randomstate, structuremanager, ichunkaccess);
            }
            CompletableFuture<ChunkAccess> pipeline = delegatePipeline
                    .thenApply(filled -> {
                        try (NativeGenerationScope runtimeScope =
                                     openHistoryRuntimeScope(route);
                             NativeGenerationScope ignored = policy.openContext(lease)) {
                            claimGeneratedSemantics(route, filled, policy.minimumY());
                            persistNaturalTerrain(filled, route);
                            primeHeightmaps(filled);
                            return filled;
                        }
                    });
            CompletableFuture<ChunkAccess> completion = new CompletableFuture<>();
            pipeline.whenComplete((filled, failure) -> {
                boolean cancelled = isCancellationFailure(failure);
                Throwable completionFailure = closeNoisePipelineResources(
                        failure, lease, route, stage);
                if (completionFailure == null) {
                    completion.complete(filled);
                } else if (cancelled) {
                    completion.cancel(false);
                } else {
                    completion.completeExceptionally(completionFailure);
                }
            });
            lease.detachThread();
            if (route != null) {
                route.detachThread();
            }
            return completion;
        } catch (RuntimeException | Error failure) {
            lease.close();
            closeHistoryRoute(route, failure);
            stage.close();
            throw failure;
        }
    }

    private void persistNaturalTerrain(ChunkAccess chunk, R route) {
        if (route == null) {
            return;
        }
        try {
            byte[] terrain = policy.naturalTerrainReceipt(route);
            if (terrain != null) {
                chunk.persistentDataContainer.set(naturalTerrainKey, PersistentDataType.BYTE_ARRAY, terrain);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to persist natural terrain for chunk " + chunk.getPos(), failure);
        }
    }

    private static Throwable closeNoisePipelineResources(
            Throwable failure,
            NativeGenerationLease lease,
            NativeGenerationRoute route,
            NativeGenerationScope stage
    ) {
        Throwable result = failure;
        try {
            lease.close();
        } catch (Throwable closeFailure) {
            result = appendFailure(result, closeFailure);
        }
        try {
            if (route != null) {
                route.close();
            }
        } catch (Throwable closeFailure) {
            result = appendFailure(result, closeFailure);
        }
        try {
            stage.close();
        } catch (Throwable closeFailure) {
            result = appendFailure(result, closeFailure);
        }
        return result;
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

    public void primeHeightmaps(ChunkAccess chunkAccess) {
        if (policy.usesFlatTerrain()) {
            primeAuthoringHeightmaps(chunkAccess);
            return;
        }
        WorldgenTerrainHeightmaps.primeTerrain(chunkAccess, worldgenSurfaceHeight(), worldgenFloorHeight());
    }

    public static void primeAuthoringHeightmaps(ChunkAccess chunkAccess) {
        Heightmap.primeHeightmaps(chunkAccess, AUTHORING_HEIGHTMAPS);
    }

    private IntBinaryOperator worldgenSurfaceHeight() {
        return (x, z) -> policy.terrainHeight(x, z, false) + minimumY + 1;
    }

    private IntBinaryOperator worldgenFloorHeight() {
        return (x, z) -> policy.terrainHeight(x, z, true) + minimumY + 1;
    }

    public static void claimGeneratedSemantics(
            NativeGenerationRoute route,
            ChunkAccess chunk,
            int minimumY
    ) {
        if (route == null) {
            return;
        }
        try {
            BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
            route.claimGeneratedSemantics((x, y, z) -> {
                BlockState state = chunk.getBlockState(position.set(x, minimumY + y, z));
                return state.isAir() || state.liquid();
            });
        } catch (IOException failure) {
            throw new IllegalStateException("Could not durably claim generated chunk semantics.", failure);
        }
    }

    private static NativeGenerationScope openHistoryRuntimeScope(NativeGenerationRoute route) {
        return route == null ? null : route.openRuntimeScope();
    }

    public record Configuration<R extends NativeGenerationRoute>(
            ChunkGenerator delegate, NativeTerrainPipelinePolicy<R> policy,
            NativeBiomeSourcePolicy<Holder<Biome>> biomes, int minimumY) {
    }
}

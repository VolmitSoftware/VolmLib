package art.arcane.volmlib.nativelib.v26_3_R1.terrain;

import art.arcane.volmlib.nativelib.terrain.NativeBlockChangeSet;
import art.arcane.volmlib.nativelib.terrain.NativePlacementFailures;
import art.arcane.volmlib.nativelib.terrain.feature.NativeImportedFeatureControl;
import art.arcane.volmlib.nativelib.terrain.feature.NativeImportedFeaturePolicy;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.levelgen.feature.Feature;

import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NativeImportedFeatureBlockChangeTest {
    private static final BlockPos REPLACED = new BlockPos(2, 65, 2);
    private static final BlockPos REMOVED = new BlockPos(17, 66, 2);
    private static final BlockPos PROTECTED = new BlockPos(3, 65, 3);
    private static final BlockPos REJECTED = new BlockPos(4, 65, 4);
    private static final BlockPos DIRECT = new BlockPos(6, 65, 6);
    private static final BlockPos UNTOUCHED = new BlockPos(5, 65, 5);

    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void unguardedFeaturesInvalidateSuccessfulWritesAcrossChunkBoundaries() {
        Fixture fixture = new Fixture(false, false);
        fixture.run();

        assertEquals(Set.of(REJECTED, UNTOUCHED), fixture.saved);
        assertTrue(fixture.changes.contains(17, 66, 2));
        assertFalse(fixture.changes.contains(4, 65, 4));
        verify(fixture.level).getBlockState(new BlockPos(64, 65, 2));
    }

    @Test
    public void guardedFeaturesPreserveProtectedAndRejectedRecords() {
        Fixture fixture = new Fixture(true, false);
        fixture.run();

        assertEquals(Set.of(PROTECTED, REJECTED, UNTOUCHED), fixture.saved);
        assertEquals(3, fixture.changes.size());
    }

    @Test
    public void failedFeaturesStillInvalidateWritesCompletedBeforeFailure() {
        Fixture fixture = new Fixture(false, true);
        IllegalStateException failure = assertThrows(IllegalStateException.class, fixture::run);

        assertSame(fixture.failure, failure.getCause());
        assertEquals(Set.of(REJECTED, UNTOUCHED), fixture.saved);
        verify(fixture.level).setCurrentlyGenerating(null);
    }

    @Test
    public void directSectionWritesInvalidateOnlyChangedSavedPositions() {
        Fixture fixture = new Fixture(false, false);
        fixture.run();

        assertTrue(fixture.changes.contains(DIRECT.getX(), DIRECT.getY(), DIRECT.getZ()));
        assertFalse(fixture.changes.contains(UNTOUCHED.getX(), UNTOUCHED.getY(), UNTOUCHED.getZ()));
        assertFalse(fixture.saved.contains(DIRECT));
    }

    @Test
    public void absentSavedPositionsLeaveFeatureWorldUnwrapped() {
        Fixture fixture = new Fixture(false, false);
        when(fixture.policy.trackedBlockPositions(any())).thenReturn(NativeBlockChangeSet.disabled());
        fixture.run();

        assertSame(fixture.level, fixture.placementWorld);
        assertSame(NativeBlockChangeSet.disabled(), fixture.changes);
    }

    @Test
    public void invalidationFailureStillClearsGenerationMarker() {
        Fixture fixture = new Fixture(false, false);
        doThrow(fixture.failure).when(fixture.policy).invalidateBlockChanges(any());

        assertSame(fixture.failure, assertThrows(IllegalStateException.class, fixture::run));
        verify(fixture.level).setCurrentlyGenerating(null);
    }

    @Test
    public void carrierPropertyChangesPreserveSavedPositions() {
        Fixture fixture = new Fixture(false, false);
        fixture.run();

        assertTrue(fixture.saved.contains(UNTOUCHED));
        assertTrue(fixture.blocks.get(UNTOUCHED).getValue(FenceBlock.EAST));
        assertFalse(fixture.changes.contains(UNTOUCHED.getX(), UNTOUCHED.getY(), UNTOUCHED.getZ()));
    }

    @Test
    public void failedSnapshotReadStillInvalidatesObservedWrites() {
        Fixture fixture = new Fixture(false, false);
        fixture.failSnapshotRead = true;

        assertSame(fixture.failure, assertThrows(IllegalStateException.class, fixture::run));
        assertFalse(fixture.saved.contains(REPLACED));
        assertFalse(fixture.saved.contains(REMOVED));
        assertTrue(fixture.saved.contains(DIRECT));
        verify(fixture.level).setCurrentlyGenerating(null);
    }

    @Test
    public void invalidationFailureDoesNotHideFeatureFailure() {
        Fixture fixture = new Fixture(false, true);
        IllegalStateException invalidationFailure = new IllegalStateException("invalidation failed");
        doThrow(invalidationFailure).when(fixture.policy).invalidateBlockChanges(any());

        IllegalStateException failure = assertThrows(IllegalStateException.class, fixture::run);

        assertSame(fixture.failure, failure.getCause());
        assertEquals(1, failure.getSuppressed().length);
        assertSame(invalidationFailure, failure.getSuppressed()[0]);
        verify(fixture.level).setCurrentlyGenerating(null);
    }

    @Test
    public void aThrowingFeatureIsSkippedAndTheNextFeatureStillPlaces() {
        Fixture fixture = new Fixture(false, true);
        List<String> skipped = new ArrayList<>();
        NativePlacementFailures.register((failure, kind, id, x, z) -> skipped.add(kind));
        try {
            fixture.run();
        } finally {
            NativePlacementFailures.register(null);
        }

        assertEquals(List.of("imported feature"), skipped);
        assertTrue(fixture.nextFeaturePlaced);
        assertEquals(Set.of(REJECTED, UNTOUCHED), fixture.saved);
        verify(fixture.level).setCurrentlyGenerating(null);
    }

    @Test
    public void anEngineFailureThroughTheHandlerStopsTheChunk() {
        Fixture fixture = new Fixture(false, true);
        NativePlacementFailures.register((failure, kind, id, x, z) -> {
            throw (IllegalStateException) failure;
        });
        try {
            IllegalStateException failure = assertThrows(IllegalStateException.class, fixture::run);
            assertSame(fixture.failure, failure.getCause());
        } finally {
            NativePlacementFailures.register(null);
        }

        assertFalse(fixture.nextFeaturePlaced);
        assertEquals(Set.of(REJECTED, UNTOUCHED), fixture.saved);
    }

    private static final class Fixture {
        private final WorldGenLevel level = mock(WorldGenLevel.class);
        private final ChunkAccess chunk = mock(ChunkAccess.class);
        private final ChunkGenerator generator = mock(ChunkGenerator.class);
        private final Set<BlockPos> saved = new HashSet<>(Set.of(REPLACED, REMOVED, PROTECTED, REJECTED, UNTOUCHED, DIRECT));
        private final IllegalStateException failure = new IllegalStateException("feature failed after writing");
        private NativeBlockChangeSet changes;
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();
        private final NativeBlockChangeSet trackedPositions = NativeBlockChangeSet.create(
                new NativeBlockChangeSet.Bounds(-1, -1, 1, 1, -64, 384));
        private final NativeImportedFeatureStage<Object> stage;
        private final NativeImportedFeaturePolicy<Object> policy;
        private WorldGenLevel placementWorld;
        private boolean failSnapshotRead;
        private boolean snapshotReadsFail;
        private boolean nextFeaturePlaced;

        @SuppressWarnings("unchecked")
        private Fixture(boolean guarded, boolean failAfterWrite) {
            policy = mock(NativeImportedFeaturePolicy.class);
            NativeImportedFeatureControl control = mock(NativeImportedFeatureControl.class);
            RegistryAccess registries = mock(RegistryAccess.class);
            Registry<Biome> biomeRegistry = mock(Registry.class);
            Registry<PlacedFeature> featureRegistry = mock(Registry.class);
            Biome biome = mock(Biome.class);
            Holder.Reference<Biome> biomeHolder = mock(Holder.Reference.class);
            LevelChunkSection section = mock(LevelChunkSection.class);
            PalettedContainerRO<Holder<Biome>> biomes = mock(PalettedContainerRO.class);
            blocks.put(UNTOUCHED, Blocks.OAK_FENCE.defaultBlockState());
            for (BlockPos position : saved) {
                trackedPositions.add(position.getX(), position.getY(), position.getZ());
            }
            PlacedFeature feature = feature(world -> {
                placementWorld = world;
                world.getBlockState(new BlockPos(64, 65, 2));
                world.setBlock(REPLACED, Blocks.STONE.defaultBlockState(), 2);
                world.removeBlock(REMOVED, false);
                world.setBlock(PROTECTED, Blocks.STONE.defaultBlockState(), 2);
                world.setBlock(REJECTED, Blocks.STONE.defaultBlockState(), 2);
                world.getChunk(0, 0).getSections()[8].setBlockState(6, 1, 6, Blocks.STONE.defaultBlockState(), false);
                world.getChunk(0, 0).getSections()[8].setBlockState(5, 1, 5,
                        Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.EAST, true), false);
                snapshotReadsFail = failSnapshotRead;
                if (failAfterWrite) {
                    throw failure;
                }
            });
            when(policy.dimension()).thenReturn(new Object());
            when(policy.control()).thenReturn(control);
            when(policy.visibleBiomeKeys()).thenReturn(Set.of("minecraft:plains"));
            when(policy.customBiomeDerivatives()).thenReturn(List.of());
            when(policy.placement()).thenReturn(new NativeImportedFeaturePolicy.Placement(
                    guarded, false, null, null,
                    (x, y, z) -> PROTECTED.equals(new BlockPos(x, y, z)), null));
            when(policy.trackedBlockPositions(any())).thenReturn(trackedPositions);
            doAnswer(invocation -> {
                NativeBlockChangeSet written = invocation.getArgument(0);
                changes = written;
                written.forEachChunk((x, z) -> written.forEachPosition(x, z,
                        (blockX, blockY, blockZ) -> saved.remove(new BlockPos(blockX, blockY, blockZ))));
                return null;
            }).when(policy).invalidateBlockChanges(any());
            when(control.shouldGenerateFeatures()).thenReturn(true);
            when(control.shouldGenerateStepOrdinal(anyInt())).thenReturn(true);
            when(level.registryAccess()).thenReturn(registries);
            when(registries.lookupOrThrow(Registries.BIOME)).thenReturn(biomeRegistry);
            when(registries.lookupOrThrow(Registries.PLACED_FEATURE)).thenReturn(featureRegistry);
            when(biomeRegistry.listElements()).thenAnswer(invocation -> Stream.of(biomeHolder));
            when(biomeHolder.unwrapKey()).thenReturn(Optional.of(
                    ResourceKey.create(Registries.BIOME, Identifier.parse("minecraft:plains"))));
            when(biomeHolder.value()).thenReturn(biome);
            PlacedFeature nextFeature = feature(world -> nextFeaturePlaced = true);
            when(biome.getGenerationSettings()).thenReturn(new BiomeGenerationSettings.PlainBuilder()
                    .addFeature(0, Holder.direct(feature))
                    .addFeature(0, Holder.direct(nextFeature)).build());
            when(level.getMinY()).thenReturn(-64);
            when(level.getMinSectionY()).thenReturn(-4);
            when(level.getHeight()).thenReturn(384);
            when(level.getBiome(any())).thenReturn(biomeHolder);
            when(level.getBiomeManager()).thenReturn(new BiomeManager((x, y, z) -> biomeHolder, 7L));
            when(level.getBlockState(any())).thenAnswer(invocation -> {
                if (snapshotReadsFail) {
                    throw failure;
                }
                return blocks.getOrDefault(invocation.getArgument(0), Blocks.AIR.defaultBlockState());
            });
            when(section.setBlockState(anyInt(), anyInt(), anyInt(), any(), anyBoolean()))
                    .thenAnswer(invocation -> blocks.put(new BlockPos(
                            invocation.<Integer>getArgument(0), 64 + invocation.<Integer>getArgument(1),
                            invocation.<Integer>getArgument(2)), invocation.getArgument(3)));
            when(level.setBlock(any(), any(), anyInt(), anyInt()))
                    .thenAnswer(invocation -> !REJECTED.equals(invocation.getArgument(0)));
            when(level.removeBlock(any(), anyBoolean())).thenReturn(true);
            when(chunk.getPos()).thenReturn(new ChunkPos(0, 0));
            when(level.getChunk(anyInt(), anyInt())).thenReturn(chunk);
            when(chunk.getSections()).thenReturn(new LevelChunkSection[]{section, section, section, section, section, section, section, section, section});
            when(section.getBiomes()).thenReturn(biomes);
            doAnswer(invocation -> {
                Consumer<Holder<Biome>> consumer = invocation.getArgument(0);
                consumer.accept(biomeHolder);
                return null;
            }).when(biomes).getAll(any());
            stage = new NativeImportedFeatureStage<>(policy);
            stage.prepare(level);
        }

        private void run() {
            stage.run(level, chunk, generator);
        }
    }

    @SuppressWarnings("unchecked")
    private static PlacedFeature feature(Consumer<WorldGenLevel> placement) {
        Feature feature = mock(Feature.class);
        when(feature.place(any(), any(), any(), any())).thenAnswer(invocation -> {
            placement.accept(invocation.getArgument(0));
            return true;
        });
        return new PlacedFeature(Holder.direct(feature), List.of());
    }
}

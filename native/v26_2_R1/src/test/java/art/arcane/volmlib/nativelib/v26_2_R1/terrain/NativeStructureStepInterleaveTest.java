package art.arcane.volmlib.nativelib.v26_2_R1.terrain;

import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureOwnershipRecovery;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructurePostProcessor;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureSurfaceFitter;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureVegetationClearer;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.WorldgenTerrainHeightmaps;
import art.arcane.volmlib.nativelib.terrain.NativeBlockChangeSet;
import art.arcane.volmlib.nativelib.terrain.structure.StructureCachePolicy;
import art.arcane.volmlib.nativelib.terrain.structure.StructureOwnershipRecordView;
import art.arcane.volmlib.nativelib.terrain.structure.StructurePlacementDecision;
import art.arcane.volmlib.nativelib.terrain.structure.StructureReferencePolicy;
import art.arcane.volmlib.nativelib.terrain.structure.StructureSeaSurface;
import art.arcane.volmlib.nativelib.terrain.structure.StructureStagePolicy;
import art.arcane.volmlib.nativelib.terrain.structure.StructureStartPlan;
import art.arcane.volmlib.nativelib.terrain.structure.StructureStrata;
import art.arcane.volmlib.nativelib.terrain.structure.StructureWater;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class NativeStructureStepInterleaveTest {
    private static final String MINESHAFT = "minecraft:mineshaft";
    private static final String VILLAGE = "minecraft:village_plains";

    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void eachStepsStructuresPlaceBeforeThatStepCompletes() {
        Fixture fixture = new Fixture();

        List<String> events = fixture.placeRecordingSteps();

        assertEquals(List.of(
                "step 0", "step 1", "step 2",
                MINESHAFT, "step 3",
                VILLAGE, "step 4",
                "step 5", "step 6", "step 7", "step 8", "step 9", "step 10"), events);
    }

    @Test
    public void disabledStructureGenerationSkipsTheStepCallbacks() {
        Fixture fixture = new Fixture();
        when(fixture.manager.shouldGenerateStructures()).thenReturn(false);

        assertEquals(List.of(), fixture.placeRecordingSteps());
    }

    private static final class Fixture {
        private final WorldGenLevel world = mock(WorldGenLevel.class);
        private final ChunkAccess chunk = mock(ChunkAccess.class);
        private final StructureManager manager = mock(StructureManager.class);
        private final NativeBukkitStructureStage<Object, Object, StructureStartPlan, Ownership> stage;

        @SuppressWarnings("unchecked")
        private Fixture() {
            StructureStagePolicy<Object, StructureStartPlan, Ownership> policy = mock(StructureStagePolicy.class);
            StructureReferencePolicy<StructureStartPlan, Ownership> structures = mock(StructureReferencePolicy.class);
            StructureCachePolicy<Object> cache = mock(StructureCachePolicy.class);
            RegistryAccess registries = mock(RegistryAccess.class);
            Registry<Structure> registry = mock(Registry.class);
            Structure village = mock(Structure.class);
            Structure mineshaft = mock(Structure.class);
            StructureStart villageStart = start(new ChunkPos(0, 0));
            StructureStart mineshaftStart = start(new ChunkPos(0, 0));
            StructurePlacementDecision decision = mock(StructurePlacementDecision.class);
            Holder<Biome> biome = Holder.direct(mock(Biome.class));
            NativeBlockChangeSet changes = NativeBlockChangeSet.create(
                    new NativeBlockChangeSet.Bounds(-1, -1, 1, 1, -64, 384));
            when(world.registryAccess()).thenReturn(registries);
            when(registries.lookupOrThrow(Registries.STRUCTURE)).thenReturn(registry);
            when(registry.iterator()).thenAnswer(invocation -> List.of(village, mineshaft).iterator());
            when(registry.getKey(village)).thenReturn(Identifier.parse(VILLAGE));
            when(registry.getKey(mineshaft)).thenReturn(Identifier.parse(MINESHAFT));
            when(village.step()).thenReturn(GenerationStep.Decoration.SURFACE_STRUCTURES);
            when(mineshaft.step()).thenReturn(GenerationStep.Decoration.UNDERGROUND_STRUCTURES);
            when(manager.shouldGenerateStructures()).thenReturn(true);
            when(manager.startsForStructure(any(SectionPos.class), eq(village))).thenReturn(List.of(villageStart));
            when(manager.startsForStructure(any(SectionPos.class), eq(mineshaft))).thenReturn(List.of(mineshaftStart));
            when(policy.allowsFootprint(any(), any())).thenReturn(true);
            when(policy.strata(any())).thenReturn(mock(StructureStrata.class));
            when(policy.water(any())).thenReturn(mock(StructureWater.class));
            when(policy.seaSurface(any())).thenReturn(StructureSeaSurface.NONE);
            when(policy.structurePolicy(any())).thenReturn(structures);
            when(structures.resolve(anyString(), anyBoolean())).thenReturn(decision);
            when(decision.generate()).thenReturn(true);
            when(policy.protectedPositions(any())).thenReturn((x, y, z) -> false);
            when(policy.worldgenHeight(any(), anyInt(), anyBoolean())).thenReturn((x, z) -> 64);
            when(policy.trackBlockChanges(any(), any())).thenReturn(changes);
            when(chunk.getPos()).thenReturn(new ChunkPos(0, 0));
            when(chunk.getMinY()).thenReturn(-64);
            when(chunk.getHeight()).thenReturn(384);
            when(world.getMinY()).thenReturn(-64);
            when(world.getHeight()).thenReturn(384);
            when(world.getBiome(any())).thenReturn(biome);
            when(world.getBiomeManager()).thenReturn(new BiomeManager((x, y, z) -> biome, 7L));
            stage = new NativeBukkitStructureStage<>(new NativeBukkitStructureStage.Configuration<>(
                    null, null, -64, policy, cache, null));
        }

        private static StructureStart start(ChunkPos chunkPos) {
            StructureStart start = mock(StructureStart.class);
            when(start.getChunkPos()).thenReturn(chunkPos);
            when(start.getBoundingBox()).thenReturn(new BoundingBox(0, 60, 0, 15, 80, 15));
            return start;
        }

        private List<String> placeRecordingSteps() {
            List<String> events = new ArrayList<>();
            try (MockedStatic<WorldgenTerrainHeightmaps> heights = mockStatic(WorldgenTerrainHeightmaps.class);
                 MockedStatic<NativeStructureOwnershipRecovery> ownership = mockStatic(NativeStructureOwnershipRecovery.class);
                 MockedStatic<NativeStructureVegetationClearer> vegetation = mockStatic(NativeStructureVegetationClearer.class);
                 MockedStatic<NativeStructureSurfaceFitter> surface = mockStatic(NativeStructureSurfaceFitter.class);
                 MockedStatic<NativeStructurePostProcessor> post = mockStatic(NativeStructurePostProcessor.class)) {
                surface.when(() -> NativeStructureSurfaceFitter.prepareSurfaceStructures(any(), any(), any(), any(), any(), any(), any()))
                        .thenReturn(new NativeStructureSurfaceFitter.SurfaceTerrainPlan(List.of(), Set.of(), Map.of(), Map.of()));
                post.when(() -> NativeStructurePostProcessor.place(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                        .thenAnswer(invocation -> {
                            events.add(invocation.getArgument(6));
                            return null;
                        });
                stage.placeVanillaStructures(world, chunk, manager, step -> events.add("step " + step));
            }
            return events;
        }
    }

    private interface Ownership extends StructureOwnershipRecordView<Ownership> {
    }
}

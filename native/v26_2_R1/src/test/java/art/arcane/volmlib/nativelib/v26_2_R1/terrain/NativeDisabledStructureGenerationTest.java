package art.arcane.volmlib.nativelib.v26_2_R1.terrain;

import art.arcane.volmlib.nativelib.terrain.structure.StructureOwnershipRecordView;
import art.arcane.volmlib.nativelib.terrain.structure.StructureStartPlan;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class NativeDisabledStructureGenerationTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void disabledPlacementSkipsWorldAndChunkAccess() {
        StructureManager manager = mock(StructureManager.class);
        WorldGenLevel world = mock(WorldGenLevel.class);
        ChunkAccess chunk = mock(ChunkAccess.class);
        NativeBukkitStructureStage<Object, Object, StructureStartPlan, Ownership> stage =
                new NativeBukkitStructureStage<>(new NativeBukkitStructureStage.Configuration<Object, Object, StructureStartPlan, Ownership>(
                        null, null, 0, null, null, null));

        stage.placeVanillaStructures(world, chunk, manager);

        verifyNoInteractions(world, chunk);
    }

    @Test
    public void disabledStartsAndReferencesSkipRuntimeAndChunkAccess() {
        NativeChunkGenerator<?, ?, ?, ?, ?> generator = mock(NativeChunkGenerator.class, CALLS_REAL_METHODS);
        StructureManager manager = mock(StructureManager.class);
        WorldGenLevel world = mock(WorldGenLevel.class);
        ChunkAccess chunk = mock(ChunkAccess.class);

        generator.createStructures(null, null, manager, chunk, null, null);
        generator.createReferences(world, manager, chunk);

        verifyNoInteractions(world, chunk);
    }

    @Test
    public void disabledLocateDoesNotReadGeneratorRuntime() {
        NativeChunkGenerator<?, ?, ?, ?, ?> generator = mock(NativeChunkGenerator.class, CALLS_REAL_METHODS);
        StructureManager manager = mock(StructureManager.class);
        ServerLevel level = mock(ServerLevel.class);
        when(level.structureManager()).thenReturn(manager);

        assertNull(generator.findNearestMapStructure(level, null, null, 100, false));
    }

    private interface Ownership extends StructureOwnershipRecordView<Ownership> {
    }
}

package art.arcane.volmlib.nativelib.v26_2_R1.terrain;

import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureTerrainIntegrator;

import art.arcane.volmlib.nativelib.terrain.structure.StructureStagePolicy;
import art.arcane.volmlib.nativelib.terrain.structure.StructureTerrainSettings;
import art.arcane.volmlib.nativelib.terrain.structure.StructureWater;
import art.arcane.volmlib.nativelib.terrain.structure.StructureWaterColumn;
import art.arcane.volmlib.util.structure.StructureCarveShape;
import art.arcane.volmlib.util.structure.StructureTerrainMode;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.ShipwreckStructure;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NativeStructureTerrainWaterTest {
    private static final int RIVER_FLOOR = 60;
    private static final int RIVER_SURFACE = 64;
    private static final BoundingBox AREA = new BoundingBox(-16, -64, -16, 31, 319, 31);
    private static final StructureWater RIVER = (x, z) -> x == 3 || x == 4
            ? new StructureWaterColumn(true, true, RIVER_FLOOR, RIVER_SURFACE) : StructureWaterColumn.DRY;

    @BeforeClass
    public static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void boreSkipsRiverColumnsAndTheirBanks() {
        Map<Long, BlockState> blocks = ground();

        NativeStructureTerrainIntegrator.integrateTerrain(world(blocks), AREA, "minecraft:shipwreck", start(),
                terrain(StructureTerrainMode.BORE, StructureCarveShape.BOX), (palette, rng, x, y, z) -> null, RIVER);

        assertRiverKept(blocks);
    }

    @Test
    public void forceCarveSkipsRiverColumnsAndTheirBanks() {
        for (StructureCarveShape shape : List.of(StructureCarveShape.BOX, StructureCarveShape.ROUNDED)) {
            Map<Long, BlockState> blocks = ground();

            NativeStructureTerrainIntegrator.integrateTerrain(world(blocks), AREA, "minecraft:shipwreck", start(),
                    terrain(StructureTerrainMode.FORCE_CARVE, shape), (palette, rng, x, y, z) -> null, RIVER);

            assertRiverKept(blocks);
        }
    }

    @Test
    public void pieceFootprintsAreThePieceColumns() {
        assertEquals(List.of(new StructureStagePolicy.Footprint(0, 0, 8, 2)),
                NativeStructureTerrainIntegrator.pieceFootprints(start()));
    }

    private static void assertRiverKept(Map<Long, BlockState> blocks) {
        assertTrue(state(blocks, 3, 62, 1).isSolid());
        assertTrue(state(blocks, 4, 68, 1).isSolid());
        assertTrue(state(blocks, 1, 58, 1).isSolid());
        assertTrue(state(blocks, 6, 64, 1).isSolid());
        assertTrue(state(blocks, 3, 55, 1).isAir());
        assertTrue(state(blocks, 1, 66, 1).isAir());
        assertTrue(state(blocks, 0, 62, 1).isAir());
        assertTrue(state(blocks, 7, 62, 1).isAir());
    }

    private static Map<Long, BlockState> ground() {
        Map<Long, BlockState> blocks = new HashMap<>();
        for (int x = -4; x <= 12; x++) {
            for (int z = -4; z <= 6; z++) {
                for (int y = 40; y <= 80; y++) {
                    blocks.put(BlockPos.asLong(x, y, z), Blocks.STONE.defaultBlockState());
                }
            }
        }
        return blocks;
    }

    private static StructureStart start() {
        Structure structure = new ShipwreckStructure(new Structure.StructureSettings(
                HolderSet.empty(), Map.of(), GenerationStep.Decoration.SURFACE_STRUCTURES, TerrainAdjustment.NONE), false);
        StructurePiece piece = mock(StructurePiece.class);
        when(piece.getBoundingBox()).thenReturn(new BoundingBox(0, 60, 0, 8, 64, 2));
        return new StructureStart(structure, new ChunkPos(0, 0), 0, new PiecesContainer(List.of(piece)));
    }

    private static StructureTerrainSettings terrain(StructureTerrainMode mode, StructureCarveShape shape) {
        StructureTerrainSettings terrain = mock(StructureTerrainSettings.class);
        when(terrain.resolvedMode()).thenReturn(mode);
        when(terrain.resolvedShape()).thenReturn(shape);
        when(terrain.getFloorPadding()).thenReturn(10);
        when(terrain.getCeilingPadding()).thenReturn(4);
        return terrain;
    }

    private static BlockState state(Map<Long, BlockState> blocks, int x, int y, int z) {
        return blocks.getOrDefault(BlockPos.asLong(x, y, z), Blocks.AIR.defaultBlockState());
    }

    private static WorldGenLevel world(Map<Long, BlockState> blocks) {
        WorldGenLevel world = mock(WorldGenLevel.class);
        when(world.getBlockState(any(BlockPos.class))).thenAnswer(invocation -> blocks.getOrDefault(
                invocation.<BlockPos>getArgument(0).asLong(), Blocks.AIR.defaultBlockState()));
        when(world.setBlock(any(BlockPos.class), any(BlockState.class), anyInt())).thenAnswer(invocation -> {
            blocks.put(invocation.<BlockPos>getArgument(0).asLong(), invocation.getArgument(1));
            return true;
        });
        return world;
    }
}

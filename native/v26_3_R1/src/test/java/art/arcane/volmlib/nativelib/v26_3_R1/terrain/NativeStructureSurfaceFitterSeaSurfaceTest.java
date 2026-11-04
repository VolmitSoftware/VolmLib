package art.arcane.volmlib.nativelib.v26_3_R1.terrain;

import art.arcane.volmlib.nativelib.terrain.structure.StructureTerrainSettings;
import art.arcane.volmlib.nativelib.terrain.structure.StructureWater;
import art.arcane.volmlib.util.structure.StructureTerrainMode;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.ShipwreckStructure;
import net.minecraft.world.ticks.ProtoChunkTicks;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NativeStructureSurfaceFitterSeaSurfaceTest {
    private static final int SEA_TOP_Y = 62;
    private static final int SEABED_Y = 46;
    private static final int SHORE_Y = 66;
    private static final int WRECK_MIN_X = -4;
    private static final int WRECK_MAX_X = 4;
    private static final int SHORELINE_X = -2;
    private static final BoundingBox COAST = new BoundingBox(-40, 0, 0, 10, 127, 0);

    @BeforeClass
    public static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void cutBelowTheSeaSurfaceHoldsSeaWater() {
        Map<Long, BlockState> blocks = new HashMap<>();
        for (int y = 0; y <= 66; y++) {
            blocks.put(BlockPos.asLong(0, y, 0), Blocks.STONE.defaultBlockState());
        }

        NativeStructureSurfaceFitter.applySurfaceColumn(world(blocks), new BlockPos.MutableBlockPos(),
                0, 0, 66, 58, 0, 127, false, sea(), PlainSurfaceStrata.STRATA);

        for (int y = 59; y <= 62; y++) {
            assertEquals("Sea water at y " + y, Blocks.WATER.defaultBlockState(), state(blocks, 0, y));
        }
        for (int y = 63; y <= 66; y++) {
            assertTrue("Air above the sea surface at y " + y, state(blocks, 0, y).isAir());
        }
        assertTrue(state(blocks, 0, 58).isSolid());
    }

    @Test
    public void flattenNeverLowersDryShoreBelowTheSeaSurface() {
        Map<Long, BlockState> blocks = coast();

        fitCoast(blocks);

        for (int x = COAST.minX(); x <= COAST.maxX(); x++) {
            if (surfaceHeight(x, 0) < SEA_TOP_Y) {
                continue;
            }
            for (int y = COAST.minY(); y <= SEA_TOP_Y; y++) {
                assertFalse("Air below the sea surface at " + x + "," + y, state(blocks, x, y).isAir());
            }
        }
        for (int x = COAST.minX(); x < WRECK_MIN_X; x++) {
            assertTrue("Dry shore below the sea surface at " + x, state(blocks, x, SEA_TOP_Y).isSolid());
        }
        assertEquals(Blocks.WATER.defaultBlockState(), state(blocks, WRECK_MIN_X, SEA_TOP_Y));
        assertTrue(state(blocks, WRECK_MIN_X, SEA_TOP_Y + 1).isAir());
        assertTrue(state(blocks, WRECK_MIN_X - 1, SEA_TOP_Y + 1).isAir());
    }

    @Test
    public void cutBelowTheSeaSurfaceKeepsTheColumnFluid() {
        Map<Long, BlockState> blocks = new HashMap<>();
        for (int y = 0; y <= 60; y++) {
            blocks.put(BlockPos.asLong(0, y, 0), Blocks.STONE.defaultBlockState());
        }
        for (int y = 61; y <= 70; y++) {
            blocks.put(BlockPos.asLong(0, y, 0), Blocks.WATER.defaultBlockState());
        }
        NativeStructureSurfaceFitter.SeaSurface lavaSea =
                new NativeStructureSurfaceFitter.SeaSurface(SEA_TOP_Y, Blocks.LAVA.defaultBlockState());

        NativeStructureSurfaceFitter.applySurfaceColumn(world(blocks), new BlockPos.MutableBlockPos(),
                0, 0, 60, 56, 0, 127, false, lavaSea, PlainSurfaceStrata.STRATA);

        for (int y = 57; y <= 70; y++) {
            assertEquals("Column water at y " + y, Blocks.WATER.defaultBlockState(), state(blocks, 0, y));
        }
        assertTrue(state(blocks, 0, 56).isSolid());
    }

    @Test
    public void seaSurfaceRequiresAFluidBlock() {
        assertThrows(IllegalStateException.class,
                () -> new NativeStructureSurfaceFitter.SeaSurface(SEA_TOP_Y, Blocks.STONE.defaultBlockState()));
        assertThrows(IllegalStateException.class,
                () -> new NativeStructureSurfaceFitter.SeaSurface(SEA_TOP_Y, Blocks.AIR.defaultBlockState()));
        assertEquals(Integer.MIN_VALUE, NativeStructureSurfaceFitter.SeaSurface.NONE.topY());
    }

    @Test
    public void floodedFlattenCutPrimesTheWorldSurfaceAboveTheSeaFluid() {
        NativeStructureSurfaceFitter.SurfaceTerrainPlan plan = fitCoast(coast());
        ProtoChunk chunk = chunk(new ChunkPos(-1, 0));

        plan.primeHeightmaps(chunk);

        assertEquals(SEA_TOP_Y, chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, WRECK_MIN_X, 0));
        assertEquals(SEABED_Y, chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, WRECK_MIN_X, 0));
        assertEquals(SEA_TOP_Y, chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, WRECK_MIN_X - 6, 0));
        assertEquals(SEA_TOP_Y, chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, WRECK_MIN_X - 6, 0));
    }

    private static NativeStructureSurfaceFitter.SurfaceTerrainPlan fitCoast(Map<Long, BlockState> blocks) {
        return NativeStructureSurfaceFitter.prepareSurfaceStructures(world(blocks), COAST,
                List.of(beachedWreck()), NativeStructureSurfaceFitterSeaSurfaceTest::surfaceHeight, sea(), StructureWater.DRY, PlainSurfaceStrata.STRATA);
    }

    private static NativeStructureSurfaceFitter.SeaSurface sea() {
        return new NativeStructureSurfaceFitter.SeaSurface(SEA_TOP_Y, Blocks.WATER.defaultBlockState());
    }

    private static int surfaceHeight(int x, int z) {
        return x < SHORELINE_X ? SHORE_Y : SEABED_Y;
    }

    private static Map<Long, BlockState> coast() {
        Map<Long, BlockState> blocks = new HashMap<>();
        for (int x = COAST.minX(); x <= COAST.maxX(); x++) {
            int surfaceY = surfaceHeight(x, 0);
            for (int y = COAST.minY(); y <= surfaceY; y++) {
                blocks.put(BlockPos.asLong(x, y, 0), Blocks.STONE.defaultBlockState());
            }
            for (int y = surfaceY + 1; y <= SEA_TOP_Y; y++) {
                blocks.put(BlockPos.asLong(x, y, 0), Blocks.WATER.defaultBlockState());
            }
        }
        return blocks;
    }

    private static NativeStructureTerrainIntegrator.TerrainTarget beachedWreck() {
        Structure structure = new ShipwreckStructure(new Structure.StructureSettings(
                HolderSet.empty(), Map.of(), GenerationStep.Decoration.SURFACE_STRUCTURES, TerrainAdjustment.NONE), true);
        StructurePiece piece = mock(StructurePiece.class);
        when(piece.getBoundingBox()).thenReturn(new BoundingBox(WRECK_MIN_X, 47, 0, WRECK_MAX_X, 55, 0));
        StructureStart start = new StructureStart(structure, new ChunkPos(0, 0), 0,
                new PiecesContainer(List.of(piece)));
        StructureTerrainSettings terrain = mock(StructureTerrainSettings.class);
        when(terrain.resolvedMode()).thenReturn(StructureTerrainMode.FLATTEN);
        when(terrain.resolvedFlattenRange()).thenReturn(64);
        when(terrain.getHorizontalPadding()).thenReturn(24);
        return new NativeStructureTerrainIntegrator.TerrainTarget("minecraft:shipwreck_beached", start, terrain);
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

    private static BlockState state(Map<Long, BlockState> blocks, int x, int y) {
        return blocks.getOrDefault(BlockPos.asLong(x, y, 0), Blocks.AIR.defaultBlockState());
    }

    @SuppressWarnings("unchecked")
    private static ProtoChunk chunk(ChunkPos position) {
        LevelChunkSection[] sections = new LevelChunkSection[8];
        for (int index = 0; index < sections.length; index++) {
            PalettedContainer<BlockState> states = new PalettedContainer<>(
                    Blocks.AIR.defaultBlockState(), Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY), null);
            sections[index] = new LevelChunkSection(states,
                    (PalettedContainer<Holder<Biome>>) mock(PalettedContainer.class));
        }
        return new ProtoChunk(position, UpgradeData.EMPTY, sections, new ProtoChunkTicks<>(),
                new ProtoChunkTicks<>(), LevelHeightAccessor.create(0, 128),
                mock(PalettedContainerFactory.class), (BlendingData) null);
    }
}

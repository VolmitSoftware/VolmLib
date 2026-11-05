package art.arcane.volmlib.nativelib.v26_3_R1.terrain;

import art.arcane.volmlib.nativelib.terrain.structure.StructureTerrainSettings;
import art.arcane.volmlib.nativelib.terrain.structure.StructureWater;
import art.arcane.volmlib.nativelib.terrain.structure.StructureWaterColumn;
import art.arcane.volmlib.util.structure.StructureTerrainMode;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidStructure;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NativeStructureSurfaceFitterStreamTest {
    private static final int MIN_Y = -40;
    private static final int MAX_Y = 125;
    private static final int FIRST_X = -100;
    private static final int LAST_X = 200;
    private static final int FLATTEN_STREAM_Z = 21;
    private static final int SOURCE_STREAM_Z = 18;
    private static final int PIECE_MIN_Y = 81;

    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void flattenLowersTheStreamWithItsBanksAndHoldsTheLevelDownstream() {
        Site site = new Site(FLATTEN_STREAM_Z, 2, Integer.MAX_VALUE);

        Map<Long, BlockState> blocks = fit(site, target(StructureTerrainMode.FLATTEN, TerrainAdjustment.NONE), NativeStructureSurfaceFitter.SeaSurface.NONE, false);

        assertStreamRules(blocks, site);
        assertTrue("the stream goes down with the cut", sourceTop(blocks, 7, site.streamZ) <= site.surface(7) - 6);
        assertEquals("the bed material moves with it", Blocks.SAND.defaultBlockState(),
                state(blocks, 7, waterBottom(blocks, 7, site.streamZ) - 1, site.streamZ));
        assertTrue("a channel carries the level past the bowl", sourceTop(blocks, 30, site.streamZ) < site.surface(30));
        for (int x = 8; x <= LAST_X; x++) {
            assertTrue("no falling water hangs above the lowered stream at " + x,
                    top(blocks, x, site.streamZ) <= Math.max(site.surface(x), waterTop(blocks, x - 1, site.streamZ)));
        }
        assertEquals("the stream's own head takes over again", site.surface(LAST_X), sourceTop(blocks, LAST_X, site.streamZ));
    }

    @Test
    public void sourceFittingLowersTheStreamWithItsBanks() {
        Site site = new Site(SOURCE_STREAM_Z, 1, Integer.MAX_VALUE);

        Map<Long, BlockState> blocks = fit(site, target(StructureTerrainMode.SOURCE, TerrainAdjustment.BEARD_THIN), NativeStructureSurfaceFitter.SeaSurface.NONE, false);

        assertStreamRules(blocks, site);
        assertTrue("the stream goes down with the cut", sourceTop(blocks, 7, site.streamZ) < site.surface(7));
    }

    @Test
    public void streamBlocksDoNotDependOnChunkOrder() {
        Site site = new Site(FLATTEN_STREAM_Z, 2, Integer.MAX_VALUE);
        NativeStructureTerrainIntegrator.TerrainTarget target = target(StructureTerrainMode.FLATTEN, TerrainAdjustment.NONE);

        Map<Long, BlockState> eastward = fit(site, target, NativeStructureSurfaceFitter.SeaSurface.NONE, false);
        Map<Long, BlockState> westward = fit(site, target(StructureTerrainMode.FLATTEN, TerrainAdjustment.NONE), NativeStructureSurfaceFitter.SeaSurface.NONE, true);

        assertEquals(eastward, westward);
    }

    @Test
    public void aStreamJoinedToTheSeaStopsAboveItAndKeepsBanks() {
        int seaTop = 82;
        Site site = new Site(FLATTEN_STREAM_Z, 2, 30);
        NativeStructureSurfaceFitter.SeaSurface sea =
                new NativeStructureSurfaceFitter.SeaSurface(seaTop, Blocks.WATER.defaultBlockState());

        Map<Long, BlockState> blocks = fit(site, target(StructureTerrainMode.FLATTEN, TerrainAdjustment.NONE), sea, false);

        assertStreamRules(blocks, site);
        for (int x = FIRST_X; x <= LAST_X; x++) {
            int source = sourceTop(blocks, x, site.streamZ);
            if (x >= site.mouthX) {
                assertEquals("sea water stays at " + x, site.surface(x), source);
            } else {
                assertTrue("source " + source + " reaches the sea at " + x, source > seaTop);
            }
        }
        assertTrue("ground past the bank is still cut", top(blocks, 7, site.streamZ - 3) < site.surface(7));
        assertTrue("ground past the bank slopes from the water",
                top(blocks, 7, site.streamZ - 3) >= sourceTop(blocks, 7, site.streamZ) - 1);
    }

    @Test
    public void aBlockOnUnloweredWaterBesideALoweredCellStays() {
        Site site = new Site(FLATTEN_STREAM_Z, 2, Integer.MAX_VALUE);
        Map<Long, BlockState> first = fit(site, target(StructureTerrainMode.FLATTEN, TerrainAdjustment.NONE), NativeStructureSurfaceFitter.SeaSurface.NONE, false);
        int firstStanding = 8;
        while (sourceTop(first, firstStanding, site.streamZ) < site.surface(firstStanding)) {
            firstStanding++;
        }
        int blockY = Math.max(site.surface(firstStanding), site.surface(firstStanding - 1)) + 1;
        Map<Long, BlockState> blocks = site.blocks();
        BlockPos block = new BlockPos(firstStanding, blockY, site.streamZ);
        blocks.put(block.asLong(), Blocks.OAK_SLAB.defaultBlockState());

        fitOver(blocks, site, target(StructureTerrainMode.FLATTEN, TerrainAdjustment.NONE));

        assertEquals(Blocks.OAK_SLAB.defaultBlockState(), state(blocks, block.getX(), block.getY(), block.getZ()));
    }

    private static void fitOver(Map<Long, BlockState> blocks, Site site, NativeStructureTerrainIntegrator.TerrainTarget target) {
        WorldGenLevel world = world(blocks);
        for (int chunkX = FIRST_X >> 4; chunkX <= LAST_X >> 4; chunkX++) {
            BoundingBox area = new BoundingBox(chunkX << 4, MIN_Y, site.minZ(), (chunkX << 4) + 15, MAX_Y, site.maxZ());
            NativeStructureSurfaceFitter.prepareSurfaceStructures(world, area, List.of(target),
                    site::ground, NativeStructureSurfaceFitter.SeaSurface.NONE, site.water(), PlainSurfaceStrata.STRATA);
        }
    }

    private static NativeStructureTerrainIntegrator.TerrainTarget at(int minX, int maxX, int chunkX, int pieceY) {
        Structure structure = new DesertPyramidStructure(new Structure.StructureSettings(
                HolderSet.empty(), Map.of(), GenerationStep.Decoration.SURFACE_STRUCTURES, TerrainAdjustment.NONE));
        StructurePiece piece = mock(StructurePiece.class);
        when(piece.getBoundingBox()).thenReturn(new BoundingBox(minX, pieceY, 0, maxX, pieceY + 10, 15));
        StructureStart start = new StructureStart(structure, new ChunkPos(chunkX, 0), 0, new PiecesContainer(List.of(piece)));
        StructureTerrainSettings terrain = mock(StructureTerrainSettings.class);
        when(terrain.resolvedMode()).thenReturn(StructureTerrainMode.FLATTEN);
        when(terrain.resolvedFlattenRange()).thenReturn(64);
        when(terrain.getHorizontalPadding()).thenReturn(24);
        return new NativeStructureTerrainIntegrator.TerrainTarget("test:stream", start, terrain);
    }

    private static Map<Long, BlockState> fitMany(Site site, List<NativeStructureTerrainIntegrator.TerrainTarget> targets, boolean reversed) {
        Map<Long, BlockState> blocks = site.blocks();
        WorldGenLevel world = world(blocks);
        List<Integer> chunks = new ArrayList<>();
        for (int chunkX = FIRST_X >> 4; chunkX <= LAST_X >> 4; chunkX++) {
            chunks.add(chunkX);
        }
        if (reversed) {
            Collections.reverse(chunks);
        }
        for (int chunkX : chunks) {
            BoundingBox area = new BoundingBox(chunkX << 4, MIN_Y, site.minZ(), (chunkX << 4) + 15, MAX_Y, site.maxZ());
            List<NativeStructureTerrainIntegrator.TerrainTarget> visible = new ArrayList<>();
            for (NativeStructureTerrainIntegrator.TerrainTarget target : targets) {
                BoundingBox bounds = NativeStructureReferenceEnvelope.referenceBounds(target.start(), target.start().getStructure(), target.terrain(), target.structureId());
                if (bounds.intersects(area.minX(), area.minZ(), area.maxX(), area.maxZ())) {
                    visible.add(target);
                }
            }
            if (!visible.isEmpty()) {
                NativeStructureSurfaceFitter.prepareSurfaceStructures(world, area, visible, site::ground, NativeStructureSurfaceFitter.SeaSurface.NONE, site.water(), PlainSurfaceStrata.STRATA);
            }
        }
        return blocks;
    }

    @Test
    public void twoOverlappingStartsAtDifferentHeights() {
        Site site = new Site(FLATTEN_STREAM_Z, 2, Integer.MAX_VALUE);
        Map<Long, BlockState> east = fitMany(site, List.of(at(0, 15, 0, 81), at(30, 45, 2, 74)), false);
        Map<Long, BlockState> west = fitMany(site, List.of(at(0, 15, 0, 81), at(30, 45, 2, 74)), true);
        assertStreamRules(east, site);
        assertEquals(east, west);
    }

    @Test
    public void clampedBoxAtTheReferenceLimit() {
        Site site = new Site(FLATTEN_STREAM_Z, 2, Integer.MAX_VALUE);
        NativeStructureTerrainIntegrator.TerrainTarget clamped = at(0, 110, 0, 70);
        BoundingBox bounds = NativeStructureReferenceEnvelope.referenceBounds(clamped.start(), clamped.start().getStructure(), clamped.terrain(), clamped.structureId());
        assertEquals(143, bounds.maxX());
        Map<Long, BlockState> east = fitMany(site, List.of(clamped), false);
        Map<Long, BlockState> west = fitMany(site, List.of(at(0, 110, 0, 70)), true);
        assertStreamRules(east, site);
        assertEquals(east, west);
        assertEquals("past the box the plan head stands", site.surface(150), sourceTop(east, 150, site.streamZ));
    }

    private static void assertStreamRules(Map<Long, BlockState> blocks, Site site) {
        int previous = Integer.MAX_VALUE;
        for (int x = FIRST_X; x <= LAST_X; x++) {
            int water = waterTop(blocks, x, site.streamZ);
            assertTrue("water rises to " + water + " after " + previous + " at " + x, water <= previous);
            int bank = Math.min(top(blocks, x, site.streamZ - 1), top(blocks, x, site.streamZ + 1));
            assertTrue("water " + water + " beside lower ground " + bank + " at " + x, bank >= water);
            assertTrue("air under the water at " + x,
                    !state(blocks, x, waterBottom(blocks, x, site.streamZ) - 1, site.streamZ).isAir());
            int source = sourceTop(blocks, x, site.streamZ);
            assertTrue("source " + source + " beside a lower top at " + x, top(blocks, x + 1, site.streamZ) >= source
                    && top(blocks, x - 1, site.streamZ) >= source && bank >= source);
            previous = water;
        }
    }

    private static Map<Long, BlockState> fit(Site site, NativeStructureTerrainIntegrator.TerrainTarget target,
                                             NativeStructureSurfaceFitter.SeaSurface sea, boolean reversed) {
        Map<Long, BlockState> blocks = site.blocks();
        WorldGenLevel world = world(blocks);
        List<Integer> chunks = new ArrayList<>();
        for (int chunkX = FIRST_X >> 4; chunkX <= LAST_X >> 4; chunkX++) {
            chunks.add(chunkX);
        }
        if (reversed) {
            Collections.reverse(chunks);
        }
        for (int chunkX : chunks) {
            BoundingBox area = new BoundingBox(chunkX << 4, MIN_Y, site.minZ(), (chunkX << 4) + 15, MAX_Y, site.maxZ());
            NativeStructureSurfaceFitter.prepareSurfaceStructures(world, area, List.of(target),
                    site::ground, sea, site.water(), PlainSurfaceStrata.STRATA);
        }
        return blocks;
    }

    private static NativeStructureTerrainIntegrator.TerrainTarget target(StructureTerrainMode mode, TerrainAdjustment adjustment) {
        Structure structure = new DesertPyramidStructure(new Structure.StructureSettings(
                HolderSet.empty(), Map.of(), GenerationStep.Decoration.SURFACE_STRUCTURES, adjustment));
        StructurePiece piece = mock(StructurePiece.class);
        when(piece.getBoundingBox()).thenReturn(new BoundingBox(0, PIECE_MIN_Y, 0, 15, PIECE_MIN_Y + 10, 15));
        StructureStart start = new StructureStart(structure, new ChunkPos(0, 0), 0, new PiecesContainer(List.of(piece)));
        StructureTerrainSettings terrain = mock(StructureTerrainSettings.class);
        when(terrain.resolvedMode()).thenReturn(mode);
        when(terrain.resolvedFlattenRange()).thenReturn(64);
        when(terrain.getHorizontalPadding()).thenReturn(24);
        return new NativeStructureTerrainIntegrator.TerrainTarget("test:stream", start, terrain);
    }

    private static int waterTop(Map<Long, BlockState> blocks, int x, int z) {
        for (int y = MAX_Y; y >= MIN_Y; y--) {
            if (!state(blocks, x, y, z).getFluidState().isEmpty()) {
                return y;
            }
        }
        throw new AssertionError("no water at " + x + "," + z);
    }

    private static int sourceTop(Map<Long, BlockState> blocks, int x, int z) {
        for (int y = MAX_Y; y >= MIN_Y; y--) {
            if (state(blocks, x, y, z).getFluidState().isSource()) {
                return y;
            }
        }
        throw new AssertionError("no source at " + x + "," + z);
    }

    private static int waterBottom(Map<Long, BlockState> blocks, int x, int z) {
        int y = waterTop(blocks, x, z);
        while (!state(blocks, x, y - 1, z).getFluidState().isEmpty()) {
            y--;
        }
        return y;
    }

    private static int top(Map<Long, BlockState> blocks, int x, int z) {
        for (int y = MAX_Y; y >= MIN_Y; y--) {
            if (!state(blocks, x, y, z).isAir()) {
                return y;
            }
        }
        return MIN_Y - 1;
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

    private record Site(int streamZ, int bankHeight, int mouthX) {
        private int surface(int x) {
            return x >= mouthX ? 82 : 90 - Math.floorDiv(x, 4);
        }

        private int minZ() {
            return streamZ - 3;
        }

        private int maxZ() {
            return streamZ + 3;
        }

        private StructureWater water() {
            return (x, z) -> z != streamZ || x < -300 || x > 300 ? StructureWaterColumn.DRY
                    : new StructureWaterColumn(true, x < mouthX, surface(x) - 1, surface(x));
        }

        private int ground(int x, int z) {
            return z == streamZ ? surface(x) - 1 : surface(x) + bankHeight;
        }

        private Map<Long, BlockState> blocks() {
            Map<Long, BlockState> blocks = new HashMap<>();
            for (int x = FIRST_X - 16; x <= LAST_X + 16; x++) {
                for (int z = minZ(); z <= maxZ(); z++) {
                    int ground = ground(x, z);
                    for (int y = MIN_Y; y <= ground; y++) {
                        blocks.put(BlockPos.asLong(x, y, z), Blocks.STONE.defaultBlockState());
                    }
                    if (z == streamZ) {
                        blocks.put(BlockPos.asLong(x, ground, z), Blocks.SAND.defaultBlockState());
                        blocks.put(BlockPos.asLong(x, surface(x), z), Blocks.WATER.defaultBlockState());
                        for (int y = surface(x) + 1; y <= surface(x - 1); y++) {
                            blocks.put(BlockPos.asLong(x, y, z),
                                    Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 8));
                        }
                    }
                }
            }
            return blocks;
        }
    }
}

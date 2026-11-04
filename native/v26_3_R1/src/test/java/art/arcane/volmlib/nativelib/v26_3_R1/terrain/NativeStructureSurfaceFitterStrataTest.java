package art.arcane.volmlib.nativelib.v26_3_R1.terrain;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NativeStructureSurfaceFitterStrataTest {
    private static final int ORIGINAL = 70;

    @BeforeClass
    public static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void fillPaintsTheBiomeStrataInsteadOfRepeatingTheBlockUnderTheOldSurface() {
        Map<Long, BlockState> blocks = naturalColumn(Blocks.COAL_ORE.defaultBlockState());

        apply(blocks, ORIGINAL, 90);

        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(blocks, 90));
        assertEquals(Blocks.DIRT.defaultBlockState(), state(blocks, 89));
        assertEquals(Blocks.DIRT.defaultBlockState(), state(blocks, 88));
        for (int y = 87; y > ORIGINAL; y--) {
            BlockState expected = y % 5 == 0 ? Blocks.IRON_ORE.defaultBlockState() : Blocks.STONE.defaultBlockState();
            assertEquals("Fill at y " + y, expected, state(blocks, y));
        }
    }

    @Test
    public void fillRepaintsTheBuriedSurfaceAndSoil() {
        Map<Long, BlockState> blocks = naturalColumn(Blocks.DIRT.defaultBlockState());

        apply(blocks, ORIGINAL, 90);

        for (int y = ORIGINAL; y > ORIGINAL - 3; y--) {
            assertEquals("Old soil at y " + y, y % 5 == 0 ? Blocks.IRON_ORE.defaultBlockState()
                    : Blocks.STONE.defaultBlockState(), state(blocks, y));
        }
    }

    @Test
    public void fillOnlyTallerThanTheSoilKeepsTheSurfaceOnTop() {
        Map<Long, BlockState> blocks = naturalColumn(Blocks.DIRT.defaultBlockState());

        apply(blocks, ORIGINAL, ORIGINAL + 1);

        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(blocks, ORIGINAL + 1));
        assertEquals(Blocks.DIRT.defaultBlockState(), state(blocks, ORIGINAL));
        assertEquals(Blocks.DIRT.defaultBlockState(), state(blocks, ORIGINAL - 1));
    }

    @Test
    public void cutRebuildsTheSurfaceAndSoilUnderTheNewGround() {
        Map<Long, BlockState> blocks = naturalColumn(Blocks.DIRT.defaultBlockState());
        blocks.put(BlockPos.asLong(0, 60, 0), Blocks.COAL_ORE.defaultBlockState());

        apply(blocks, ORIGINAL, 62);

        assertTrue(state(blocks, 63).isAir());
        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(blocks, 62));
        assertEquals(Blocks.DIRT.defaultBlockState(), state(blocks, 61));
        assertEquals(Blocks.DIRT.defaultBlockState(), state(blocks, 60));
        assertEquals("rock under the soil stays natural", Blocks.STONE.defaultBlockState(), state(blocks, 59));
    }

    @Test
    public void cutLeavesCavesUnderTheNewSurfaceOpen() {
        Map<Long, BlockState> blocks = naturalColumn(Blocks.DIRT.defaultBlockState());
        blocks.remove(BlockPos.asLong(0, 61, 0));

        apply(blocks, ORIGINAL, 62);

        assertTrue(state(blocks, 61).isAir());
        assertEquals(Blocks.DIRT.defaultBlockState(), state(blocks, 60));
    }

    @Test
    public void cutBelowTheSeaAsksForSubmergedStrata() {
        Map<Long, BlockState> blocks = naturalColumn(Blocks.DIRT.defaultBlockState());
        boolean[] submerged = new boolean[1];
        NativeStructureSurfaceFitter.SurfaceStrata strata = (x, z, surfaceY, wet) -> {
            submerged[0] = wet;
            return STRATA.column(x, z, surfaceY, wet);
        };

        NativeStructureSurfaceFitter.applySurfaceColumn(world(blocks), new BlockPos.MutableBlockPos(),
                0, 0, ORIGINAL, 50, 0, 127, false,
                new NativeStructureSurfaceFitter.SeaSurface(62, Blocks.WATER.defaultBlockState()), strata);

        assertTrue(submerged[0]);
        assertEquals(Blocks.DIRT.defaultBlockState(), state(blocks, 50));
    }

    @Test
    public void paintingDependsOnTheColumnNotOnTheOrderColumnsAreWritten() {
        Map<Long, BlockState> first = naturalColumn(Blocks.DIRT.defaultBlockState());
        Map<Long, BlockState> second = naturalColumn(Blocks.DIRT.defaultBlockState());

        apply(first, ORIGINAL, 90);
        apply(first, ORIGINAL, 90);
        apply(second, ORIGINAL, 90);

        assertEquals(second, first);
    }

    private static final NativeStructureSurfaceFitter.SurfaceStrata STRATA =
            (x, z, surfaceY, submerged) -> new NativeStructureSurfaceFitter.SurfaceStrata.Column() {
                @Override
                public BlockState block(int y) {
                    int depth = surfaceY - y;
                    if (depth == 0) {
                        return submerged ? Blocks.DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
                    }
                    if (depth <= 2) {
                        return Blocks.DIRT.defaultBlockState();
                    }
                    return y % 5 == 0 ? Blocks.IRON_ORE.defaultBlockState() : Blocks.STONE.defaultBlockState();
                }

                @Override
                public int soilDepth() {
                    return 3;
                }
            };

    private static void apply(Map<Long, BlockState> blocks, int originalY, int targetY) {
        NativeStructureSurfaceFitter.applySurfaceColumn(world(blocks), new BlockPos.MutableBlockPos(),
                0, 0, originalY, targetY, 0, 127, false, NativeStructureSurfaceFitter.SeaSurface.NONE, STRATA);
    }

    private static Map<Long, BlockState> naturalColumn(BlockState underSurface) {
        Map<Long, BlockState> blocks = new HashMap<>();
        for (int y = 0; y < ORIGINAL - 1; y++) {
            blocks.put(BlockPos.asLong(0, y, 0), Blocks.STONE.defaultBlockState());
        }
        blocks.put(BlockPos.asLong(0, ORIGINAL - 1, 0), underSurface);
        blocks.put(BlockPos.asLong(0, ORIGINAL, 0), Blocks.GRASS_BLOCK.defaultBlockState());
        return blocks;
    }

    private static BlockState state(Map<Long, BlockState> blocks, int y) {
        return blocks.getOrDefault(BlockPos.asLong(0, y, 0), Blocks.AIR.defaultBlockState());
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

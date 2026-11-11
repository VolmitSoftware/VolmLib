package art.arcane.volmlib.nativelib.v26_3_R1.terrain;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NativeStructureTemplateOccupancyTest {
    @BeforeClass
    public static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void thinSnowAndCarpetNeedGroundBeneathThem() {
        assertTrue(NativeStructureTemplateOccupancy.isFoundationBase(snow(1)));
        assertTrue(NativeStructureTemplateOccupancy.isFoundationBase(Blocks.CARPET.white().defaultBlockState()));
        assertTrue(NativeStructureTemplateOccupancy.isFoundationBase(Blocks.STONE.defaultBlockState()));
    }

    @Test
    public void airFluidsAndMarkersAreNotFoundationBases() {
        assertFalse(NativeStructureTemplateOccupancy.isFoundationBase(Blocks.AIR.defaultBlockState()));
        assertFalse(NativeStructureTemplateOccupancy.isFoundationBase(Blocks.WATER.defaultBlockState()));
        assertFalse(NativeStructureTemplateOccupancy.isFoundationBase(Blocks.STRUCTURE_VOID.defaultBlockState()));
        assertFalse(NativeStructureTemplateOccupancy.isFoundationBase(Blocks.JIGSAW.defaultBlockState()));
        assertFalse(NativeStructureTemplateOccupancy.isFoundationBase(Blocks.TORCH.defaultBlockState()));
    }

    @Test
    public void snowOnlyColumnBecomesAFoundationCell() {
        Map<Long, NativeStructureTemplateOccupancy.OccupancyCell> cells = new HashMap<>();
        put(cells, 0, 64, 0, snow(1));
        put(cells, 1, 64, 0, Blocks.CARPET.white().defaultBlockState());

        Map<Long, NativeStructureTemplateOccupancy.LowestCell> lowest =
                NativeStructureTemplateOccupancy.lowestFoundationCells(cells);

        assertEquals(64, lowest.get(NativeStructureTemplateOccupancy.columnKey(0, 0)).y());
        assertEquals(64, lowest.get(NativeStructureTemplateOccupancy.columnKey(1, 0)).y());
    }

    @Test
    public void lowerSolidBlockStillAnchorsTheColumn() {
        Map<Long, NativeStructureTemplateOccupancy.OccupancyCell> cells = new HashMap<>();
        put(cells, 0, 65, 0, snow(1));
        put(cells, 0, 64, 0, Blocks.GRASS_BLOCK.defaultBlockState());
        put(cells, 2, 64, 0, Blocks.TORCH.defaultBlockState());

        Map<Long, NativeStructureTemplateOccupancy.LowestCell> lowest =
                NativeStructureTemplateOccupancy.lowestFoundationCells(cells);

        assertEquals(64, lowest.get(NativeStructureTemplateOccupancy.columnKey(0, 0)).y());
        assertNull(lowest.get(NativeStructureTemplateOccupancy.columnKey(2, 0)));
    }

    private static BlockState snow(int layers) {
        return Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers);
    }

    private static void put(Map<Long, NativeStructureTemplateOccupancy.OccupancyCell> cells,
                            int x, int y, int z, BlockState state) {
        cells.put(BlockPos.asLong(x, y, z), new NativeStructureTemplateOccupancy.OccupancyCell(state, false));
    }
}

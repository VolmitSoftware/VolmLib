package art.arcane.volmlib.nativelib.terrain.structure;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class StructureWaterRulesTest {
    private static final int RIVER_FLOOR = 60;
    private static final int RIVER_SURFACE = 64;

    @Test
    public void structuresAvoidRivers() {
        Set<Long> river = new HashSet<>();
        StructureWater water = riverAt(river);
        List<StructureStagePolicy.Footprint> pieces = List.of(
                new StructureStagePolicy.Footprint(0, 0, 15, 15),
                new StructureStagePolicy.Footprint(8, 0, 39, 15));
        for (int column = 0; column < 64; column++) {
            river.add(key(32 + (column & 7), column >> 3));
        }

        assertTrue("64 of 640 columns is 10% river", StructureWaterRules.admitsFootprint(water, pieces));

        river.add(key(39, 15));
        assertFalse("a river beyond the start chunk counts towards the whole footprint",
                StructureWaterRules.admitsFootprint(water, pieces));
    }

    @Test
    public void onlyRiverWaterCountsTowardsTheLimit() {
        StructureWater mouth = (x, z) -> new StructureWaterColumn(true, false, RIVER_FLOOR, RIVER_SURFACE);
        List<StructureStagePolicy.Footprint> pieces = List.of(new StructureStagePolicy.Footprint(0, 0, 15, 15));

        assertTrue(StructureWaterRules.admitsFootprint(mouth, pieces));
        assertTrue(StructureWaterRules.admitsFootprint(StructureWater.DRY, pieces));
        assertThrows(IllegalStateException.class, () -> new StructureWaterColumn(false, true, 0, 0));
    }

    @Test
    public void startsThatAllowWaterOrSitUndergroundIgnoreTheLimit() {
        StructureWater flooded = (x, z) -> riverColumn();
        List<StructureStagePolicy.Footprint> pieces = List.of(new StructureStagePolicy.Footprint(0, 0, 15, 15));

        assertFalse(StructureWaterRules.admitsStart(plan(false, false), flooded, pieces));
        assertTrue(StructureWaterRules.admitsStart(plan(true, false), flooded, pieces));
        assertTrue(StructureWaterRules.admitsStart(plan(false, true), flooded, pieces));
        assertTrue("starts no placement planned keep their own rules",
                StructureWaterRules.admitsStart(null, flooded, pieces));
    }

    @Test
    public void carvesSpareWaterColumnsAndTheirBanks() {
        StructureWater water = (x, z) -> x == 3 || x == 4 ? riverColumn() : StructureWaterColumn.DRY;

        StructureWaterRules.Carve carve = StructureWaterRules.carve(water, 0, 0, 8, 2);

        assertTrue(carve.keeps(3, RIVER_FLOOR - 2, 1));
        assertTrue(carve.keeps(4, RIVER_SURFACE + 20, 1));
        assertFalse("rock deeper than two blocks under the floor is carved", carve.keeps(3, RIVER_FLOOR - 3, 1));
        assertTrue(carve.keeps(1, RIVER_FLOOR - 2, 1));
        assertTrue(carve.keeps(6, RIVER_SURFACE, 1));
        assertFalse("the bank above the water surface is carved", carve.keeps(1, RIVER_SURFACE + 1, 1));
        assertFalse("three columns away from the water is carved", carve.keeps(0, RIVER_FLOOR, 1));
        assertFalse(carve.keeps(7, RIVER_FLOOR, 1));
        assertFalse("outside the sampled area nothing is kept", carve.keeps(3, RIVER_FLOOR, 9));
        assertFalse(StructureWaterRules.carve(StructureWater.DRY, 0, 0, 8, 2).keeps(3, RIVER_FLOOR, 1));
    }

    private static StructureWater riverAt(Set<Long> river) {
        return (x, z) -> river.contains(key(x, z)) ? riverColumn() : StructureWaterColumn.DRY;
    }

    private static StructureWaterColumn riverColumn() {
        return new StructureWaterColumn(true, true, RIVER_FLOOR, RIVER_SURFACE);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static StructureStartPlan plan(boolean allowWater, boolean underground) {
        return new StructureStartPlan() {
            @Override
            public String structureKey() {
                return "minecraft:village_plains";
            }

            @Override
            public JigsawSettings jigsaw() {
                return null;
            }

            @Override
            public boolean underground() {
                return underground;
            }

            @Override
            public boolean allowWater() {
                return allowWater;
            }

            @Override
            public boolean replacesSource() {
                return false;
            }

            @Override
            public StructureTerrainSettings terrain() {
                return null;
            }

            @Override
            public int chunkX() {
                return 0;
            }

            @Override
            public int chunkZ() {
                return 0;
            }

            @Override
            public int baseY() {
                return 64;
            }

            @Override
            public long placementIdentity() {
                return 1L;
            }
        };
    }
}

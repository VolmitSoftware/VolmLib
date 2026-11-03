package art.arcane.volmlib.nativelib.terrain.structure;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.IntBinaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StructureStreamLoweringTest {
    private static final int STREAM_Z = 0;
    private static final int BOX = 120;
    private static final int NO_SEA = Integer.MIN_VALUE;

    @Test
    public void bowlLowersTheStreamAndHoldsUntilItsOwnHeadFalls() {
        Line line = Line.falling(4);
        IntBinaryOperator after = bowl(line, 0, 20, 10);

        Map<Long, Integer> plan = plan(line, after, NO_SEA);

        assertTrue(line.surface(0) - level(plan, line, 0) >= 8);
        assertTrue("the channel continues past the bowl", level(plan, line, 25) < line.surface(25));
        assertEquals("the stream's own head takes over again", line.surface(100), level(plan, line, 100));
        assertRules(plan, line, after);
    }

    @Test
    public void loweredWaterNeverRisesDownstreamAndNeverStandsAboveItsBanks() {
        Random random = new Random(1337L);
        int lowered = 0;
        for (int round = 0; round < 200; round++) {
            Line line = Line.random(random);
            IntBinaryOperator after = bowl(line, random.nextInt(61) - 30, 8 + random.nextInt(24), 1 + random.nextInt(14));
            Map<Long, Integer> plan = plan(line, after, NO_SEA);
            lowered += plan.isEmpty() ? 0 : 1;
            if (!plan.isEmpty()) {
                assertRules(plan, line, after);
            }
        }
        assertTrue("the random cases must exercise lowering, got " + lowered, lowered > 50);
    }

    @Test
    public void aStreamThatCannotFallLeavingTheBoxKeepsItsLevel() {
        Line flat = Line.falling(Integer.MAX_VALUE);

        assertTrue(plan(flat, bowl(flat, 0, 20, 6), NO_SEA).isEmpty());
    }

    @Test
    public void aHoldStopsAtTheLevelTheStreamLeavesTheBoxAt() {
        Line line = Line.falling(16);
        IntBinaryOperator after = bowl(line, 0, 20, 10);

        Map<Long, Integer> plan = StructureStreamLowering.plan(line::columnAt, line::ground, after, NO_SEA, -30, -30, 12, 30);

        int exit = line.surface(12);
        assertFalse(plan.isEmpty());
        plan.values().forEach((Integer level) -> assertTrue(level + " below the exit " + exit, level >= exit));
    }

    @Test
    public void waterJoinedToTheSeaStaysAndItsFeederStopsAtItsLevel() {
        Line line = Line.falling(4);
        int mouth = line.surface(16);
        StructureWater water = (x, z) -> z != STREAM_Z || Math.abs(x) > BOX ? StructureWaterColumn.DRY
                : x >= 16 ? new StructureWaterColumn(true, false, line.surface(x) - 2, line.surface(x))
                : line.column(x);
        IntBinaryOperator after = bowl(line, 0, 20, 12);

        Map<Long, Integer> plan = StructureStreamLowering.plan(water, line::ground, after, NO_SEA, -BOX, -BOX, BOX, BOX);

        for (int x = -BOX; x <= BOX; x++) {
            Integer level = plan.get(StructureStreamLowering.columnKey(x, STREAM_Z));
            if (x >= 16) {
                assertEquals("mouth water stays at " + x, null, level);
            } else if (level != null) {
                assertTrue(level + " below the mouth at " + x, level >= mouth);
            }
        }
    }

    @Test
    public void aLevelStopsAboveTheSea() {
        Line line = Line.falling(4);
        IntBinaryOperator after = bowl(line, 0, 20, 10);
        int free = level(plan(line, after, NO_SEA), line, 0);

        assertEquals(free + 3, level(plan(line, after, free + 2), line, 0));
        assertEquals(free, level(plan(line, after, free - 2), line, 0));
    }

    @Test
    public void aWideRiverStaysAndItsFeederStopsAtItsLevel() {
        Line line = Line.falling(4);
        StructureWater wide = (x, z) -> Math.abs(z) > 3 || Math.abs(x) > BOX ? StructureWaterColumn.DRY : line.column(x);
        IntBinaryOperator after = (x, z) -> Math.abs(z) == 4 && Math.abs(x) < 20 ? line.ground(x, z) - 8 : line.ground(x, z);

        assertTrue(StructureStreamLowering.plan(wide, line::ground, after, NO_SEA, -BOX, -BOX, BOX, BOX).isEmpty());
    }

    @Test
    public void aBigCutStopsAtTheDropLimit() {
        Line line = Line.falling(2);

        Map<Long, Integer> plan = plan(line, bowl(line, 0, 20, StructureStreamLowering.MAX_DROP + 6), NO_SEA);

        assertFalse(plan.isEmpty());
        for (int x = -BOX + 3; x <= BOX - 3; x++) {
            assertTrue("drop at " + x, line.surface(x) - level(plan, line, x) <= StructureStreamLowering.MAX_DROP);
        }
    }

    @Test
    public void streamCellsSeparatedByAGapStillHoldTheLevel() {
        Line line = Line.falling(4);
        StructureWater gapped = (x, z) -> z == STREAM_Z && Math.abs(x) <= BOX && Math.floorMod(x, 2) == 0
                ? line.column(x) : StructureWaterColumn.DRY;
        IntBinaryOperator after = bowl(line, 0, 20, 10);

        Map<Long, Integer> plan = StructureStreamLowering.plan(gapped, line::ground, after, NO_SEA, -BOX, -BOX, BOX, BOX);

        int previous = Integer.MAX_VALUE;
        for (int x = -BOX + 4; x <= BOX - 4; x += 2) {
            int level = level(plan, line, x);
            assertTrue("rise at " + x, level <= previous);
            previous = level;
        }
    }

    @Test
    public void neighbouringChunksReadTheSameLevels() {
        Line line = Line.falling(4);
        Map<Long, Integer> plan = plan(line, bowl(line, 0, 20, 10), NO_SEA);
        StructureWater water = line::columnAt;
        StructureStreamLowering.ChunkView west = StructureStreamLowering.ChunkView.of(water, List.of(plan), -16, -16, -1, -1);
        StructureStreamLowering.ChunkView east = StructureStreamLowering.ChunkView.of(water, List.of(plan), 0, 0, 15, 15);

        assertEquals(west.level(0, STREAM_Z), east.level(0, STREAM_Z));
        assertEquals(west.level(-1, STREAM_Z), east.level(-1, STREAM_Z));
        assertEquals(level(plan, line, -1), east.level(-1, STREAM_Z));
    }

    @Test
    public void theLowestStartWinsAndStaysMonotonic() {
        Line line = Line.falling(4);
        Map<Long, Integer> first = plan(line, bowl(line, -20, 12, 8), NO_SEA);
        Map<Long, Integer> second = plan(line, bowl(line, 20, 12, 12), NO_SEA);
        StructureStreamLowering.ChunkView view = StructureStreamLowering.ChunkView.of(
                line::columnAt, List.of(first, second), -BOX + 4, -8, BOX - 4, 8);

        int previous = Integer.MAX_VALUE;
        for (int x = -BOX + 4; x <= BOX - 4; x++) {
            int level = view.level(x, STREAM_Z);
            assertEquals(Math.min(level(first, line, x), level(second, line, x)), level);
            assertTrue("rise at " + x, level <= previous);
            previous = level;
        }
    }

    @Test
    public void keptBanksStopAtTheGroundAndFadeBeyondTheBank() {
        Line line = Line.falling(Integer.MAX_VALUE);
        int surface = line.surface(0);
        StructureStreamLowering.ChunkView view = StructureStreamLowering.ChunkView.of(line::columnAt, List.of(), -8, -8, 8, 8);

        assertEquals("a cut bank keeps the water's level", surface, view.keepBank(0, 2, surface + 2, surface - 6));
        assertEquals("lower ground is never raised to the water", surface - 1, view.keepBank(0, 2, surface - 1, surface - 6));
        assertEquals("ground beyond the bank is cut", surface - 6, view.keepBank(0, 3, surface + 2, surface - 6));
        assertEquals("uncut ground stays", surface + 2, view.keepBank(0, 2, surface + 2, surface + 2));
    }

    private static Map<Long, Integer> plan(Line line, IntBinaryOperator after, int seaTop) {
        return StructureStreamLowering.plan(line::columnAt, line::ground, after, seaTop, -BOX, -BOX, BOX, BOX);
    }

    private static int level(Map<Long, Integer> plan, Line line, int x) {
        return plan.getOrDefault(StructureStreamLowering.columnKey(x, STREAM_Z), line.surface(x));
    }

    private static IntBinaryOperator bowl(Line line, int centerX, int radius, int cut) {
        return (x, z) -> {
            double distance = Math.hypot(x - centerX, z - STREAM_Z);
            if (distance >= radius) {
                return line.ground(x, z);
            }
            double progress = distance / radius;
            double factor = 1D - progress * progress * (3D - 2D * progress);
            return line.ground(x, z) - (int) Math.round(cut * factor);
        };
    }

    private static void assertRules(Map<Long, Integer> plan, Line line, IntBinaryOperator after) {
        int previous = Integer.MAX_VALUE;
        for (int x = -BOX + 3; x <= BOX - 3; x++) {
            int level = level(plan, line, x);
            assertTrue("rise at " + x + ": " + level + " after " + previous, level <= previous);
            assertTrue("above its own head at " + x, level <= line.surface(x));
            StructureStreamLowering.ChunkView view = StructureStreamLowering.ChunkView.of(
                    line::columnAt, List.of(plan), x - 1, STREAM_Z - 1, x + 1, STREAM_Z + 1);
            for (int z = STREAM_Z - 1; z <= STREAM_Z + 1; z += 2) {
                int kept = view.keepBank(x, z, line.ground(x, z), after.applyAsInt(x, z));
                assertTrue("water " + level + " beside lower ground " + kept + " at " + x, level <= kept);
            }
            previous = level;
        }
    }

    private record Line(int run, int[] extraDrops) {
        private static Line falling(int run) {
            return new Line(run, new int[0]);
        }

        private static Line random(Random random) {
            int[] drops = new int[random.nextInt(3)];
            for (int index = 0; index < drops.length; index++) {
                drops[index] = random.nextInt(BOX * 2) - BOX;
            }
            return new Line(1 + random.nextInt(8), drops);
        }

        private int surface(int x) {
            int surface = 300 - (run == Integer.MAX_VALUE ? 0 : Math.floorDiv(x + BOX, run));
            for (int drop : extraDrops) {
                surface -= x >= drop ? 4 : 0;
            }
            return surface;
        }

        private StructureWaterColumn column(int x) {
            return new StructureWaterColumn(true, true, surface(x) - 1, surface(x));
        }

        private StructureWaterColumn columnAt(int x, int z) {
            return z == STREAM_Z && Math.abs(x) <= BOX ? column(x) : StructureWaterColumn.DRY;
        }

        private int ground(int x, int z) {
            return surface(x) + 2;
        }
    }
}

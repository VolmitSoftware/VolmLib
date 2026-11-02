package art.arcane.volmlib.nativelib.terrain.structure;

import java.util.BitSet;
import java.util.List;

public final class StructureWaterRules {
    public static final int MAX_RIVER_PERCENT = 10;
    public static final int BANK = 2;
    public static final int FLOOR = 2;
    private static final long MAX_FOOTPRINT_COLUMNS = 1L << 26;

    private StructureWaterRules() {
    }

    public static boolean admitsStart(StructureStartPlan plan, StructureWater water,
                                      List<StructureStagePolicy.Footprint> pieces) {
        return plan == null || plan.underground() || plan.allowWater() || admitsFootprint(water, pieces);
    }

    public static boolean admitsFootprint(StructureWater water, List<StructureStagePolicy.Footprint> pieces) {
        if (pieces.isEmpty()) {
            return true;
        }
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (StructureStagePolicy.Footprint piece : pieces) {
            minX = Math.min(minX, piece.minX());
            minZ = Math.min(minZ, piece.minZ());
            maxX = Math.max(maxX, piece.maxX());
            maxZ = Math.max(maxZ, piece.maxZ());
        }
        long width = (long) maxX - minX + 1L;
        long depth = (long) maxZ - minZ + 1L;
        if (width * depth > MAX_FOOTPRINT_COLUMNS) {
            throw new IllegalStateException("Structure footprint " + minX + "," + minZ + " to " + maxX + "," + maxZ
                    + " is too large to test against river water");
        }
        BitSet covered = new BitSet((int) (width * depth));
        for (StructureStagePolicy.Footprint piece : pieces) {
            for (int z = piece.minZ(); z <= piece.maxZ(); z++) {
                int row = (int) ((z - (long) minZ) * width);
                covered.set(row + piece.minX() - minX, row + piece.maxX() - minX + 1);
            }
        }
        long columns = 0L;
        long river = 0L;
        for (int index = covered.nextSetBit(0); index >= 0; index = covered.nextSetBit(index + 1)) {
            columns++;
            if (water.column(minX + (int) (index % width), minZ + (int) (index / width)).river()) {
                river++;
            }
        }
        return river * 100L <= columns * MAX_RIVER_PERCENT;
    }

    public static Carve carve(StructureWater water, int minX, int minZ, int maxX, int maxZ) {
        if (maxX < minX || maxZ < minZ) {
            return Carve.NONE;
        }
        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        int sampleWidth = width + BANK * 2;
        int sampleDepth = depth + BANK * 2;
        StructureWaterColumn[] samples = new StructureWaterColumn[sampleWidth * sampleDepth];
        boolean any = false;
        for (int sz = 0; sz < sampleDepth; sz++) {
            for (int sx = 0; sx < sampleWidth; sx++) {
                StructureWaterColumn column = water.column(minX - BANK + sx, minZ - BANK + sz);
                samples[sz * sampleWidth + sx] = column;
                any |= column.wet();
            }
        }
        if (!any) {
            return Carve.NONE;
        }
        int[] floors = new int[width * depth];
        int[] tops = new int[width * depth];
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                int floor = Integer.MAX_VALUE;
                int top = Integer.MIN_VALUE;
                for (int dz = -BANK; dz <= BANK; dz++) {
                    for (int dx = -BANK; dx <= BANK; dx++) {
                        StructureWaterColumn column = samples[(z + BANK + dz) * sampleWidth + x + BANK + dx];
                        if (!column.wet()) {
                            continue;
                        }
                        floor = Math.min(floor, column.floor() - FLOOR);
                        top = dx == 0 && dz == 0 ? Integer.MAX_VALUE : Math.max(top, column.surface());
                    }
                }
                floors[z * width + x] = floor;
                tops[z * width + x] = top;
            }
        }
        return new Carve(minX, minZ, width, depth, floors, tops);
    }

    public static final class Carve {
        public static final Carve NONE = new Carve(0, 0, 0, 0, null, null);

        private final int minX;
        private final int minZ;
        private final int width;
        private final int depth;
        private final int[] floors;
        private final int[] tops;

        private Carve(int minX, int minZ, int width, int depth, int[] floors, int[] tops) {
            this.minX = minX;
            this.minZ = minZ;
            this.width = width;
            this.depth = depth;
            this.floors = floors;
            this.tops = tops;
        }

        public boolean keeps(int x, int y, int z) {
            if (floors == null) {
                return false;
            }
            int localX = x - minX;
            int localZ = z - minZ;
            if (localX < 0 || localZ < 0 || localX >= width || localZ >= depth) {
                return false;
            }
            int index = localZ * width + localX;
            return y >= floors[index] && y <= tops[index];
        }
    }
}

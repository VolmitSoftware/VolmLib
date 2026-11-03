package art.arcane.volmlib.nativelib.terrain.structure;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;

public final class StructureStreamLowering {
    public static final int HOLD_REACH = 64;
    public static final int MAX_DROP = 16;
    private static final int LINK = StructureWaterRules.BANK;
    private static final int EDGE = StructureWaterRules.BANK + 1;
    private static final int WIDE = 2;
    private static final int[][] CARDINALS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private StructureStreamLowering() {
    }

    public static long columnKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static boolean holdsWater(StructureWaterColumn column) {
        return column.wet() && column.surface() > column.floor();
    }

    public static Map<Long, Integer> plan(StructureWater water, IntBinaryOperator groundBefore,
                                          IntBinaryOperator groundAfter, int seaTop,
                                          int minX, int minZ, int maxX, int maxZ) {
        Field field = Field.read(water, seaTop, minX, minZ, maxX, maxZ);
        if (!field.capAtCutBanks(groundBefore, groundAfter)) {
            return Map.of();
        }
        field.groupFlatRuns();
        field.raiseFloorsUpstream();
        field.holdDownstream();
        return field.loweredLevels();
    }

    private static final class Field {
        private static final int DRY = Integer.MIN_VALUE;

        private final int minX;
        private final int minZ;
        private final int width;
        private final int depth;
        private final int seaTop;
        private final int[] surface;
        private final int[] level;
        private final int[] floor;
        private final boolean[] fixed;
        private final List<Integer> streams = new ArrayList<>();
        private final List<List<Integer>> flatRuns = new ArrayList<>();

        private Field(int seaTop, int minX, int minZ, int width, int depth) {
            this.seaTop = seaTop;
            this.minX = minX;
            this.minZ = minZ;
            this.width = width;
            this.depth = depth;
            surface = new int[width * depth];
            level = new int[width * depth];
            floor = new int[width * depth];
            fixed = new boolean[width * depth];
        }

        private static Field read(StructureWater water, int seaTop, int minX, int minZ, int maxX, int maxZ) {
            Field field = new Field(seaTop, minX, minZ, maxX - minX + 1, maxZ - minZ + 1);
            for (int z = 0; z < field.depth; z++) {
                for (int x = 0; x < field.width; x++) {
                    int index = z * field.width + x;
                    StructureWaterColumn column = water.column(minX + x, minZ + z);
                    if (!holdsWater(column)) {
                        field.surface[index] = DRY;
                        continue;
                    }
                    field.surface[index] = column.surface();
                    field.level[index] = column.surface();
                    field.fixed[index] = !column.river() || column.surface() <= seaTop
                            || x < EDGE || z < EDGE || x >= field.width - EDGE || z >= field.depth - EDGE;
                }
            }
            field.fixWideWater();
            for (int index = 0; index < field.surface.length; index++) {
                if (field.surface[index] != DRY && !field.fixed[index]) {
                    field.streams.add(index);
                }
            }
            return field;
        }

        private void fixWideWater() {
            int span = WIDE * 2 + 1;
            List<Integer> corners = new ArrayList<>();
            for (int z = 0; z + span <= depth; z++) {
                for (int x = 0; x + span <= width; x++) {
                    if (allWet(x, z, span)) {
                        corners.add(z * width + x);
                    }
                }
            }
            for (int corner : corners) {
                for (int dz = 0; dz < span; dz++) {
                    for (int dx = 0; dx < span; dx++) {
                        fixed[corner + dz * width + dx] = true;
                    }
                }
            }
        }

        private boolean allWet(int x, int z, int span) {
            for (int dz = span - 1; dz >= 0; dz--) {
                for (int dx = span - 1; dx >= 0; dx--) {
                    if (surface[(z + dz) * width + x + dx] == DRY) {
                        return false;
                    }
                }
            }
            return true;
        }

        private boolean capAtCutBanks(IntBinaryOperator groundBefore, IntBinaryOperator groundAfter) {
            boolean capped = false;
            for (int index : streams) {
                int x = index % width;
                int z = index / width;
                for (int[] direction : CARDINALS) {
                    int nx = x + direction[0];
                    int nz = z + direction[1];
                    if (nx < 0 || nz < 0 || nx >= width || nz >= depth || surface[nz * width + nx] != DRY) {
                        continue;
                    }
                    int after = groundAfter.applyAsInt(minX + nx, minZ + nz);
                    if (after < groundBefore.applyAsInt(minX + nx, minZ + nz) && after < level[index]) {
                        level[index] = after;
                        capped = true;
                    }
                }
            }
            return capped;
        }

        private void groupFlatRuns() {
            List<Integer> ordered = new ArrayList<>(streams);
            ordered.sort(Comparator.comparingInt((Integer index) -> surface[index]));
            boolean[] grouped = new boolean[surface.length];
            for (int index : ordered) {
                if (grouped[index]) {
                    continue;
                }
                List<Integer> run = new ArrayList<>();
                ArrayDeque<Integer> open = new ArrayDeque<>();
                open.add(index);
                grouped[index] = true;
                while (!open.isEmpty()) {
                    int current = open.poll();
                    run.add(current);
                    for (int neighbour : links(current)) {
                        if (!fixed[neighbour] && surface[neighbour] == surface[index] && !grouped[neighbour]) {
                            grouped[neighbour] = true;
                            open.add(neighbour);
                        }
                    }
                }
                flatRuns.add(run);
            }
        }

        private List<Integer> links(int index) {
            int x = index % width;
            int z = index / width;
            List<Integer> links = new ArrayList<>();
            for (int nz = Math.max(0, z - LINK); nz <= Math.min(depth - 1, z + LINK); nz++) {
                for (int nx = Math.max(0, x - LINK); nx <= Math.min(width - 1, x + LINK); nx++) {
                    int neighbour = nz * width + nx;
                    if (neighbour != index && surface[neighbour] != DRY) {
                        links.add(neighbour);
                    }
                }
            }
            return links;
        }

        private void raiseFloorsUpstream() {
            for (List<Integer> run : flatRuns) {
                int runSurface = surface[run.get(0)];
                int highest = Math.max(runSurface - MAX_DROP, seaTop == Integer.MIN_VALUE ? Integer.MIN_VALUE : seaTop + 1);
                for (int member : run) {
                    for (int neighbour : links(member)) {
                        if (fixed[neighbour] && surface[neighbour] <= runSurface) {
                            highest = Math.max(highest, surface[neighbour]);
                        } else if (!fixed[neighbour] && surface[neighbour] < runSurface) {
                            highest = Math.max(highest, floor[neighbour]);
                        }
                    }
                }
                for (int member : run) {
                    floor[member] = highest;
                }
            }
        }

        private void holdDownstream() {
            for (int position = flatRuns.size() - 1; position >= 0; position--) {
                List<Integer> run = flatRuns.get(position);
                int runSurface = surface[run.get(0)];
                int lowest = runSurface;
                for (int member : run) {
                    lowest = Math.min(lowest, level[member]);
                    for (int neighbour : links(member)) {
                        if (!fixed[neighbour] && surface[neighbour] > runSurface) {
                            lowest = Math.min(lowest, level[neighbour]);
                        }
                    }
                }
                int settled = Math.max(lowest, floor[run.get(0)]);
                for (int member : run) {
                    level[member] = settled;
                }
            }
        }

        private Map<Long, Integer> loweredLevels() {
            Map<Long, Integer> lowered = new HashMap<>();
            for (int index : streams) {
                if (level[index] < surface[index]) {
                    lowered.put(columnKey(minX + index % width, minZ + index / width), level[index]);
                }
            }
            return lowered.isEmpty() ? Map.of() : Map.copyOf(lowered);
        }
    }

    public static final class ChunkView {
        public static final ChunkView DRY = new ChunkView(0, 0, 0, 0, new StructureWaterColumn[0], new int[0]);

        private final int minX;
        private final int minZ;
        private final int width;
        private final int depth;
        private final StructureWaterColumn[] columns;
        private final int[] levels;

        private ChunkView(int minX, int minZ, int width, int depth, StructureWaterColumn[] columns, int[] levels) {
            this.minX = minX;
            this.minZ = minZ;
            this.width = width;
            this.depth = depth;
            this.columns = columns;
            this.levels = levels;
        }

        public static ChunkView of(StructureWater water, List<Map<Long, Integer>> plans,
                                   int areaMinX, int areaMinZ, int areaMaxX, int areaMaxZ) {
            int bank = StructureWaterRules.BANK;
            int minX = areaMinX - bank;
            int minZ = areaMinZ - bank;
            int width = areaMaxX - areaMinX + 1 + bank * 2;
            int depth = areaMaxZ - areaMinZ + 1 + bank * 2;
            StructureWaterColumn[] columns = new StructureWaterColumn[width * depth];
            int[] levels = new int[width * depth];
            Arrays.fill(levels, Integer.MIN_VALUE);
            boolean any = false;
            for (int z = 0; z < depth; z++) {
                for (int x = 0; x < width; x++) {
                    int index = z * width + x;
                    StructureWaterColumn column = water.column(minX + x, minZ + z);
                    columns[index] = column;
                    if (!holdsWater(column)) {
                        continue;
                    }
                    any = true;
                    int level = column.surface();
                    long key = columnKey(minX + x, minZ + z);
                    for (Map<Long, Integer> plan : plans) {
                        Integer lowered = plan.get(key);
                        if (lowered != null) {
                            level = Math.min(level, lowered);
                        }
                    }
                    levels[index] = level;
                }
            }
            return any ? new ChunkView(minX, minZ, width, depth, columns, levels) : DRY;
        }

        public StructureWaterColumn column(int x, int z) {
            int index = index(x, z);
            return index < 0 ? StructureWaterColumn.DRY : columns[index];
        }

        public int level(int x, int z) {
            int index = index(x, z);
            return index < 0 ? Integer.MIN_VALUE : levels[index];
        }

        public boolean settles(int x, int z) {
            if (lowered(x, z)) {
                return true;
            }
            for (int[] direction : CARDINALS) {
                if (lowered(x + direction[0], z + direction[1])) {
                    return true;
                }
            }
            return false;
        }

        public int highestNeighbourLevel(int x, int z) {
            int highest = Integer.MIN_VALUE;
            for (int[] direction : CARDINALS) {
                highest = Math.max(highest, level(x + direction[0], z + direction[1]));
            }
            return highest;
        }

        private boolean lowered(int x, int z) {
            int index = index(x, z);
            return index >= 0 && levels[index] != Integer.MIN_VALUE && levels[index] < columns[index].surface();
        }

        public int keepBank(int x, int z, int groundBefore, int groundAfter) {
            if (groundAfter >= groundBefore) {
                return groundAfter;
            }
            int highest = Integer.MIN_VALUE;
            int bank = StructureWaterRules.BANK;
            for (int dz = -bank; dz <= bank; dz++) {
                for (int dx = -bank; dx <= bank; dx++) {
                    highest = Math.max(highest, level(x + dx, z + dz));
                }
            }
            return Math.max(groundAfter, Math.min(groundBefore, highest));
        }

        private int index(int x, int z) {
            int localX = x - minX;
            int localZ = z - minZ;
            return localX < 0 || localZ < 0 || localX >= width || localZ >= depth ? -1 : localZ * width + localX;
        }
    }
}

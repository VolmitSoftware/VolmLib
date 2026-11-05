package art.arcane.volmlib.nativelib.terrain.structure;

import org.junit.Test;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.IntBinaryOperator;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StructureStreamLoweringFuzzTest {
    record Net(Map<Long, int[]> cells, List<int[]> edges) {}
    static long k(int x, int z) { return StructureStreamLowering.columnKey(x, z); }
    static Net net(Random r, int maxGap) {
        Map<Long, int[]> cells = new HashMap<>(); List<int[]> edges = new ArrayList<>();
        ArrayDeque<int[]> heads = new ArrayDeque<>();
        heads.add(new int[]{-200 + r.nextInt(30), -40 + r.nextInt(80), 400}); heads.add(new int[]{-180 + r.nextInt(30), -80 + r.nextInt(30), 410});
        int[] trunk = null;
        for (int[] h : heads) {
            int x = h[0], z = h[1], s = h[2]; int[] prev = null; int stepAt = 0;
            for (int i = 0; i < 400 && x < 200; i++) {
                int[] cur = cells.get(k(x, z));
                if (cur != null) { if (prev != null && cur[2] <= prev[2]) edges.add(new int[]{prev[0], prev[1], cur[0], cur[1]}); break; }
                cur = new int[]{x, z, s};
                cells.put(k(x, z), cur);
                if (prev != null) edges.add(new int[]{prev[0], prev[1], x, z});
                prev = cur;
                int gap = maxGap > 0 && r.nextInt(6) == 0 ? 1 + r.nextInt(maxGap) : 0;
                x += 1 + gap; z += r.nextInt(3) - 1;
                if (--stepAt < 0) { s -= 2; stepAt = r.nextInt(30); }
            }
        }
        return new Net(cells, edges);
    }
    static StructureWater water(Net n) { return (x, z) -> { int[] c = n.cells().get(k(x, z)); return c == null ? StructureWaterColumn.DRY : new StructureWaterColumn(true, true, c[2] - 1, c[2]); }; }
    static IntBinaryOperator ground(Net n) { return (x, z) -> { int best = 500; for (int d = -6; d <= 6; d++) for (int e = -6; e <= 6; e++) { int[] c = n.cells().get(k(x + d, z + e)); if (c != null) best = Math.min(best, c[2] + 1 + Math.max(Math.abs(d), Math.abs(e)) / 2); } return best; }; }
    static IntBinaryOperator bowl(IntBinaryOperator g, int cx, int cz, int rad, int cut) { return (x, z) -> { double d = Math.hypot(x - cx, z - cz); if (d >= rad) return g.applyAsInt(x, z); double p = d / rad; return g.applyAsInt(x, z) - (int) Math.round(cut * (1 - p * p * (3 - 2 * p))); }; }

    void check(int seed, int maxGap, int[] out) {
        Random r = new Random(seed);
        Net n = net(r, maxGap); StructureWater w = water(n); IntBinaryOperator g = ground(n);
        int starts = 1 + r.nextInt(3); List<Map<Long, Integer>> plans = new ArrayList<>(); List<IntBinaryOperator> afters = new ArrayList<>();
        for (int s = 0; s < starts; s++) {
            int cx = -120 + r.nextInt(240), cz = -40 + r.nextInt(80), half = 60 + r.nextInt(70);
            IntBinaryOperator a = bowl(g, cx, cz, 16 + r.nextInt(16), 2 + r.nextInt(14)); afters.add(a);
            plans.add(StructureStreamLowering.plan(w, g, a, Integer.MIN_VALUE, cx - half, cz - half, cx + half, cz + half));
        }
        Map<Long, Integer> global = new HashMap<>();
        for (long key : n.cells().keySet()) { int[] c = n.cells().get(key); int l = c[2]; for (Map<Long, Integer> p : plans) { Integer v = p.get(key); if (v != null) l = Math.min(l, v); } global.put(key, l); if (l < c[2]) out[1]++; }
        for (int[] e : n.edges()) { int up = global.get(k(e[0], e[1])), dn = global.get(k(e[2], e[3])); if (dn > up) { out[0]++; } }
        for (int cx = -14; cx <= 13; cx++) for (int cz = -6; cz <= 5; cz++) {
            StructureStreamLowering.ChunkView v = StructureStreamLowering.ChunkView.of(w, plans, cx << 4, cz << 4, (cx << 4) + 15, (cz << 4) + 15);
            for (int x = (cx << 4) - 2; x <= (cx << 4) + 17; x++) for (int z = (cz << 4) - 2; z <= (cz << 4) + 17; z++) {
                int[] c = n.cells().get(k(x, z));
                if (c != null && v != StructureStreamLowering.ChunkView.DRY) assertEquals(global.get(k(x, z)).intValue(), v.level(x, z));
                if (c == null && v != StructureStreamLowering.ChunkView.DRY && x >= cx << 4 && x <= (cx << 4) + 15 && z >= cz << 4 && z <= (cz << 4) + 15) {
                    int before = g.applyAsInt(x, z); int actual = before; for (IntBinaryOperator a : afters) actual = Math.min(actual, a.applyAsInt(x, z));
                    int kept = v.keepBank(x, z, before, actual);
                    for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) { Integer l = global.get(k(x + d[0], z + d[1])); if (l != null && l > kept && kept < before) { out[2]++; } }
                }
            }
        }
    }
    @Test public void fuzzWithinLinkRadius() { int[] o = new int[3]; for (int s = 0; s < 300; s++) { check(s, 1, o); } assertEquals(0, o[0]); assertEquals(0, o[2]); assertTrue(o[1] > 1000); }
}

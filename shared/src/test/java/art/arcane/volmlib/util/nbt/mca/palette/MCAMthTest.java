package art.arcane.volmlib.util.nbt.mca.palette;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MCAMthTest {
    @Test
    public void preservesPaletteBitCountsAndCoordinateClamping() {
        int[][] samples = {{1, 0, 1}, {2, 1, 2}, {3, 2, 4}, {15, 4, 16}, {16, 4, 16},
                {17, 5, 32}, {255, 8, 256}, {256, 8, 256}, {257, 9, 512}, {30000000, 25, 33554432}};
        for (int[] sample : samples) {
            assertEquals(sample[1], MCAMth.ceillog2(sample[0]));
            assertEquals(sample[2], MCAMth.smallestEncompassingPowerOfTwo(sample[0]));
        }
        assertEquals(-4, MCAMth.clamp(Integer.MIN_VALUE, -4, 19));
        assertEquals(-4, MCAMth.clamp(-4, -4, 19));
        assertEquals(0, MCAMth.clamp(0, -4, 19));
        assertEquals(19, MCAMth.clamp(19, -4, 19));
        assertEquals(19, MCAMth.clamp(Integer.MAX_VALUE, -4, 19));
    }

    @Test
    public void preservesExactPaletteHashes() {
        int[][] samples = {
                {Integer.MIN_VALUE, 1832674720}, {-1, -2114883783}, {0, 0}, {1, 1364076727},
                {15, -870655931}, {16, 1428509628}, {17, -657416472}, {Integer.MAX_VALUE, -104067416}
        };
        for (int[] sample : samples) {
            assertEquals(sample[1], MCAMth.murmurHash3Mixer(sample[0]));
        }
    }
}

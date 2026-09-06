package art.arcane.volmlib.util.interpolation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class StarcastTest {
    @Test
    public void preservesExactKernelResultsAcrossSampleCountsAndFloatBoundaries() {
        int[] checks = {1, 3, 6, 9, 12, 128};
        int[][] positions = {{-31, 47}, {16777217, -16777219}};
        long[][] expectedBits = {
                {0xc01a637000000000L, 0xc020788000000000L, 0xc020788000000000L,
                        0xc020788020000000L, 0xc020788000000000L, 0xc027297420000000L},
                {0xc24fffe8a0000000L, 0xc24fffe8c0000000L, 0xc24fffe880000000L,
                        0xc24fffe8a0000000L, 0xc24fffe880000000L, 0xc2567fede0000000L}
        };

        for (int position = 0; position < positions.length; position++) {
            for (int sample = 0; sample < checks.length; sample++) {
                int x = positions[position][0];
                int z = positions[position][1];
                assertEquals(expectedBits[position][sample], Double.doubleToRawLongBits(
                        Starcast.starcast(x, z, 17.25, checks[sample], StarcastTest::noise)));
                assertEquals(expectedBits[position][sample], Double.doubleToRawLongBits(
                        Starcast.starcast(x, z, 17.25, checks[sample], false, StarcastTest::noise)));
            }
        }
    }

    @Test
    public void rejectsMissingNoiseProviderWhenSampling() {
        assertThrows(NullPointerException.class, () -> Starcast.starcast(0, 0, 1, 3, null));
    }

    private static double noise(double x, double z) {
        return x * 0.125 - z * 0.0625 + x * z * 0.0009765625;
    }
}

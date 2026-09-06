package art.arcane.volmlib.util.math;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;

public class BlockPositionTest {
    @Test
    public void preservesPackedCoordinatesAtWorldAndBitBoundaries() {
        long[][] samples = {
                {0, 0, 0, 0x0000000000000000L},
                {1, 1, 1, 0x0000004000001001L},
                {-1, -1, -1, 0xffffffffffffffffL},
                {33554431, 2047, 33554431, 0x7fffffdffffff7ffL},
                {-33554432, -2048, -33554432, 0x8000002000000800L},
                {30000000, 319, -30000000, 0x7270e02363c8013fL},
                {-30000000, -64, 30000000, 0x8d8f201c9c380fc0L},
                {33554432, 2048, 33554432, 0x8000002000000800L},
                {-33554433, -2049, -33554433, 0x7fffffdffffff7ffL},
                {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, 0xffffffc000000fffL},
                {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, 0x0000003ffffff000L}
        };

        for (long[] sample : samples) {
            assertEquals(sample[3], BlockPosition.toLong((int) sample[0], (int) sample[1], (int) sample[2]));
        }
    }

    @Test
    public void roundTripsSignedCoordinatesWithoutOverlappingAxes() {
        Random random = new Random(812048L);
        for (int sample = 0; sample < 10000; sample++) {
            int x = random.nextInt(1 << 26) - (1 << 25);
            int y = random.nextInt(1 << 12) - (1 << 11);
            int z = random.nextInt(1 << 26) - (1 << 25);
            long packed = new BlockPosition(x, y, z).asLong();

            assertEquals(x, (int) (packed >> 38));
            assertEquals(y, (int) (packed << 52 >> 52));
            assertEquals(z, (int) (packed << 26 >> 38));
        }
    }
}

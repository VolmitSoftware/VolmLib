package art.arcane.volmlib.util.data;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;

public class WeightedRandomTest {
    @Test
    public void everyEqualWeightBucketIsReachable() {
        WeightedRandom<String> weighted = new WeightedRandom<>();
        weighted.put("first", 1);
        weighted.put("second", 1);

        assertEquals("first", weighted.pullRandom(new FixedRandom(0)));
        assertEquals("second", weighted.pullRandom(new FixedRandom(1)));
    }

    @Test
    public void weightedBoundariesSelectTheCorrectBucket() {
        WeightedRandom<String> weighted = new WeightedRandom<>();
        weighted.put("first", 2);
        weighted.put("second", 3);

        assertEquals("first", weighted.pullRandom(new FixedRandom(0)));
        assertEquals("first", weighted.pullRandom(new FixedRandom(1)));
        assertEquals("second", weighted.pullRandom(new FixedRandom(2)));
        assertEquals("second", weighted.pullRandom(new FixedRandom(4)));
    }

    private static final class FixedRandom extends Random {
        private final int value;

        private FixedRandom(int value) {
            this.value = value;
        }

        @Override
        public int nextInt(int bound) {
            if (value < 0 || value >= bound) {
                throw new IllegalArgumentException("Fixed value outside bound");
            }
            return value;
        }
    }
}

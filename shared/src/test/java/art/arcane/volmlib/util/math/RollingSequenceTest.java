package art.arcane.volmlib.util.math;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RollingSequenceTest {
    @Test
    public void averageReturnsExactZeroOnceTheWindowHoldsOnlyZeros() {
        RollingSequence sequence = new RollingSequence(4);
        for (double value : new double[]{0.1D, 0.7D, 0.3D, 1.9D, 0.05D, 2.2D, 0.6D}) {
            sequence.put(value);
        }
        for (int index = 0; index < 4; index++) {
            sequence.put(0D);
        }

        assertEquals(Double.doubleToLongBits(0D), Double.doubleToLongBits(sequence.getAverage()));
    }

    @Test
    public void averageFollowsTheWindowAfterItReturnsToZero() {
        RollingSequence sequence = new RollingSequence(4);
        for (double value : new double[]{0.1D, 0.7D, 0.3D, 1.9D, 0D, 0D, 0D, 0D, 2D}) {
            sequence.put(value);
        }

        assertEquals(0.5D, sequence.getAverage(), 0D);
    }

    @Test
    public void averageStartsFromTheFirstValue() {
        RollingSequence sequence = new RollingSequence(5);
        sequence.put(3D);

        assertEquals(3D, sequence.getAverage(), 0D);
        sequence.put(0D);
        assertEquals(2.4D, sequence.getAverage(), 1e-12D);
    }
}

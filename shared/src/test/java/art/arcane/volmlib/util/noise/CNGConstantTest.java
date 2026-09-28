package art.arcane.volmlib.util.noise;

import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CNGConstantTest {
    @Test
    public void flatFieldWithFracturePowerAndZoomIsConstantAtEveryCoordinate() {
        CNG flat = new CNG(new RNG(9L), NoiseType.FLAT, 0.75D, 3)
                .bake()
                .pow(1.7D)
                .fractureWith(new CNG(new RNG(4L), NoiseType.SIMPLEX, 1D, 1), 12D)
                .zoom(3.5D)
                .up(0.1D)
                .down(0.05D)
                .patch(0.9D);

        assertTrue(flat.isConstant());
        double constant = flat.noise(0D, 0D, 0D);
        for (int index = -200; index <= 200; index++) {
            double x = index * 13.37D;
            double y = index * -0.61D + 300D;
            double z = index * 7.29D - 11D;
            assertEquals(Double.doubleToRawLongBits(constant), Double.doubleToRawLongBits(flat.noise(x, y, z)));
            assertEquals(Double.doubleToRawLongBits(constant), Double.doubleToRawLongBits(flat.noise((int) x, (int) y, (int) z)));
        }
    }

    @Test
    public void coordinateDependentFieldsAreNotConstant() {
        assertFalse(new CNG(new RNG(9L), NoiseType.SIMPLEX, 1D, 1).isConstant());
        assertFalse(new CNG(new RNG(9L), NoiseType.WHITE, 1D, 1).isConstant());
        assertFalse(new CNG(new RNG(9L), NoiseType.FLAT, 1D, 1)
                .child(new CNG(new RNG(2L), NoiseType.SIMPLEX, 1D, 1))
                .isConstant());
        assertFalse(new CNG(new RNG(9L), NoiseType.FLAT, 1D, 1) {
            @Override
            public double noise(double x, double y, double z) {
                return x;
            }
        }.isConstant());
    }
}

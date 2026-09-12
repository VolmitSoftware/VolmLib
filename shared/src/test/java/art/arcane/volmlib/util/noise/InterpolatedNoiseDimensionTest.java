package art.arcane.volmlib.util.noise;

import art.arcane.volmlib.util.interpolation.InterpolationMethod;
import art.arcane.volmlib.util.interpolation.IrisInterpolation;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class InterpolatedNoiseDimensionTest {
    @Test
    public void threeDimensionalSamplesAlwaysUseTheHorizontalPlane() {
        InterpolatedNoise noise = new InterpolatedNoise(37L, NoiseType.WHITE, InterpolationMethod.BILINEAR);

        for (double x : new double[]{-13D, 0D, 41D}) {
            for (double y : new double[]{-89D, 0D, 173D}) {
                for (double z : new double[]{-37D, 0D, 29D}) {
                    assertEquals(noise.noise(x, z), noise.noise(x, y, z), 0D);
                }
            }
            assertEquals(noise.noise(x, 0D), noise.noise(x), 0D);
        }
    }

    @Test
    public void interpolationSamplesTheNormalizedSourceAndForwardsOctaves() {
        long seed = 93L;
        NoiseType type = NoiseType.SIMPLEX;
        NoiseGenerator source = type.create(seed);
        ((OctaveNoise) source).setOctaves(4);
        InterpolatedNoise noise = new InterpolatedNoise(seed, type, InterpolationMethod.BILINEAR);
        noise.setOctaves(4);
        double scale = type.getCoordinateScale();

        for (int x = -49; x <= 49; x += 7) {
            for (int z = -51; z <= 51; z += 11) {
                double expected = IrisInterpolation.getNoise(InterpolationMethod.BILINEAR, x, z, 32D,
                        (sampleX, sampleZ) -> source.noise(sampleX * scale, sampleZ * scale));
                assertEquals(expected, noise.noise(x, z), 1.0E-12D);
            }
        }
    }

    @Test
    public void fractionalCoordinatesReachEveryInterpolationKernel() {
        long seed = 109L;
        NoiseType type = NoiseType.SIMPLEX;
        NoiseGenerator source = type.create(seed);
        double scale = type.getCoordinateScale();

        for (InterpolationMethod method : InterpolationMethod.values()) {
            InterpolatedNoise noise = new InterpolatedNoise(seed, type, method);
            double expected = IrisInterpolation.getNoise(method, -7.75D, 3.25D, 32D,
                    (x, z) -> source.noise(x * scale, z * scale));
            double actual = noise.noise(-7.75D, 3.25D);

            assertEquals(method.name(), expected, actual, 0D);
            assertNotEquals(method.name(), noise.noise(-7D, 3D), actual, 1.0E-9D);
        }
    }

    @Test
    public void noiseWrappersConstrainCubicOvershootWithoutConstrainingTheKernel() {
        double overshoot = IrisInterpolation.getNoise(InterpolationMethod.BICUBIC, 16D, 16D, 32D,
                (x, z) -> x >= 0D && x <= 32D ? 1D : 0D);
        assertEquals(1.25D, overshoot, 0D);

        for (NoiseType type : new NoiseType[]{NoiseType.WHITE, NoiseType.CELLULAR, NoiseType.CLOVER}) {
            for (InterpolationMethod method : new InterpolationMethod[]{InterpolationMethod.BICUBIC,
                    InterpolationMethod.HERMITE}) {
                InterpolatedNoise noise = new InterpolatedNoise(173L, type, method);
                boolean reachesEndpoint = false;
                for (double x = -256D; x <= 256D; x += 7.375D) {
                    for (double z = -256D; z <= 256D; z += 5.5D) {
                        double value = noise.noise(x, z);
                        assertTrue(type.name() + "/" + method.name(), value >= 0D && value <= 1D);
                        reachesEndpoint |= value == 0D || value == 1D;
                    }
                }
                if (type == NoiseType.WHITE) {
                    assertTrue(method.name(), reachesEndpoint);
                }
            }
        }
    }
}

package art.arcane.volmlib.util.noise;

import art.arcane.volmlib.util.interpolation.InterpolationMethod;
import art.arcane.volmlib.util.interpolation.IrisInterpolation;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class InterpolatedNoiseMemoTest {
    @Test
    public void memoizedLatticeSamplesMatchFreshSamplesForEveryMethod() {
        NoiseType[] types = {NoiseType.CLOVER, NoiseType.CELLULAR, NoiseType.WHITE, NoiseType.HEXAGON};
        for (NoiseType type : types) {
            for (InterpolationMethod method : InterpolationMethod.values()) {
                long seed = 7919L * type.ordinal() + method.ordinal();
                InterpolatedNoise noise = new InterpolatedNoise(seed, type, method);
                NoiseGenerator source = type.create(seed);
                double scale = type.getCoordinateScale();
                for (int z = -70; z <= 70; z += 3) {
                    for (int x = -70; x <= 70; x += 5) {
                        double expected = Math.max(0D, Math.min(1D, IrisInterpolation.getNoise(method, x, z, 32D,
                                (sampleX, sampleZ) -> source.noise(sampleX * scale, sampleZ * scale))));
                        assertEquals(type + "/" + method + " at " + x + "," + z, expected, noise.noise(x, z), 0D);
                    }
                }
            }
        }
    }

    @Test
    public void changingOctavesRetiresMemoizedSamples() {
        long seed = 211L;
        NoiseType type = NoiseType.SIMPLEX;
        InterpolatedNoise noise = new InterpolatedNoise(seed, type, InterpolationMethod.HERMITE);
        double scale = type.getCoordinateScale();
        for (int x = 0; x < 64; x += 4) {
            noise.noise(x, 9D);
        }
        noise.setOctaves(3);
        NoiseGenerator source = type.create(seed);
        ((OctaveNoise) source).setOctaves(3);
        for (int x = 0; x < 64; x += 4) {
            double expected = Math.max(0D, Math.min(1D, IrisInterpolation.getNoise(InterpolationMethod.HERMITE, x, 9D,
                    32D, (sampleX, sampleZ) -> source.noise(sampleX * scale, sampleZ * scale))));
            assertEquals(expected, noise.noise(x, 9D), 0D);
        }
    }
}

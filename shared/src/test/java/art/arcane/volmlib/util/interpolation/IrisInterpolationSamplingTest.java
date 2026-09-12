package art.arcane.volmlib.util.interpolation;

import art.arcane.volmlib.util.function.NoiseProvider;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class IrisInterpolationSamplingTest {
    @Test
    public void bicubicDispatchUsesSixteenSamplesAndTheCubicKernel() {
        NoiseProvider source = (x, z) -> x * x + z * z * z;
        AtomicInteger calls = new AtomicInteger();
        NoiseProvider countingSource = (x, z) -> {
            calls.incrementAndGet();
            return source.noise(x, z);
        };
        double expected = IrisInterpolation.getBicubicNoise(3, 5, 8D, source);
        double bilinear = IrisInterpolation.getBilinearNoise(3, 5, 8D, source);
        double actual = IrisInterpolation.getNoise(InterpolationMethod.BICUBIC, 3, 5, 8D, countingSource);

        assertEquals(expected, actual, 0D);
        assertNotEquals(bilinear, actual, 1.0E-12D);
        assertEquals(16, calls.get());
    }

    @Test
    public void bilinearSamplingPreservesFractionalAndNegativeCoordinates() {
        NoiseProvider source = (x, z) -> x * 0.001D + z * 0.002D;

        for (double x : new double[]{-31.75D, -0.25D, 0D, 0.75D, 31.25D, 29_999_999.25D}) {
            for (double z : new double[]{-33.5D, -0.5D, 0.5D, 23.75D}) {
                assertEquals(source.noise(x, z),
                        IrisInterpolation.getNoise(InterpolationMethod.BILINEAR, x, z, 32D, source), 1.0E-11D);
            }
        }

        assertEquals(-1, IrisInterpolation.getRadiusFactor(-0.25D, 2.5D));
        assertEquals(0, IrisInterpolation.getRadiusFactor(2.25D, 2.5D));
        assertEquals(1, IrisInterpolation.getRadiusFactor(2.75D, 2.5D));
    }

    @Test
    public void starcastPreservesFractionalCentersNearTheWorldBorder() {
        double x = 30_000_000.25D;
        double z = -29_999_999.75D;
        List<Coordinate> samples = new ArrayList<>();
        IrisInterpolation.getNoise(InterpolationMethod.STARCAST_3, x, z, 8D, (sampleX, sampleZ) -> {
            samples.add(new Coordinate(sampleX, sampleZ));
            return 0.5D;
        });

        assertEquals(3, samples.size());
        for (int index = 0; index < samples.size(); index++) {
            double angle = index * Math.PI * 2D / 3D;
            double dx = 8D * (Math.cos(angle) - Math.sin(angle));
            double dz = 8D * (Math.sin(angle) + Math.cos(angle));
            assertEquals(x + dx, samples.get(index).x, 1.0E-12D);
            assertEquals(z + dz, samples.get(index).z, 1.0E-12D);
        }
    }

    @Test
    public void compositeSampleCachesKeepFractionalCentersSeparate() {
        NoiseProvider source = (x, z) -> Math.sin(x * 0.013D) + Math.cos(z * 0.021D);

        for (double x : new double[]{-31.75D, -0.25D, 0.25D, 31.75D}) {
            double z = x + 7.5D;
            Map<Coordinate, Integer> samples = new HashMap<>();
            double expected = IrisInterpolation.getNoise(InterpolationMethod.STARCAST_12, x, z, 32D,
                    (sampleX, sampleZ) -> IrisInterpolation.getBilinearNoise(sampleX, sampleZ, 32D, source));
            double actual = IrisInterpolation.getNoise(InterpolationMethod.BILINEAR_STARCAST_12,
                    x, z, 32D, (sampleX, sampleZ) -> {
                        samples.merge(new Coordinate(sampleX, sampleZ), 1, Integer::sum);
                        return source.noise(sampleX, sampleZ);
                    });

            assertEquals(expected, actual, 0D);
            for (int calls : samples.values()) {
                assertEquals(1, calls);
            }
        }
    }

    @Test
    public void fractionalGridSpacingDoesNotCollapseOrJumpAtCellBoundaries() {
        NoiseProvider source = (x, z) -> Math.sin(x * 0.73D) * 0.4D + Math.cos(z * 0.59D) * 0.2D;

        for (InterpolationMethod method : InterpolationMethod.values()) {
            for (double radius : new double[]{0.125D, 0.375D, 2.5D, 7.75D}) {
                for (int cell : new int[]{-7, -1, 0, 1, 7}) {
                    double boundary = cell * radius;
                    double center = IrisInterpolation.getNoise(method, boundary, 0.37D, radius, source);
                    double before = IrisInterpolation.getNoise(method, boundary - 1.0E-8D, 0.37D, radius, source);
                    double after = IrisInterpolation.getNoise(method, boundary + 1.0E-8D, 0.37D, radius, source);

                    assertTrue(method.name(), Double.isFinite(center));
                    assertEquals(method.name(), center, before, 1.0E-6D);
                    assertEquals(method.name(), center, after, 1.0E-6D);
                }
            }
        }
    }

    @Test
    public void gridSamplesKeepTheConfiguredFractionalSpacing() {
        List<Coordinate> samples = new ArrayList<>();
        IrisInterpolation.getBilinearNoise(-0.25D, 0.125D, 0.375D, (x, z) -> {
            samples.add(new Coordinate(x, z));
            return x + z;
        });

        assertEquals(List.of(new Coordinate(-0.375D, 0D), new Coordinate(0D, 0D),
                new Coordinate(-0.375D, 0.375D), new Coordinate(0D, 0.375D)), samples);
    }

    @Test
    public void starcastPreservesDoubleSamplePrecisionAndLinearFields() {
        NoiseProvider source = (x, z) -> 0.125D + x * 1.0E-11D - z * 2.0E-11D;

        for (InterpolationMethod method : new InterpolationMethod[]{InterpolationMethod.STARCAST_3,
                InterpolationMethod.STARCAST_6, InterpolationMethod.STARCAST_9, InterpolationMethod.STARCAST_12}) {
            assertEquals(method.name(), source.noise(0.25D, -0.75D),
                    IrisInterpolation.getNoise(method, 0.25D, -0.75D, 32.00000001D, source), 1.0E-16D);
            assertNotEquals(method.name(),
                    IrisInterpolation.getNoise(method, 0D, 0D, 32D, source),
                    IrisInterpolation.getNoise(method, 0.25D, -0.75D, 32D, source), 1.0E-13D);
        }
    }

    @Test
    public void parametricCurvesHaveSymmetricMidpointsAndMonotonicEndpoints() {
        for (double alpha : new double[]{1D, 1.5D, 2D, 4D}) {
            assertEquals(0D, IrisInterpolation.parametric(0D, alpha), 0D);
            assertEquals(0.5D, IrisInterpolation.parametric(0.5D, alpha), 0D);
            assertEquals(1D, IrisInterpolation.parametric(1D, alpha), 0D);
            double previous = 0D;
            for (int sample = 1; sample <= 100; sample++) {
                double t = sample / 100D;
                double value = IrisInterpolation.parametric(t, alpha);
                assertTrue(value >= previous);
                assertTrue(value <= 1D);
                assertEquals(value, 1D - IrisInterpolation.parametric(1D - t, alpha), 1.0E-15D);
                previous = value;
            }
        }
    }

    @Test
    public void catmullRomKeepsLinearSlopesAcrossGridBoundaries() {
        NoiseProvider source = (x, z) -> x * 0.7D - z * 0.3D;
        for (double x : new double[]{-7.75D, -0.25D, 0.25D, 7.75D}) {
            assertEquals(source.noise(x, 2.75D),
                    IrisInterpolation.getNoise(InterpolationMethod.CATMULL_ROM_SPLINE, x, 2.75D, 8D, source),
                    1.0E-12D);
        }
    }

    private record Coordinate(double x, double z) {
    }
}

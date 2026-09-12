package art.arcane.volmlib.util.interpolation;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class IrisInterpolationBoundsTest {
    @Test
    public void everyMethodPreservesSeparatePassValuesAndSamplesEachCoordinateOnce() {
        Random random = new Random(19053);
        for (InterpolationMethod method : InterpolationMethod.values()) {
            for (double scale : new double[]{1D, 7D, 12D, 23D, 200D, 7.75D, 8192D}) {
                for (int index = 0; index < 24; index++) {
                    int x = random.nextInt();
                    int z = random.nextInt();
                    Map<Coordinate, NoiseBounds> expectedSamples = new HashMap<>();
                    NoiseBounds expected = separatePasses(method, x, z, scale,
                            IrisInterpolationBoundsTest::asymmetricBounds, expectedSamples);
                    Map<Coordinate, Integer> actualSamples = new HashMap<>();
                    NoiseBounds actual = IrisInterpolation.getNoiseBounds(method, x, z, scale, (sx, sz) -> {
                        actualSamples.merge(new Coordinate(sx, sz), 1, Integer::sum);
                        return asymmetricBounds(sx, sz);
                    });
                    assertBounds(expected, actual);
                    assertEquals(expectedSamples.keySet(), actualSamples.keySet());
                    for (int calls : actualSamples.values()) {
                        assertEquals(1, calls);
                    }
                }
            }
        }
    }

    @Test
    public void fractionalBoundsMatchSeparatePassesAndSampleEachCoordinateOnce() {
        for (InterpolationMethod method : InterpolationMethod.values()) {
            Map<Coordinate, NoiseBounds> expectedSamples = new HashMap<>();
            NoiseBounds expected = separatePasses(method, -31.75D, 29.25D, 32D,
                    IrisInterpolationBoundsTest::asymmetricBounds, expectedSamples);
            Map<Coordinate, Integer> actualSamples = new HashMap<>();
            NoiseBounds actual = IrisInterpolation.getNoiseBounds(method, -31.75D, 29.25D, 32D, (x, z) -> {
                actualSamples.merge(new Coordinate(x, z), 1, Integer::sum);
                return asymmetricBounds(x, z);
            });

            assertBounds(expected, actual);
            assertEquals(expectedSamples.keySet(), actualSamples.keySet());
            for (int calls : actualSamples.values()) {
                assertEquals(1, calls);
            }
        }
    }

    @Test
    public void nestedProvidersKeepTheirOwnSamplingSequences() {
        for (InterpolationMethod method : InterpolationMethod.values()) {
            NoiseBoundsProvider expectedProvider = (x, z) -> separatePasses(
                    InterpolationMethod.HERMITE_STARCAST_12, (int) x + 103, (int) z - 79, 13.5D,
                    IrisInterpolationBoundsTest::asymmetricBounds, new HashMap<>());
            NoiseBoundsProvider actualProvider = (x, z) -> IrisInterpolation.getNoiseBounds(
                    InterpolationMethod.HERMITE_STARCAST_12, (int) x + 103, (int) z - 79, 13.5D,
                    IrisInterpolationBoundsTest::asymmetricBounds);
            NoiseBounds expected = separatePasses(method, -93, 201, 23D, expectedProvider, new HashMap<>());
            NoiseBounds actual = IrisInterpolation.getNoiseBounds(method, -93, 201, 23D, actualProvider);
            assertBounds(expected, actual);
        }
    }

    @Test
    public void nestedScalarProvidersPreserveTheirOriginalCallbackOrder() {
        for (InterpolationMethod method : InterpolationMethod.values()) {
            AtomicInteger expectedCalls = new AtomicInteger();
            AtomicInteger actualCalls = new AtomicInteger();
            NoiseBoundsProvider expectedProvider = (x, z) -> scalarBounds(x, z, expectedCalls);
            NoiseBoundsProvider actualProvider = (x, z) -> scalarBounds(x, z, actualCalls);
            NoiseBounds expected = separatePasses(method, -93, 201, 23D, expectedProvider, new HashMap<>());
            NoiseBounds actual = IrisInterpolation.getNoiseBounds(method, -93, 201, 23D, actualProvider);
            assertBounds(expected, actual);
            assertEquals(expectedCalls.get(), actualCalls.get());
        }
    }

    private static NoiseBounds scalarBounds(double x, double z, AtomicInteger calls) {
        double value = IrisInterpolation.getNoise(InterpolationMethod.HERMITE_STARCAST_12,
                (int) x + 103, (int) z - 79, 13.5D, (sx, sz) -> calls.incrementAndGet());
        return new NoiseBounds(value, value + 17D);
    }

    @Test
    public void failedProviderDoesNotLeavePartialSamplesForTheNextPass() {
        AtomicInteger calls = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("provider failure");
        try {
            IrisInterpolation.getNoiseBounds(InterpolationMethod.HERMITE_STARCAST_12, 71, -209, 7D,
                    (x, z) -> {
                        if (calls.incrementAndGet() == 5) {
                            throw failure;
                        }
                        return asymmetricBounds(x, z);
                    });
            fail("Expected provider failure");
        } catch (IllegalStateException expected) {
            assertEquals(failure, expected);
        }
        NoiseBounds expected = separatePasses(InterpolationMethod.HERMITE_STARCAST_12, 71, -209, 7D,
                IrisInterpolationBoundsTest::asymmetricBounds, new HashMap<>());
        NoiseBounds actual = IrisInterpolation.getNoiseBounds(InterpolationMethod.HERMITE_STARCAST_12,
                71, -209, 7D, IrisInterpolationBoundsTest::asymmetricBounds);
        assertBounds(expected, actual);
    }

    private static NoiseBounds separatePasses(InterpolationMethod method, double x, double z, double scale,
                                              NoiseBoundsProvider provider, Map<Coordinate, NoiseBounds> samples) {
        double minimum = IrisInterpolation.getNoise(method, x, z, scale,
                (sx, sz) -> samples.computeIfAbsent(new Coordinate(sx, sz),
                        coordinate -> provider.noise(sx, sz)).min());
        double maximum = IrisInterpolation.getNoise(method, x, z, scale,
                (sx, sz) -> samples.computeIfAbsent(new Coordinate(sx, sz),
                        coordinate -> provider.noise(sx, sz)).max());
        return new NoiseBounds(minimum, maximum);
    }

    private static NoiseBounds asymmetricBounds(double x, double z) {
        double minimum = StrictMath.sin(x * 0.0031D) * 35D - StrictMath.cos(z * 0.0087D) * 7D;
        return new NoiseBounds(minimum, minimum + 17D + StrictMath.cos(x * 0.0123D - z * 0.019D) * 11D);
    }

    private static void assertBounds(NoiseBounds expected, NoiseBounds actual) {
        assertEquals(Double.doubleToLongBits(expected.min()), Double.doubleToLongBits(actual.min()));
        assertEquals(Double.doubleToLongBits(expected.max()), Double.doubleToLongBits(actual.max()));
    }

    private record Coordinate(double x, double z) {
    }
}

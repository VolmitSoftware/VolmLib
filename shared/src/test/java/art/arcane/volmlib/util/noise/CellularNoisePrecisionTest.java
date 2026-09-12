package art.arcane.volmlib.util.noise;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CellularNoisePrecisionTest {
    @Test
    public void distantCellBoundariesRetainSubFloatCoordinatePrecision() {
        for (double origin : new double[]{-29_999_000D, 29_998_000D}) {
            CellularNoise noise = new CellularNoise(1337L);
            for (int dimensions = 1; dimensions <= 3; dimensions++) {
                double previousCoordinate = origin;
                double previousValue = sample(noise, dimensions, origin);
                boolean preciseBoundary = false;
                for (int step = 1; step <= 8192; step++) {
                    double coordinate = origin + step * 0.125D;
                    double value = sample(noise, dimensions, coordinate);
                    if (Float.floatToRawIntBits((float) coordinate)
                            == Float.floatToRawIntBits((float) previousCoordinate)
                            && value != previousValue) {
                        preciseBoundary = true;
                        break;
                    }
                    previousCoordinate = coordinate;
                    previousValue = value;
                }
                assertTrue("Missing precise cell boundary at " + origin + " in " + dimensions + "D", preciseBoundary);
            }
        }
    }

    @Test
    public void distantSamplesAreBoundedAndRepeatableAcrossSeeds() {
        for (long seed : new long[]{0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            CellularNoise first = new CellularNoise(seed);
            CellularNoise second = new CellularNoise(seed);
            for (int dimensions = 1; dimensions <= 3; dimensions++) {
                for (int step = -256; step <= 256; step++) {
                    double coordinate = Math.copySign(29_999_000D, step) + step * 0.125D;
                    double value = sample(first, dimensions, coordinate);
                    assertTrue(Double.isFinite(value) && value >= 0D && value <= 1D);
                    assertEquals(value, sample(second, dimensions, coordinate), 0D);
                }
            }
        }
    }

    private static double sample(CellularNoise noise, int dimensions, double coordinate) {
        return switch (dimensions) {
            case 1 -> noise.noise(coordinate);
            case 2 -> noise.noise(coordinate, -29_998_713.375D);
            default -> noise.noise(coordinate, 73.375D, 29_998_713.375D);
        };
    }
}

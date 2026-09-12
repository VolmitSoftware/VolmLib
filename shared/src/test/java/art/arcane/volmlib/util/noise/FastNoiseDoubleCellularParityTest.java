package art.arcane.volmlib.util.noise;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FastNoiseDoubleCellularParityTest {
    private static final FastNoiseDouble.CellularDistanceFunction[] DISTANCE_FUNCTIONS = new FastNoiseDouble.CellularDistanceFunction[]{
            FastNoiseDouble.CellularDistanceFunction.Euclidean,
            FastNoiseDouble.CellularDistanceFunction.Manhattan,
            FastNoiseDouble.CellularDistanceFunction.Natural
    };
    private static final FastNoiseDouble.CellularReturnType[] TWO_EDGE_RETURN_TYPES = new FastNoiseDouble.CellularReturnType[]{
            FastNoiseDouble.CellularReturnType.Distance2,
            FastNoiseDouble.CellularReturnType.Distance2Add,
            FastNoiseDouble.CellularReturnType.Distance2Sub,
            FastNoiseDouble.CellularReturnType.Distance2Mul,
            FastNoiseDouble.CellularReturnType.Distance2Div
    };
    private static final Method HASH_2D = getDeclaredMethod("hash2D", long.class, long.class, long.class);
    private static final Method HASH_3D = getDeclaredMethod("hash3D", long.class, long.class, long.class, long.class);
    private static final double[] CELL_2D_X = getDeclaredDoubleArray("CELL_2D_X");
    private static final double[] CELL_2D_Y = getDeclaredDoubleArray("CELL_2D_Y");
    private static final double[] CELL_3D_X = getDeclaredDoubleArray("CELL_3D_X");
    private static final double[] CELL_3D_Y = getDeclaredDoubleArray("CELL_3D_Y");
    private static final double[] CELL_3D_Z = getDeclaredDoubleArray("CELL_3D_Z");

    @Test
    public void cellular2DTwoEdgeModesMatchExpandedReference() throws Exception {
        for (long seed = 11L; seed <= 17L; seed++) {
            double frequency = 0.037D + (seed * 0.001D);
            for (FastNoiseDouble.CellularDistanceFunction distanceFunction : DISTANCE_FUNCTIONS) {
                for (FastNoiseDouble.CellularReturnType returnType : TWO_EDGE_RETURN_TYPES) {
                    FastNoiseDouble noise = new FastNoiseDouble(seed);
                    noise.setFrequency(frequency);
                    noise.setCellularDistanceFunction(distanceFunction);
                    noise.setCellularReturnType(returnType);
                    for (int x = -96; x <= 96; x += 11) {
                        for (int y = -96; y <= 96; y += 13) {
                            double sampleX = x + 0.37D;
                            double sampleY = y - 0.41D;
                            double expected = referenceGetCellular2D(seed, frequency, distanceFunction, returnType, sampleX, sampleY);
                            double actual = noise.GetCellular(sampleX, sampleY);
                            assertEquals("2D seed=" + seed + " distance=" + distanceFunction + " return=" + returnType + " x=" + sampleX + " y=" + sampleY, expected, actual, 0D);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void cellular3DTwoEdgeModesMatchExpandedReference() throws Exception {
        for (long seed = 31L; seed <= 35L; seed++) {
            double frequency = 0.029D + (seed * 0.001D);
            for (FastNoiseDouble.CellularDistanceFunction distanceFunction : DISTANCE_FUNCTIONS) {
                for (FastNoiseDouble.CellularReturnType returnType : TWO_EDGE_RETURN_TYPES) {
                    FastNoiseDouble noise = new FastNoiseDouble(seed);
                    noise.setFrequency(frequency);
                    noise.setCellularDistanceFunction(distanceFunction);
                    noise.setCellularReturnType(returnType);
                    for (int x = -48; x <= 48; x += 9) {
                        for (int y = -32; y <= 32; y += 7) {
                            for (int z = -48; z <= 48; z += 11) {
                                double sampleX = x + 0.19D;
                                double sampleY = y - 0.27D;
                                double sampleZ = z + 0.43D;
                                double expected = referenceGetCellular3D(seed, frequency, distanceFunction, returnType, sampleX, sampleY, sampleZ);
                                double actual = noise.GetCellular(sampleX, sampleY, sampleZ);
                                assertEquals("3D seed=" + seed + " distance=" + distanceFunction + " return=" + returnType + " x=" + sampleX + " y=" + sampleY + " z=" + sampleZ, expected, actual, 0D);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void secondNearestCellMayLieOutsideImmediateNeighbors() throws Exception {
        long seed2D = 2320148477032134941L;
        long seed3D = 8394744594210714849L;
        for (FastNoiseDouble.CellularDistanceFunction distanceFunction : DISTANCE_FUNCTIONS) {
            for (FastNoiseDouble.CellularReturnType returnType : TWO_EDGE_RETURN_TYPES) {
                FastNoiseDouble noise = new FastNoiseDouble(seed2D);
                noise.setFrequency(1D);
                noise.setCellularDistanceFunction(distanceFunction);
                noise.setCellularReturnType(returnType);
                double x = 783.44443982D;
                double y = 1896.50036984D;
                assertEquals(referenceGetCellular2D(seed2D, 1D, distanceFunction, returnType, x, y),
                        noise.GetCellular(x, y), 0D);
                noise.setSeed(seed3D);
                x = -2741.75935805D;
                y = -1049.4161945D;
                double z = -1528.00711843D;
                assertEquals(referenceGetCellular3D(seed3D, 1D, distanceFunction, returnType, x, y, z),
                        noise.GetCellular(x, y, z), 0D);
            }
        }
    }

    @Test
    public void distanceProfilesStayContinuousWhenSearchCenterChanges() {
        FastNoiseDouble noise = new FastNoiseDouble(2320148477032134941L);
        noise.setFrequency(1D);
        noise.setCellularDistanceFunction(FastNoiseDouble.CellularDistanceFunction.Natural);
        for (FastNoiseDouble.CellularReturnType returnType : TWO_EDGE_RETURN_TYPES) {
            noise.setCellularReturnType(returnType);
            double below = noise.GetCellular(783.44443982D, 1896.5D - 1E-8D);
            double above = noise.GetCellular(783.44443982D, 1896.5D + 1E-8D);
            assertTrue(Math.abs(above - below) < 1E-6D);
        }
    }

    private double referenceGetCellular2D(long seed, double frequency, FastNoiseDouble.CellularDistanceFunction distanceFunction, FastNoiseDouble.CellularReturnType returnType, double x, double y) throws Exception {
        double scaledX = x * frequency;
        double scaledY = y * frequency;
        long xr = fastRound(scaledX);
        long yr = fastRound(scaledY);
        double distance = 999999D;
        double distance2 = 999999D;

        for (long xi = xr - 2; xi <= xr + 2; xi++) {
            for (long yi = yr - 2; yi <= yr + 2; yi++) {
                int cellIndex = (int) hash2D(seed, xi, yi) & 255;
                double vecX = xi - scaledX + CELL_2D_X[cellIndex];
                double vecY = yi - scaledY + CELL_2D_Y[cellIndex];
                double newDistance = switch (distanceFunction) {
                    case Euclidean -> vecX * vecX + vecY * vecY;
                    case Manhattan -> Math.abs(vecX) + Math.abs(vecY);
                    case Natural -> (Math.abs(vecX) + Math.abs(vecY)) + (vecX * vecX + vecY * vecY);
                };
                distance2 = Math.max(Math.min(distance2, newDistance), distance);
                distance = Math.min(distance, newDistance);
            }
        }

        return applyTwoEdgeReturn(distance, distance2, returnType);
    }

    private double referenceGetCellular3D(long seed, double frequency, FastNoiseDouble.CellularDistanceFunction distanceFunction, FastNoiseDouble.CellularReturnType returnType, double x, double y, double z) throws Exception {
        double scaledX = x * frequency;
        double scaledY = y * frequency;
        double scaledZ = z * frequency;
        long xr = fastRound(scaledX);
        long yr = fastRound(scaledY);
        long zr = fastRound(scaledZ);
        double distance = 999999D;
        double distance2 = 999999D;
        int radius = distanceFunction == FastNoiseDouble.CellularDistanceFunction.Manhattan ? 3 : 2;

        for (long xi = xr - radius; xi <= xr + radius; xi++) {
            for (long yi = yr - radius; yi <= yr + radius; yi++) {
                for (long zi = zr - radius; zi <= zr + radius; zi++) {
                    int cellIndex = (int) hash3D(seed, xi, yi, zi) & 255;
                    double vecX = xi - scaledX + CELL_3D_X[cellIndex];
                    double vecY = yi - scaledY + CELL_3D_Y[cellIndex];
                    double vecZ = zi - scaledZ + CELL_3D_Z[cellIndex];
                    double newDistance = switch (distanceFunction) {
                        case Euclidean -> vecX * vecX + vecY * vecY + vecZ * vecZ;
                        case Manhattan -> Math.abs(vecX) + Math.abs(vecY) + Math.abs(vecZ);
                        case Natural -> (Math.abs(vecX) + Math.abs(vecY) + Math.abs(vecZ)) + (vecX * vecX + vecY * vecY + vecZ * vecZ);
                    };
                    distance2 = Math.max(Math.min(distance2, newDistance), distance);
                    distance = Math.min(distance, newDistance);
                }
            }
        }

        return applyTwoEdgeReturn(distance, distance2, returnType);
    }

    private double applyTwoEdgeReturn(double distance, double distance2, FastNoiseDouble.CellularReturnType returnType) {
        return switch (returnType) {
            case Distance2 -> distance2 - 1D;
            case Distance2Add -> distance2 + distance - 1D;
            case Distance2Sub -> distance2 - distance - 1D;
            case Distance2Mul -> distance2 * distance - 1D;
            case Distance2Div -> distance / distance2 - 1D;
            default -> 0D;
        };
    }

    private static long fastRound(double value) {
        return value >= 0D ? (long) (value + 0.5D) : (long) (value - 0.5D);
    }

    private static long hash2D(long seed, long x, long y) throws Exception {
        Long value = (Long) HASH_2D.invoke(null, seed, x, y);
        return value.longValue();
    }

    private static long hash3D(long seed, long x, long y, long z) throws Exception {
        Long value = (Long) HASH_3D.invoke(null, seed, x, y, z);
        return value.longValue();
    }

    private static Method getDeclaredMethod(String name, Class<?>... parameterTypes) {
        try {
            Method method = FastNoiseDouble.class.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static double[] getDeclaredDoubleArray(String name) {
        try {
            Field field = FastNoiseDouble.class.getDeclaredField(name);
            field.setAccessible(true);
            return (double[]) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}

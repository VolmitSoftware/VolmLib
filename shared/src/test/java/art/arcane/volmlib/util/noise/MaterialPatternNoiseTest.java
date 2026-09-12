package art.arcane.volmlib.util.noise;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MaterialPatternNoiseTest {
    @Test
    public void materialPatternsStayBoundedSeededAndVolumetric() {
        for (long seed : new long[]{0L, 813753L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            PatternNoise[] patterns = patterns(seed);
            PatternNoise[] repeated = patterns(seed);
            PatternNoise[] otherSeed = patterns(seed ^ (1L << 47));
            for (int pattern = 0; pattern < patterns.length; pattern++) {
                PatternNoise noise = patterns[pattern];
                double seedDifference = 0D;
                double heightDifference = 0D;
                for (int octaves : new int[]{1, 4, 16}) {
                    noise.setOctaves(octaves);
                    repeated[pattern].setOctaves(octaves);
                    otherSeed[pattern].setOctaves(octaves);
                    for (double origin : new double[]{-468750D, 0D, 468750D}) {
                        for (int i = 0; i < 128; i++) {
                            double x = origin + i * 0.173D;
                            double y = i * 0.071D - 4D;
                            double z = -origin + i * 0.319D;
                            double value = noise.noise(x, y, z);
                            assertTrue(Double.isFinite(value) && value >= 0D && value <= 1D);
                            assertEquals(value, repeated[pattern].noise(x, y, z), 0D);
                            assertEquals(noise.noise(x, z), noise.noise(x, 0D, z), 0D);
                            seedDifference += Math.abs(value - otherSeed[pattern].noise(x, y, z));
                            heightDifference += Math.abs(value - noise.noise(x, y + 0.713D, z));
                        }
                    }
                }
                assertTrue(noise.getClass().getSimpleName(), seedDifference > 10D);
                assertTrue(noise.getClass().getSimpleName(), heightDifference > 10D);
            }
        }
    }

    @Test
    public void materialPatternsHaveNoCoordinateBoundaryJumps() {
        for (long seed : new long[]{0L, 813753L, Long.MAX_VALUE}) {
            for (PatternNoise noise : patterns(seed)) {
                for (double boundary : new double[]{-468750D, -2D, 0D, 3D, 468750D}) {
                    for (int i = 0; i < 128; i++) {
                        double coordinate = i * 0.073D - 4D;
                        assertEquals(noise.noise(boundary - 1E-7D, 0.317D, coordinate),
                                noise.noise(boundary + 1E-7D, 0.317D, coordinate), 1E-4D);
                        assertEquals(noise.noise(coordinate, 0.317D, boundary - 1E-7D),
                                noise.noise(coordinate, 0.317D, boundary + 1E-7D), 1E-4D);
                    }
                }
            }
        }
    }

    @Test
    public void duneWindwardSlopeIsLongerAndHornsBendDownwind() {
        DuneNoise dune = new DuneNoise(0L);
        assertTrue(dune.noise(0.02D, 0.2D) > dune.noise(0.38D, 0.2D) + 0.2D);
        double across = 0.2D + 0.32D * 0.65D;
        double crest = 0.2D + 0.43D * 0.65D * 0.65D;
        assertTrue(dune.noise(crest, across) > dune.noise(0.2D, across) + 0.05D);
        assertTrue(dune.noise(0.2D, 0.2D) > dune.noise(crest, across));
    }

    @Test
    public void woodKnotsCarryClosedGrowthRings() {
        WoodNoise wood = new WoodNoise(0L);
        double minimum = 1D;
        double maximum = 0D;
        for (int i = 0; i < 64; i++) {
            double angle = i * Math.PI / 32D;
            double across = 0.3D + Math.cos(angle) * 0.12D;
            double along = 0.3D + Math.sin(angle) * 0.12D;
            double x = across - 0.11D * Math.sin(along * 0.85D);
            double value = wood.noise(x, along / 0.45D);
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
        }
        double brightRingX = 0.34D - 0.11D * Math.sin(0.3D * 0.85D);
        assertTrue(maximum - minimum < 0.22D);
        assertTrue(wood.noise(brightRingX, 0.3D / 0.45D) > maximum + 0.4D);
    }

    @Test
    public void strataContainsRepeatedHighAndLowBands() {
        StrataNoise strata = new StrataNoise(813753L);
        int highBands = 0;
        int lowBands = 0;
        double previous = strata.noise(0D, -8D);
        double current = strata.noise(0D, -8D + 0.008D);
        for (int i = 2; i < 2048; i++) {
            double next = strata.noise(0D, -8D + i * 0.008D);
            if (current > previous && current > next && current > 0.75D) {
                highBands++;
            }
            if (current < previous && current < next && current < 0.25D) {
                lowBands++;
            }
            previous = current;
            current = next;
        }
        assertTrue(highBands >= 6);
        assertTrue(lowBands >= 6);
    }

    private static PatternNoise[] patterns(long seed) {
        return new PatternNoise[]{new DuneNoise(seed), new StrataNoise(seed), new WoodNoise(seed)};
    }
}

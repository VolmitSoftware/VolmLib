package art.arcane.volmlib.util.noise;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CircuitNoiseTest {
    @Test
    public void tracksMeetAtTilePortsWithoutBoundaryJumps() {
        CircuitNoise noise = new CircuitNoise(0L);
        double maximum = 0D;
        for (int boundary = -8; boundary <= 8; boundary++) {
            for (int step = 0; step < 256; step++) {
                double z = step * 0.019D - 2D;
                assertEquals(noise.noise(boundary - 1E-8D, z), noise.noise(boundary + 1E-8D, z), 1E-5D);
                maximum = Math.max(maximum, noise.noise(boundary, z));
            }
        }
        assertTrue(maximum > 0.99D);
    }

    @Test
    public void circuitHasOpenBoardTracesAndIntermediateEdges() {
        CircuitNoise noise = new CircuitNoise(1337L);
        int open = 0;
        int trace = 0;
        int edge = 0;
        for (int x = 0; x < 256; x++) {
            for (int z = 0; z < 256; z++) {
                double value = noise.noise(x / 64D, z / 64D);
                if (value == 0D) {
                    open++;
                } else if (value > 0.8D) {
                    trace++;
                } else {
                    edge++;
                }
            }
        }
        assertTrue(open > 32768);
        assertTrue(trace > 1500);
        assertTrue(edge > 1500);
    }
}

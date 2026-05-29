package art.arcane.volmlib.util.math;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PowerOfTwoCoordinatesTest {
    @Test
    public void coordinateConversionsMatchExpectedFloorAndShiftSemantics() {
        int[] samples = new int[]{-513, -512, -33, -32, -17, -16, -1, 0, 15, 16, 31, 32, 511, 512};

        for (int value : samples) {
            assertEquals(Math.floorDiv(value, 16), PowerOfTwoCoordinates.blockToChunkFloor(value));
            assertEquals(Math.floorDiv(value, 512), PowerOfTwoCoordinates.blockToRegionFloor(value));
            assertEquals(Math.floorDiv(value, 32), PowerOfTwoCoordinates.chunkToRegion(value));
            assertEquals(value * 16, PowerOfTwoCoordinates.chunkToBlock(value));
            assertEquals(value * 32, PowerOfTwoCoordinates.regionToChunk(value));
            assertEquals(value * 512, PowerOfTwoCoordinates.regionToBlock(value));
        }
    }

    @Test
    public void ceilDivPow2MatchesPositiveAndNegativeBoundaries() {
        int[] samples = new int[]{-513, -512, -33, -32, -17, -16, -1, 0, 15, 16, 31, 32, 511, 512};

        for (int value : samples) {
            assertEquals(Math.ceilDiv(value, 16), PowerOfTwoCoordinates.ceilDivPow2(value, 4));
            assertEquals(Math.ceilDiv(value, 512), PowerOfTwoCoordinates.ceilDivPow2(value, 9));
        }
    }

    @Test
    public void localPackingMatchesExistingPackedIndexLayouts() {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int packed = PowerOfTwoCoordinates.packLocal16(x, z);
                assertEquals((x << 4) | z, packed);
                assertEquals(x, PowerOfTwoCoordinates.unpackLocal16X(packed));
                assertEquals(z, PowerOfTwoCoordinates.unpackLocal16Z(packed));
            }
        }

        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                int packed = PowerOfTwoCoordinates.packLocal32(x, z);
                assertEquals((x << 5) | z, packed);
                assertEquals(x, PowerOfTwoCoordinates.unpackLocal32X(packed));
                assertEquals(z, PowerOfTwoCoordinates.unpackLocal32Z(packed));
            }
        }
    }
}

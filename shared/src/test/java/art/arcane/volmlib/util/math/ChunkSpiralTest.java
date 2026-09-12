package art.arcane.volmlib.util.math;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ChunkSpiralTest {
    @Test
    public void preservesDistanceOrderAndStableTies() {
        List<int[]> chunks = ChunkSpiral.centerOut(12, -7, 1);
        assertEquals(9, chunks.size());
        assertArrayEquals(new int[]{12, -7}, chunks.get(0));
        assertArrayEquals(new int[]{11, -7}, chunks.get(1));
        assertArrayEquals(new int[]{12, -8}, chunks.get(2));
        assertArrayEquals(new int[]{12, -6}, chunks.get(3));
        assertArrayEquals(new int[]{13, -7}, chunks.get(4));
    }

    @Test
    public void zeroRadiusReturnsOnlyTheCenterAndNegativeRadiusIsEmpty() {
        List<int[]> chunks = ChunkSpiral.centerOut(-2, 3, 0);
        assertEquals(1, chunks.size());
        assertArrayEquals(new int[]{-2, 3}, chunks.get(0));
        assertTrue(ChunkSpiral.centerOut(-2, 3, -1).isEmpty());
    }
}

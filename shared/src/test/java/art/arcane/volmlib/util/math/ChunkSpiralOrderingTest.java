package art.arcane.volmlib.util.math;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ChunkSpiralOrderingTest {
    @Test
    public void centerOutMatchesLegacyOrdering() {
        int centerX = 5;
        int centerZ = -3;
        int radius = 3;
        List<int[]> expected = legacyOrderedTargets(centerX, centerZ, radius);
        List<int[]> actual = ChunkSpiral.centerOut(centerX, centerZ, radius);
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertArrayEquals(expected.get(i), actual.get(i));
        }
    }

    @Test
    public void centerOutStartsAtCenterAndCoversSquare() {
        List<int[]> targets = ChunkSpiral.centerOut(10, 20, 2);
        assertEquals(25, targets.size());
        assertArrayEquals(new int[]{10, 20}, targets.get(0));
    }

    @Test
    public void centerOutRadiusZeroIsSingleChunk() {
        List<int[]> targets = ChunkSpiral.centerOut(-7, 9, 0);
        assertEquals(1, targets.size());
        assertArrayEquals(new int[]{-7, 9}, targets.get(0));
    }

    private static List<int[]> legacyOrderedTargets(int centerChunkX, int centerChunkZ, int radius) {
        List<int[]> targets = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                targets.add(new int[]{centerChunkX + dx, centerChunkZ + dz});
            }
        }

        targets.sort(Comparator.comparingInt((int[] t) -> {
            int ox = t[0] - centerChunkX;
            int oz = t[1] - centerChunkZ;
            return ox * ox + oz * oz;
        }));
        return targets;
    }
}

package art.arcane.volmlib.util.hunk.storage;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AtomicHunkTest {
    @Test
    public void roundTripsEveryCellOfANonCubicHunk() {
        AtomicHunk<String> hunk = new AtomicHunk<String>(3, 5, 7);
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 7; z++) {
                    hunk.setRaw(x, y, z, x + "," + y + "," + z);
                }
            }
        }
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 7; z++) {
                    assertEquals(x + "," + y + "," + z, hunk.getRaw(x, y, z));
                }
            }
        }
    }

    @Test
    public void storesCellsInZMajorYThenXOrder() {
        AtomicHunk<Integer> hunk = new AtomicHunk<Integer>(4, 3, 2);
        hunk.setRaw(1, 2, 1, 7);
        assertEquals(Integer.valueOf(7), hunk.getData().get((1 * 4 * 3) + (2 * 4) + 1));
        hunk.setRaw(3, 0, 0, 9);
        assertEquals(Integer.valueOf(9), hunk.getData().get(3));
        assertNull(hunk.getRaw(0, 0, 0));
    }

    @Test
    public void reportsDimensionsAndAtomicity() {
        AtomicHunk<Object> hunk = new AtomicHunk<Object>(2, 9, 4);
        assertEquals(2, hunk.getWidth());
        assertEquals(9, hunk.getHeight());
        assertEquals(4, hunk.getDepth());
        assertTrue(hunk.isAtomic());
        assertEquals(2 * 9 * 4, hunk.getData().length());
    }

    @Test
    public void fillWritesEveryCell() {
        AtomicHunk<String> hunk = new AtomicHunk<String>(2, 2, 3);
        hunk.fill("v");
        for (int i = 0; i < hunk.getData().length(); i++) {
            assertEquals("v", hunk.getData().get(i));
        }
    }
}

package art.arcane.volmlib.util.inventorygui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UIWindowScrollMathTest {
    @Test
    public void contentThatFitsTheViewportNeverScrolls() {
        assertEquals(0, UIWindow.maxViewportPosition(0, 1));
        assertEquals(0, UIWindow.maxViewportPosition(2, 3));
        assertEquals(0, UIWindow.maxViewportPosition(2, 6));
    }

    @Test
    public void lastContentRowIsAlwaysReachableAtMaxScroll() {
        for (int highestRow = 0; highestRow <= 20; highestRow++) {
            for (int viewportHeight = 1; viewportHeight <= 6; viewportHeight++) {
                int max = UIWindow.maxViewportPosition(highestRow, viewportHeight);
                int lastVisibleRow = max + viewportHeight - 1;
                assertTrue(
                    "highestRow " + highestRow + " viewport " + viewportHeight,
                    lastVisibleRow >= highestRow);
                assertTrue("never scrolls past content", max <= Math.max(0, highestRow));
            }
        }
    }

    @Test
    public void sixRowLayoutWithThreeRowViewportScrollsToTheBottom() {
        assertEquals(3, UIWindow.maxViewportPosition(5, 3));
    }
}

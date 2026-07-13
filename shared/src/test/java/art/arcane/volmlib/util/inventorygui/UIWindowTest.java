package art.arcane.volmlib.util.inventorygui;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UIWindowTest {
    @Test
    public void immediateCloseReasons_coverTerminalPaperReasons() {
        assertTrue(UIWindow.isImmediateCloseReason(CloseReason.DISCONNECT));
        assertTrue(UIWindow.isImmediateCloseReason(CloseReason.UNLOADED));
        assertTrue(UIWindow.isImmediateCloseReason(CloseReason.DEATH));
    }

    @Test
    public void immediateCloseReasons_deferNormalAndUnavailableReasons() {
        assertFalse(UIWindow.isImmediateCloseReason(CloseReason.PLAYER));
        assertFalse(UIWindow.isImmediateCloseReason(null));
        assertFalse(UIWindow.isImmediateCloseReason("DISCONNECT"));
    }

    private enum CloseReason {
        DISCONNECT,
        UNLOADED,
        DEATH,
        PLAYER
    }
}

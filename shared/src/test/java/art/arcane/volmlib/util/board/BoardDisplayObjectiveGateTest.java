package art.arcane.volmlib.util.board;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The packet backend used to re-send the display-objective packet on every render, which is one
 * wasted packet per player per update; sending it only on change would hand the sidebar to any
 * plugin that takes the display slot without the ownership protocol. These pin the middle ground:
 * on first render, on an objective rebuild, on reclaim, and otherwise on a slow heartbeat.
 */
public class BoardDisplayObjectiveGateTest {
    private static final long NOW = 1_000_000_000L;

    @Test
    public void theFirstRenderDisplaysTheObjective() {
        assertTrue(Board.shouldSendDisplayObjective(false, false, NOW, NOW + 1L));
    }

    @Test
    public void anUnchangedObjectiveIsNotRedisplayedBeforeTheHeartbeat() {
        assertFalse(Board.shouldSendDisplayObjective(true, false, NOW, NOW + 1L));
    }

    @Test
    public void aRebuiltObjectiveIsDisplayedAgain() {
        assertTrue(Board.shouldSendDisplayObjective(true, true, NOW, NOW + 1L));
    }

    @Test
    public void aReclaimedSidebarIsDisplayedAgain() {
        // Losing the sidebar to another plugin clears displayedObjective, so the next render that
        // wins ownership back re-sends without needing a new objective.
        assertTrue(Board.shouldSendDisplayObjective(false, true, NOW, NOW + 1L));
    }

    @Test
    public void theHeartbeatReassertsTheSidebarAgainstPluginsOutsideTheOwnershipProtocol() {
        assertTrue(Board.shouldSendDisplayObjective(true, false, NOW, NOW));
        assertTrue(Board.shouldSendDisplayObjective(true, false, NOW + 1L, NOW));
    }

    @Test
    public void theHeartbeatDeadlineIsStaggeredPerBoardAndAtLeastFiveSeconds() {
        long first = Board.nextDisplayObjectiveNanos(NOW, "board-one");
        long second = Board.nextDisplayObjectiveNanos(NOW, "board-two");

        assertTrue(first - NOW >= 5_000_000_000L);
        assertTrue(second - NOW >= 5_000_000_000L);
        assertTrue(first - NOW < 7_000_000_000L);
        assertTrue(second - NOW < 7_000_000_000L);
        assertTrue(first != second);
    }
}

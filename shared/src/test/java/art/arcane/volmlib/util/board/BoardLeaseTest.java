package art.arcane.volmlib.util.board;

import org.bukkit.scoreboard.Scoreboard;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class BoardLeaseTest {
    @Test
    public void restoresPreviousScoreboardWhileBoardStillOwnsAssignment() {
        Scoreboard previous = mock(Scoreboard.class);
        Scoreboard owned = mock(Scoreboard.class);

        assertSame(previous, Board.selectScoreboardToRestore(owned, owned, previous));
    }

    @Test
    public void preservesScoreboardAssignedAfterBoardLease() {
        Scoreboard previous = mock(Scoreboard.class);
        Scoreboard owned = mock(Scoreboard.class);
        Scoreboard replacement = mock(Scoreboard.class);

        assertSame(replacement, Board.selectScoreboardToRestore(replacement, owned, previous));
    }

    @Test
    public void ownsDedicatedScoreboardWhileItRemainsAssigned() {
        Scoreboard previous = mock(Scoreboard.class);
        Scoreboard owned = mock(Scoreboard.class);

        assertTrue(Board.ownsScoreboardAssignment(owned, owned, previous));
    }

    @Test
    public void yieldsDedicatedScoreboardToLaterReplacement() {
        Scoreboard previous = mock(Scoreboard.class);
        Scoreboard owned = mock(Scoreboard.class);
        Scoreboard replacement = mock(Scoreboard.class);

        assertFalse(Board.ownsScoreboardAssignment(replacement, owned, previous));
    }

    @Test
    public void packetBoardOwnsOnlyItsUnchangedBaselineAssignment() {
        Scoreboard previous = mock(Scoreboard.class);
        Scoreboard replacement = mock(Scoreboard.class);

        assertTrue(Board.ownsScoreboardAssignment(previous, null, previous));
        assertFalse(Board.ownsScoreboardAssignment(replacement, null, previous));
        assertFalse(Board.ownsScoreboardAssignment(null, null, previous));
    }
}

package art.arcane.volmlib.util.board;

import org.bukkit.entity.Player;
import org.junit.After;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BoardScoreNumberCompatibilityTest {
    private CharacterizationBoardRenderHarness harness;

    @After
    public void tearDown() {
        if (harness != null) {
            harness.close();
            harness = null;
        }
    }

    @Test
    public void providersHideScoreNumbersByDefault() {
        BoardProvider provider = new BoardProvider() {
            @Override
            public String getTitle(Player player) {
                return "Title";
            }

            @Override
            public List<String> getLines(Player player) {
                return List.of("Line");
            }
        };

        assertTrue(provider.hideScoreNumbers(null));
    }

    @Test
    public void providersCanKeepScoreNumbersVisible() {
        BoardProvider provider = new BoardProvider() {
            @Override
            public String getTitle(Player player) {
                return "Title";
            }

            @Override
            public List<String> getLines(Player player) {
                return List.of("Line");
            }

            @Override
            public boolean hideScoreNumbers(Player player) {
                return false;
            }
        };

        assertFalse(provider.hideScoreNumbers(null));
    }

    @Test
    public void requestedFormattingIsEffectiveOnlyWhenTheRuntimeSupportsIt() {
        assertTrue(Board.effectiveHideScoreNumbers(true, true));
        assertFalse(Board.effectiveHideScoreNumbers(true, false));
        assertFalse(Board.effectiveHideScoreNumbers(false, true));
        assertFalse(Board.effectiveHideScoreNumbers(false, false));
    }

    @Test
    public void legacyPaperBackendStillRendersWhenHiddenNumbersAreRequested() {
        harness = CharacterizationBoardRenderHarness.normal();
        CharacterizationBoardRenderHarness.PlayerHandle player = harness.newPlayer();
        CharacterizationBoardRenderHarness.ProviderHandle provider =
                harness.provider("Title", List.of("One", "Two"), true);
        Object board = harness.newBoard(player, harness.settings(provider, "DOWN"));

        harness.update(board);

        assertEquals(1, provider.hideScoreNumbersCalls.get());
        assertEquals(2, harness.ownedScoreboard().scores.size());
    }
}

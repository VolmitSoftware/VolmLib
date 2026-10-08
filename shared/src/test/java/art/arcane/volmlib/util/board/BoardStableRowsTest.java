package art.arcane.volmlib.util.board;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class BoardStableRowsTest {
    @Test
    public void disappearingAndReorderedRowsKeepSurvivingSlots() {
        BoardRowSlots slots = new BoardRowSlots();
        assertArrayEquals(new int[]{0, 1, 2}, slots.assign(List.of("a", "b", "c")));
        assertArrayEquals(new int[]{2, 0}, slots.assign(List.of("c", "a")));
        assertArrayEquals(new int[]{2, 1, 0}, slots.assign(List.of("c", "d", "a")));
        assertThrows(IllegalArgumentException.class, () -> slots.assign(List.of("a", "a")));
    }

    @Test
    public void backendKeepsTextTeamsWhileRanksMoveAndResetsOnlyRemovedRow() {
        try (CharacterizationBoardRenderHarness harness = CharacterizationBoardRenderHarness.normal()) {
            CharacterizationBoardRenderHarness.PlayerHandle player = harness.newPlayer();
            CharacterizationBoardRenderHarness.ProviderHandle provider = harness.provider("Title", List.of("A", "B", "C"));
            provider.slots = new int[]{0, 1, 2};
            Object board = harness.newBoard(player, harness.settings(provider, "DOWN"));
            harness.update(board);
            CharacterizationBoardRenderHarness.ScoreboardModel model = harness.ownedScoreboard();
            model.resetWriteCounters();
            provider.lines = () -> List.of("C", "A");
            provider.slots = new int[]{2, 0};
            harness.update(board);
            assertEquals(Map.of("§2§r", 15, "§0§r", 14), model.scores);
            assertEquals(1, model.resetScoreCalls);
            assertEquals(0, model.teams.get("§2§r").prefixWrites);
            assertEquals(0, model.teams.get("§0§r").prefixWrites);
            assertEquals(2, model.scoreWrites);
            model.resetWriteCounters();
            harness.update(board);
            assertEquals(0, model.writes());
        }
    }

    @Test
    public void upwardOrderingKeepsSlotsPairedWithTheirLabels() {
        try (CharacterizationBoardRenderHarness harness = CharacterizationBoardRenderHarness.normal()) {
            CharacterizationBoardRenderHarness.PlayerHandle player = harness.newPlayer();
            CharacterizationBoardRenderHarness.ProviderHandle provider = harness.provider("Title", List.of("A", "B"));
            provider.slots = new int[]{4, 8};
            Object board = harness.newBoard(player, harness.settings(provider, "UP"));
            harness.update(board);
            CharacterizationBoardRenderHarness.ScoreboardModel model = harness.ownedScoreboard();
            assertEquals(Map.of("§8§r", 1, "§4§r", 2), model.scores);
            assertEquals("A", model.teams.get("§4§r").prefix);
            assertEquals("B", model.teams.get("§8§r").prefix);
        }
    }
}

package art.arcane.volmlib.util.board;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Proves the equality gates in {@code Board}: a render only applies what actually changed, and the
 * resulting client-visible state is exactly the state a full re-send would have produced.
 *
 * <p>These extend the Characterization pins rather than replace them — every scenario asserts the
 * pinned final state alongside the write count, so an over-eager skip fails on state, not just on
 * the counter.
 */
public class BoardDiffGatingTest {
    private static final String T0 = "§0§r";
    private static final String T1 = "§1§r";
    private static final String T2 = "§2§r";

    private CharacterizationBoardRenderHarness harness;

    @After
    public void tearDown() {
        if (harness != null) {
            harness.close();
            harness = null;
        }
    }

    private CharacterizationBoardRenderHarness harness() {
        harness = CharacterizationBoardRenderHarness.normal();
        return harness;
    }

    @Test
    public void identicalSecondUpdateAppliesNothingAtAll() {
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        Object board = h.newBoard(player, h.settings(h.provider("§6Title", List.of("§aOne", "§bTwo")), "DOWN"));

        h.update(board);
        CharacterizationBoardRenderHarness.ScoreboardModel model = h.ownedScoreboard();
        Map<String, Object> firstState = model.stateView();
        assertTrue("first update must apply the board", model.writes() > 0);

        model.resetWriteCounters();
        h.update(board);

        assertEquals("an unchanged board must apply nothing", 0, model.writes());
        assertEquals(firstState, model.stateView());
    }

    @Test
    public void identicalUpdatesStayFreeForeverAndTheProviderIsStillConsulted() {
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        CharacterizationBoardRenderHarness.ProviderHandle provider =
                h.provider(() -> "T", () -> List.of("A", "B", "C"));
        Object board = h.newBoard(player, h.settings(provider, "DOWN"));

        h.update(board);
        CharacterizationBoardRenderHarness.ScoreboardModel model = h.ownedScoreboard();
        model.resetWriteCounters();

        for (int pass = 0; pass < 10; pass++) {
            h.update(board);
        }

        assertEquals(0, model.writes());
        // Diffing gates the application, never the provider: live text must still be sampled.
        assertEquals(11, provider.linesCalls.get());
        assertEquals(12, provider.titleCalls.get());
        assertEquals(Map.of(T0, 15, T1, 14, T2, 13), model.scores);
    }

    @Test
    public void onlyTheChangedLineIsReapplied() {
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        CharacterizationBoardRenderHarness.ProviderHandle provider =
                h.provider(() -> "T", () -> List.of("A", "B", "C"));
        Object board = h.newBoard(player, h.settings(provider, "DOWN"));

        h.update(board);
        CharacterizationBoardRenderHarness.ScoreboardModel model = h.ownedScoreboard();
        model.resetWriteCounters();
        provider.lines = () -> List.of("A", "B2", "C");
        h.update(board);

        assertEquals(0, model.teams.get(T0).prefixWrites);
        assertEquals(1, model.teams.get(T1).prefixWrites);
        assertEquals(1, model.teams.get(T1).suffixWrites);
        assertEquals(0, model.teams.get(T2).prefixWrites);
        assertEquals("no row moved, so no score is re-sent", 0, model.scoreWrites);
        assertEquals(0, model.displayNameWrites);
        assertEquals("B2", model.teams.get(T1).prefix);
        assertEquals(Map.of(T0, 15, T1, 14, T2, 13), model.scores);
    }

    @Test
    public void onlyTheTitleIsReappliedWhenOnlyTheTitleChanges() {
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        CharacterizationBoardRenderHarness.ProviderHandle provider =
                h.provider(() -> "§6First", () -> List.of("A", "B"));
        Object board = h.newBoard(player, h.settings(provider, "DOWN"));

        h.update(board);
        CharacterizationBoardRenderHarness.ScoreboardModel model = h.ownedScoreboard();
        model.resetWriteCounters();
        provider.title = () -> "§6Second";
        h.update(board);

        assertEquals(1, model.displayNameWrites);
        assertEquals("§6Second", model.objectiveDisplayName);
        assertEquals(0, model.scoreWrites);
        assertEquals(0, model.teams.get(T0).prefixWrites);
        assertEquals(0, model.teams.get(T1).prefixWrites);
    }

    @Test
    public void aRedundantColorTranslationDoesNotCountAsAChange() {
        // Provider text that already carries section codes must compare equal to itself across
        // updates; a translation that rewrote the string every pass would defeat every gate.
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        Object board = h.newBoard(player, h.settings(h.provider("§6T", List.of("§a§lStatus", "§7Plain")), "DOWN"));

        h.update(board);
        CharacterizationBoardRenderHarness.ScoreboardModel model = h.ownedScoreboard();
        model.resetWriteCounters();
        h.update(board);

        assertEquals(0, model.writes());
        assertEquals("§a§lStatus", model.teams.get(T0).prefix);
    }

    @Test
    public void ampersandTextStaysTranslatedAndStillGatesOnTheTranslatedForm() {
        // Iris's sidebar provider emits raw '&' codes, so update-time translation is load-bearing.
        // It must survive diffing AND the gate must compare the translated form, or every pass
        // would re-apply.
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        Object board = h.newBoard(player, h.settings(h.provider("&6T", List.of("&7&m-----", "&bLine")), "DOWN"));

        h.update(board);
        CharacterizationBoardRenderHarness.ScoreboardModel model = h.ownedScoreboard();
        assertEquals("§7§m-----", model.teams.get(T0).prefix);
        assertEquals("§6T", model.objectiveDisplayName);

        model.resetWriteCounters();
        h.update(board);

        assertEquals(0, model.writes());
    }

    @Test
    public void shrinkingRowCountWipesScoresAndReappliesTheSurvivingRows() {
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        CharacterizationBoardRenderHarness.ProviderHandle provider =
                h.provider(() -> "T", () -> List.of("A", "B", "C"));
        Object board = h.newBoard(player, h.settings(provider, "DOWN"));

        h.update(board);
        CharacterizationBoardRenderHarness.ScoreboardModel model = h.ownedScoreboard();
        model.resetWriteCounters();
        provider.lines = () -> List.of("A", "B");
        h.update(board);

        // The row-count change wipes the scores, so both surviving rows must be re-scored even
        // though their text is unchanged.
        assertEquals(3, model.resetScoreCalls);
        assertEquals(2, model.scoreWrites);
        assertEquals(0, model.teams.get(T0).prefixWrites);
        assertEquals(0, model.teams.get(T1).prefixWrites);
        assertEquals(Map.of(T0, 15, T1, 14), model.scores);
        assertEquals("C", model.teams.get(T2).prefix);
    }

    @Test
    public void growingRowCountReappliesEveryRow() {
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        CharacterizationBoardRenderHarness.ProviderHandle provider =
                h.provider(() -> "T", () -> List.of("A", "B"));
        Object board = h.newBoard(player, h.settings(provider, "DOWN"));

        h.update(board);
        CharacterizationBoardRenderHarness.ScoreboardModel model = h.ownedScoreboard();
        model.resetWriteCounters();
        provider.lines = () -> List.of("A", "B", "C");
        h.update(board);

        assertEquals(3, model.scoreWrites);
        assertEquals(1, model.teams.get(T2).prefixWrites);
        assertEquals(Map.of(T0, 15, T1, 14, T2, 13), model.scores);
    }

    @Test
    public void flippingScoreDirectionRewritesEveryRow() {
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        CharacterizationBoardRenderHarness.ProviderHandle provider =
                h.provider(() -> "T", () -> List.of("A", "B", "C"));
        Object board = h.newBoard(player, h.settings(provider, "DOWN"));

        h.update(board);
        CharacterizationBoardRenderHarness.ScoreboardModel model = h.ownedScoreboard();
        model.resetWriteCounters();
        h.setBoardSettings(board, h.settings(provider, "UP"));
        h.update(board);

        assertEquals(Map.of(T0, 1, T1, 2, T2, 3), model.scores);
        assertEquals("C", model.teams.get(T0).prefix);
        assertEquals("A", model.teams.get(T2).prefix);
        assertEquals(3, model.scoreWrites);
    }

    @Test
    public void fifteenRowBoardSettlesToZeroApplications() {
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            lines.add("§7Row " + i);
        }
        Object board = h.newBoard(player, h.settings(h.provider("§6Server", lines), "DOWN"));

        h.update(board);
        CharacterizationBoardRenderHarness.ScoreboardModel model = h.ownedScoreboard();
        Map<String, Object> settled = model.stateView();
        model.resetWriteCounters();

        h.update(board);
        h.update(board);

        assertEquals(0, model.writes());
        assertEquals(settled, model.stateView());
    }

    @Test
    public void aFailedApplicationForcesAFullResendOnTheNextAttempt() {
        // The normal backend is dropped after an UnsupportedOperationException, so the cache must
        // not survive it: the board here goes inert, and the pin is that the partial pass left the
        // already-applied rows alone.
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        CharacterizationBoardRenderHarness.ProviderHandle provider =
                h.provider(() -> "T", () -> List.of("A", "B"));
        Object board = h.newBoard(player, h.settings(provider, "DOWN"));

        h.update(board);
        CharacterizationBoardRenderHarness.ScoreboardModel model = h.ownedScoreboard();
        model.teams.get(T0).throwUnsupportedOnPrefix = true;
        provider.lines = () -> List.of("A2", "B2");
        h.update(board);

        assertEquals(List.of("board"), model.unregisteredObjectives);
        assertEquals("A", model.teams.get(T0).prefix);
        assertEquals("B", model.teams.get(T1).prefix);
        assertEquals(Map.of(T0, 15, T1, 14), model.scores);
    }
}

package art.arcane.volmlib.util.board;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Characterization pins for how {@code Board.update()} maps provider title/lines onto the
 * objective display name, per-line team prefix/suffix, and per-line scores on the Bukkit
 * (normal-backend) path. All assertions are final-state pins: a diffing refactor that elides
 * redundant writes must leave every one of these outcomes byte-identical.
 *
 * <p>Team names are the fixed per-index entry keys ({@code ChatColor.values()[i] + RESET});
 * they are client-visible scoreboard entries, so they are pinned literally.
 */
public class CharacterizationBoardUpdateTest {
    private static final String T0 = "§0§r";
    private static final String T1 = "§1§r";
    private static final String T2 = "§2§r";
    private static final String T3 = "§3§r";
    private static final String T4 = "§4§r";

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
    public void singleLineMapsToFirstTeamWithPrefixAndTopScore() {
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        Object board = h.newBoard(player, h.settings(h.provider("§6Title", List.of("§aHello")), "DOWN"));

        h.update(board);

        CharacterizationBoardRenderHarness.ScoreboardModel model = h.ownedScoreboard();
        assertEquals("§6Title", model.objectiveDisplayName);
        assertEquals("SIDEBAR", model.displaySlot);
        CharacterizationBoardRenderHarness.TeamState team = model.teams.get(T0);
        assertEquals("§aHello", team.prefix);
        assertEquals("", team.suffix);
        assertEquals(List.of(T0), team.entries);
        assertEquals(Map.of(T0, 15), model.scores);
    }

    @Test
    public void linesMapToIndexedTeamsWithDescendingScoresFromFifteen() {
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        Object board = h.newBoard(player, h.settings(h.provider("T", List.of("Alpha", "Beta", "Gamma")), "DOWN"));

        h.update(board);

        CharacterizationBoardRenderHarness.ScoreboardModel model = h.ownedScoreboard();
        assertEquals("Alpha", model.teams.get(T0).prefix);
        assertEquals("Beta", model.teams.get(T1).prefix);
        assertEquals("Gamma", model.teams.get(T2).prefix);
        // DOWN scores always start at 15 regardless of line count.
        assertEquals(Map.of(T0, 15, T1, 14, T2, 13), model.scores);
    }

    @Test
    public void upDirectionReversesLinesAndScoresAscendFromOne() {
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        Object board = h.newBoard(player, h.settings(h.provider("T", List.of("Alpha", "Beta", "Gamma")), "UP"));

        h.update(board);

        CharacterizationBoardRenderHarness.ScoreboardModel model = h.ownedScoreboard();
        assertEquals("Gamma", model.teams.get(T0).prefix);
        assertEquals("Beta", model.teams.get(T1).prefix);
        assertEquals("Alpha", model.teams.get(T2).prefix);
        assertEquals(Map.of(T0, 1, T1, 2, T2, 3), model.scores);
    }

    @Test
    public void longLineSplitsAcrossPrefixAndSuffixWithColorCarry() {
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        Object board = h.newBoard(player, h.settings(h.provider("T", List.of("§a123456789012345678")), "DOWN"));

        h.update(board);

        CharacterizationBoardRenderHarness.TeamState team = h.ownedScoreboard().teams.get(T0);
        assertEquals("§a12345678901234", team.prefix);
        assertEquals("§a5678", team.suffix);
    }

    @Test
    public void ampersandLinesAreColorTranslatedBeforeTheSplit() {
        // Board.update() runs translateAlternateColorCodes over every provider line before the
        // 16-char split. Fleet providers already deliver section-code text, making this a second
        // (normally redundant) translation — pinned here AS-IS. A lane that removes the update-time
        // translation must consciously flip this assertion with a note (provider '&' text would
        // then reach the client untranslated).
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        Object board = h.newBoard(player, h.settings(h.provider("T", List.of("&aHello", "&a123456789012345678")), "DOWN"));

        h.update(board);

        CharacterizationBoardRenderHarness.ScoreboardModel model = h.ownedScoreboard();
        assertEquals("§aHello", model.teams.get(T0).prefix);
        // The translated form (18 visible chars) is what gets split, not the raw '&' form.
        assertEquals("§a12345678901234", model.teams.get(T1).prefix);
        assertEquals("§a5678", model.teams.get(T1).suffix);
    }

    @Test
    public void sectionCodeLinesPassThroughByteIdentical() {
        // The invariant that must survive any translate-elision: already-translated lines render
        // byte-identical to today.
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        Object board = h.newBoard(player, h.settings(h.provider("§6Title", List.of("§a§lStatus", "§7Plain")), "DOWN"));

        h.update(board);

        CharacterizationBoardRenderHarness.ScoreboardModel model = h.ownedScoreboard();
        assertEquals("§6Title", model.objectiveDisplayName);
        assertEquals("§a§lStatus", model.teams.get(T0).prefix);
        assertEquals("§7Plain", model.teams.get(T1).prefix);
    }

    @Test
    public void sixteenLinesTruncateToFifteenTeams() {
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            lines.add("Line" + i);
        }
        Object board = h.newBoard(player, h.settings(h.provider("T", lines), "DOWN"));

        h.update(board);

        CharacterizationBoardRenderHarness.ScoreboardModel model = h.ownedScoreboard();
        assertEquals(15, model.scores.size());
        assertEquals("Line0", model.teams.get(T0).prefix);
        assertEquals("Line14", model.teams.get("§e§r").prefix);
        assertFalse(model.scores.containsKey("§f§r"));
        assertEquals(Integer.valueOf(15), model.scores.get(T0));
        assertEquals(Integer.valueOf(1), model.scores.get("§e§r"));
    }

    @Test
    public void upDirectionTruncatesToFifteenBeforeReversing() {
        // Truncation happens before the UP reversal, so the FIRST fifteen lines survive and the
        // sixteenth is dropped — not the first.
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            lines.add("Line" + i);
        }
        Object board = h.newBoard(player, h.settings(h.provider("T", lines), "UP"));

        h.update(board);

        CharacterizationBoardRenderHarness.ScoreboardModel model = h.ownedScoreboard();
        assertEquals(15, model.scores.size());
        assertEquals("Line14", model.teams.get(T0).prefix);
        assertEquals("Line0", model.teams.get("§e§r").prefix);
    }

    @Test
    public void shrinkingLineCountClearsScoresButLeavesStaleTeams() {
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        CharacterizationBoardRenderHarness.ProviderHandle provider =
                h.provider(() -> "T", () -> List.of("A", "B", "C", "D", "E"));
        Object board = h.newBoard(player, h.settings(provider, "DOWN"));

        h.update(board);
        provider.lines = () -> List.of("A2", "B2", "C2");
        h.update(board);

        CharacterizationBoardRenderHarness.ScoreboardModel model = h.ownedScoreboard();
        assertEquals(Map.of(T0, 15, T1, 14, T2, 13), model.scores);
        assertEquals("A2", model.teams.get(T0).prefix);
        assertEquals("C2", model.teams.get(T2).prefix);
        // The dropped rows' teams survive with stale text; only their scores are gone, which is
        // what hides the rows on the client.
        assertEquals("D", model.teams.get(T3).prefix);
        assertEquals("E", model.teams.get(T4).prefix);
    }

    @Test
    public void growingLineCountReusesExistingTeams() {
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        CharacterizationBoardRenderHarness.ProviderHandle provider =
                h.provider(() -> "T", () -> List.of("A", "B"));
        Object board = h.newBoard(player, h.settings(provider, "DOWN"));

        h.update(board);
        provider.lines = () -> List.of("A2", "B2", "C2", "D2");
        h.update(board);

        CharacterizationBoardRenderHarness.ScoreboardModel model = h.ownedScoreboard();
        assertEquals(Map.of(T0, 15, T1, 14, T2, 13, T3, 12), model.scores);
        assertEquals("A2", model.teams.get(T0).prefix);
        assertEquals("D2", model.teams.get(T3).prefix);
        // Teams registered on the first pass are reused, and each entry key was added exactly once.
        assertEquals(List.of(T0), model.teams.get(T0).entries);
    }

    @Test
    public void identicalSecondUpdateLeavesClientStateIdentical() {
        // The elision-tolerance pin: Board may skip redundant writes as long as the resulting
        // client-visible state is exactly the state a full re-send would have produced.
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        Object board = h.newBoard(player, h.settings(h.provider("§6Title", List.of("§aOne", "§bTwo")), "DOWN"));

        h.update(board);
        Map<String, Object> firstState = h.ownedScoreboard().stateView();
        h.update(board);

        assertEquals(firstState, h.ownedScoreboard().stateView());
    }

    @Test
    public void titleLongerThanThirtyTwoIsClippedAfterColorTranslation() {
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        String rawTitle = "&6" + "1234567890123456789012345678901234"; // 2 + 34 = 36 raw chars
        Object board = h.newBoard(player, h.settings(h.provider(rawTitle, List.of("Line")), "DOWN"));

        h.update(board);

        assertEquals("§6" + "123456789012345678901234567890", h.ownedScoreboard().objectiveDisplayName);
    }

    @Test
    public void nullTitleRendersEmptyDisplayName() {
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        Object board = h.newBoard(player, h.settings(h.provider((String) null, List.of("Line")), "DOWN"));

        h.update(board);

        assertEquals("", h.ownedScoreboard().objectiveDisplayName);
    }

    @Test
    public void titleIsNormalizedBeforeTheScoreboardIsAssigned() {
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        h.newBoard(player, h.settings(h.provider("&6Top\r\nBottom", List.of("Line")), "DOWN"));

        assertEquals("§6Top Bottom", h.ownedScoreboard().objectiveInitialDisplayName);
        assertNull(h.ownedScoreboard().displaySlot);
    }

    @Test
    public void titleLimitNeverSplitsACharacterOrFormattingPair() {
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        String prefix = "1234567890123456789012345678901";
        h.newBoard(player, h.settings(h.provider(prefix + "😀§a", List.of("Line")), "DOWN"));

        assertEquals(prefix, h.ownedScoreboard().objectiveInitialDisplayName);
    }

    @Test
    public void lineBreaksCannotCreateExtraRows() {
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        Object board = h.newBoard(player, h.settings(h.provider("T", List.of("One\r\nTwo\nThree")), "DOWN"));

        h.update(board);

        CharacterizationBoardRenderHarness.ScoreboardModel model = h.ownedScoreboard();
        assertEquals("One Two Three", model.teams.get(T0).prefix);
        assertEquals(1, model.scores.size());
    }

    @Test
    public void nullLineRendersAsAnEmptyRow() {
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        Object board = h.newBoard(player, h.settings(h.provider("T", Collections.singletonList(null)), "DOWN"));

        h.update(board);

        CharacterizationBoardRenderHarness.ScoreboardModel model = h.ownedScoreboard();
        assertEquals("", model.teams.get(T0).prefix);
        assertEquals("", model.teams.get(T0).suffix);
        assertEquals(1, model.scores.size());
    }

    @Test
    public void emptyLineProducesBlankTeamThatStillHoldsItsRow() {
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        Object board = h.newBoard(player, h.settings(h.provider("T", List.of("")), "DOWN"));

        h.update(board);

        CharacterizationBoardRenderHarness.ScoreboardModel model = h.ownedScoreboard();
        assertEquals("", model.teams.get(T0).prefix);
        assertEquals("", model.teams.get(T0).suffix);
        assertEquals(Map.of(T0, 15), model.scores);
    }

    @Test
    public void duplicateLinesOccupyDistinctTeamsAndRows() {
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        Object board = h.newBoard(player, h.settings(h.provider("T", List.of("Same", "Same")), "DOWN"));

        h.update(board);

        CharacterizationBoardRenderHarness.ScoreboardModel model = h.ownedScoreboard();
        assertEquals("Same", model.teams.get(T0).prefix);
        assertEquals("Same", model.teams.get(T1).prefix);
        assertEquals(Map.of(T0, 15, T1, 14), model.scores);
    }

    @Test
    public void nullBoardSettingsMakesUpdateANoOp() {
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        CharacterizationBoardRenderHarness.ProviderHandle provider = h.provider("T", List.of("Line"));
        Object board = h.newBoard(player, h.settings(provider, "DOWN"));

        h.update(board);
        Map<String, Object> stateAfterFirst = h.ownedScoreboard().stateView();
        h.setBoardSettings(board, null);
        h.update(board);

        assertEquals(stateAfterFirst, h.ownedScoreboard().stateView());
        assertEquals(1, provider.linesCalls.get());
    }

    @Test
    public void objectiveSetupUsesProviderTitleWithoutDisplayingAnEmptySidebar() {
        CharacterizationBoardRenderHarness h = harness();
        CharacterizationBoardRenderHarness.PlayerHandle player = h.newPlayer();
        h.newBoard(player, h.settings(h.provider("T", List.of("Line")), "DOWN"));

        CharacterizationBoardRenderHarness.ScoreboardModel model = h.ownedScoreboard();
        assertEquals(List.of("board"), model.registeredObjectives);
        assertEquals("T", model.objectiveInitialDisplayName);
        assertNull(model.displaySlot);
        CharacterizationBoardRenderHarness.TeamState setupTeam = model.teams.get("board");
        assertEquals(Boolean.TRUE, setupTeam.allowFriendlyFire);
        assertEquals(Boolean.FALSE, setupTeam.canSeeFriendlyInvisibles);
        assertEquals("", setupTeam.prefix);
        assertEquals("", setupTeam.suffix);
        // No update yet: the titled objective exists but is not displayed and has no rows.
        assertNull(model.objectiveDisplayName);
        assertTrue(model.scores.isEmpty());
    }
}

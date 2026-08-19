package art.arcane.volmlib.util.board;

import org.junit.After;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * Characterization pins for Board lifecycle: normal-backend lease acquisition, the
 * packet-vs-Bukkit path split decision, the UnsupportedOperationException fallback, and
 * remove/restore semantics.
 *
 * <p>Headless reality, pinned as such: the packet sidebar requires craftbukkit/NMS classes that
 * are absent here, so every scenario that selects the packet path ends with an inert board
 * (provider never consulted). What IS observable — and pinned — is the path decision itself:
 * whether the Bukkit scoreboard backend was leased and initialized at all.
 */
public class CharacterizationBoardLifecycleTest {
    private CharacterizationBoardRenderHarness harness;

    @After
    public void tearDown() {
        if (harness != null) {
            harness.close();
            harness = null;
        }
    }

    @Test
    public void constructionLeasesAndAssignsAnOwnedScoreboard() {
        harness = CharacterizationBoardRenderHarness.normal();
        CharacterizationBoardRenderHarness.PlayerHandle player = harness.newPlayer();
        Object board = harness.newBoard(player, harness.settings(harness.provider("T", List.of("Line")), "DOWN"));

        assertEquals(1, harness.newScoreboardCalls());
        // The player was moved onto the freshly leased scoreboard.
        assertEquals(List.of(harness.ownedScoreboard().proxy), player.assignments);
        assertSame(harness.ownedScoreboard().proxy, player.currentScoreboard);
        assertSame(harness.ownedScoreboard().proxy, harness.boardScoreboard(board));
    }

    @Test
    public void withoutScoreboardManagerBoardIsInertHeadless() {
        harness = CharacterizationBoardRenderHarness.withoutScoreboardManager();
        CharacterizationBoardRenderHarness.PlayerHandle player = harness.newPlayer();
        CharacterizationBoardRenderHarness.ProviderHandle provider = harness.provider("T", List.of("Line"));
        Object board = harness.newBoard(player, harness.settings(provider, "DOWN"));

        harness.update(board);

        // No Bukkit backend could be leased and the packet bridge is unsupported headlessly:
        // the board never renders, so the provider is never consulted.
        assertEquals(0, provider.linesCalls.get());
        assertEquals(0, provider.titleCalls.get());
        assertEquals(List.of(), player.assignments);
    }

    @Test
    public void foliaRuntimeSkipsTheBukkitScoreboardBackendEntirely() {
        harness = CharacterizationBoardRenderHarness.folia();
        CharacterizationBoardRenderHarness.PlayerHandle player = harness.newPlayer();
        CharacterizationBoardRenderHarness.ProviderHandle provider = harness.provider("T", List.of("Line"));
        Object board = harness.newBoard(player, harness.settings(provider, "DOWN"));

        harness.update(board);

        // On Folia the board goes straight to the packet path: no scoreboard is ever leased and
        // the player's scoreboard assignment is never touched.
        assertEquals(0, harness.newScoreboardCalls());
        assertEquals(List.of(), player.assignments);
        assertEquals(0, provider.linesCalls.get());
    }

    @Test
    public void canvasRuntimeSkipsTheBukkitScoreboardBackendEntirely() {
        harness = CharacterizationBoardRenderHarness.canvas();
        CharacterizationBoardRenderHarness.PlayerHandle player = harness.newPlayer();
        CharacterizationBoardRenderHarness.ProviderHandle provider = harness.provider("T", List.of("Line"));
        Object board = harness.newBoard(player, harness.settings(provider, "DOWN"));

        harness.update(board);

        assertEquals(0, harness.newScoreboardCalls());
        assertEquals(List.of(), player.assignments);
        assertEquals(0, provider.linesCalls.get());
    }

    @Test
    public void removeRestoresThePreviousScoreboard() {
        harness = CharacterizationBoardRenderHarness.normal();
        CharacterizationBoardRenderHarness.PlayerHandle player = harness.newPlayer();
        Object board = harness.newBoard(player, harness.settings(harness.provider("T", List.of("Line")), "DOWN"));
        harness.update(board);

        harness.remove(board);

        assertSame(player.previousScoreboard, player.currentScoreboard);
    }

    @Test
    public void removeIsIdempotent() {
        harness = CharacterizationBoardRenderHarness.normal();
        CharacterizationBoardRenderHarness.PlayerHandle player = harness.newPlayer();
        Object board = harness.newBoard(player, harness.settings(harness.provider("T", List.of("Line")), "DOWN"));

        harness.remove(board);
        harness.remove(board);

        // Exactly two assignments ever: onto the owned board at construction, back to the
        // previous board on the first remove. The second remove sends nothing.
        assertEquals(List.of(harness.ownedScoreboard().proxy, player.previousScoreboard), player.assignments);
        assertSame(player.previousScoreboard, player.currentScoreboard);
    }

    @Test
    public void removePreservesAScoreboardAssignedByAnotherPlugin() {
        harness = CharacterizationBoardRenderHarness.normal();
        CharacterizationBoardRenderHarness.PlayerHandle player = harness.newPlayer();
        Object board = harness.newBoard(player, harness.settings(harness.provider("T", List.of("Line")), "DOWN"));

        Object foreign = harness.newScoreboardModel().proxy;
        player.currentScoreboard = foreign;
        harness.remove(board);

        assertSame(foreign, player.currentScoreboard);
    }

    @Test
    public void updateAfterRemoveChangesNothing() {
        harness = CharacterizationBoardRenderHarness.normal();
        CharacterizationBoardRenderHarness.PlayerHandle player = harness.newPlayer();
        CharacterizationBoardRenderHarness.ProviderHandle provider =
                harness.provider(() -> "T", () -> List.of("Line"));
        Object board = harness.newBoard(player, harness.settings(provider, "DOWN"));
        harness.update(board);

        harness.remove(board);
        // remove() itself unregisters the "board" objective; the pin is that nothing changes
        // AFTER removal, so snapshot post-remove.
        Map<String, Object> stateBefore = harness.ownedScoreboard().stateView();
        int linesCallsBefore = provider.linesCalls.get();
        provider.lines = () -> List.of("Changed");
        harness.update(board);

        assertEquals(stateBefore, harness.ownedScoreboard().stateView());
        assertEquals(linesCallsBefore, provider.linesCalls.get());
    }

    @Test
    public void updateOnOfflinePlayerRetiresTheBoardWithoutScoreboardRestore() {
        harness = CharacterizationBoardRenderHarness.normal();
        CharacterizationBoardRenderHarness.PlayerHandle player = harness.newPlayer();
        CharacterizationBoardRenderHarness.ProviderHandle provider =
                harness.provider(() -> "T", () -> List.of("Line"));
        Object board = harness.newBoard(player, harness.settings(provider, "DOWN"));
        harness.update(board);

        player.online = false;
        harness.update(board);

        // Offline removal deliberately skips the scoreboard restore (nothing can be delivered to
        // an offline player): the player object keeps the owned scoreboard reference.
        assertSame(harness.ownedScoreboard().proxy, player.currentScoreboard);

        // A later update must not resurrect the board even if the player comes back online.
        player.online = true;
        int linesCallsBefore = provider.linesCalls.get();
        harness.update(board);
        assertEquals(linesCallsBefore, provider.linesCalls.get());
    }

    @Test
    public void unsupportedTeamOperationDropsTheNormalBackendAndUnregisters() {
        harness = CharacterizationBoardRenderHarness.normal();
        CharacterizationBoardRenderHarness.PlayerHandle player = harness.newPlayer();
        CharacterizationBoardRenderHarness.ProviderHandle provider =
                harness.provider(() -> "T", () -> List.of("Line"));
        Object board = harness.newBoard(player, harness.settings(provider, "DOWN"));
        harness.update(board);

        CharacterizationBoardRenderHarness.ScoreboardModel model = harness.ownedScoreboard();
        model.teams.get("§0§r").throwUnsupportedOnPrefix = true;
        // The line has to actually change: Board only writes a prefix when the rendered line
        // differs from what it last applied, so an identical re-update never reaches setPrefix.
        // (In the real failure mode the backend rejects prefixes from the very first write, so the
        // drop still happens on update one.)
        provider.lines = () -> List.of("Changed");
        harness.update(board);

        // The normal backend is abandoned: the "board" objective is unregistered, and with the
        // packet bridge unsupported headlessly the board goes fully inert.
        assertEquals(List.of("board"), model.unregisteredObjectives);
        int linesCallsBefore = provider.linesCalls.get();
        harness.update(board);
        assertEquals(linesCallsBefore, provider.linesCalls.get());
        // The first update's applied rows are untouched by the failed pass.
        assertEquals(Map.of("§0§r", 15), model.scores);
    }
}

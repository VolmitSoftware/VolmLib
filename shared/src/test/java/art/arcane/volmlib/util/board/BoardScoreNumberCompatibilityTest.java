package art.arcane.volmlib.util.board;

import org.bukkit.entity.Player;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
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
    public void detachedObjectiveArgumentsCoverLegacyAndModernConstructors() throws Exception {
        Class<?> packetBridgeClass = packetBridgeClass();
        Method argumentsMethod = packetBridgeClass.getDeclaredMethod(
                "objectiveConstructorArguments",
                int.class,
                Object.class,
                String.class,
                Object.class,
                Object.class,
                Object.class,
                Object.class
        );
        argumentsMethod.setAccessible(true);

        Object scoreboard = new Object();
        Object criteria = new Object();
        Object component = new Object();
        Object renderType = new Object();
        Object numberFormat = new Object();

        Object[] legacy = (Object[]) argumentsMethod.invoke(
                null,
                5,
                scoreboard,
                "objective",
                criteria,
                component,
                renderType,
                numberFormat
        );
        Object[] modern = (Object[]) argumentsMethod.invoke(
                null,
                7,
                scoreboard,
                "objective",
                criteria,
                component,
                renderType,
                numberFormat
        );

        assertArrayEquals(new Object[]{scoreboard, "objective", criteria, component, renderType}, legacy);
        assertArrayEquals(
                new Object[]{scoreboard, "objective", criteria, component, renderType, Boolean.TRUE, numberFormat},
                modern
        );
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

    private static Class<?> packetBridgeClass() {
        for (Class<?> nestedClass : Board.class.getDeclaredClasses()) {
            if (nestedClass.getSimpleName().equals("PacketBridge")) {
                return nestedClass;
            }
        }
        throw new IllegalStateException("Board.PacketBridge was not found.");
    }
}

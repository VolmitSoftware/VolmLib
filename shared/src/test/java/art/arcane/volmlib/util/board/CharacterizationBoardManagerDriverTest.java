package art.arcane.volmlib.util.board;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Characterization pins for the board update cadence driver: what {@code BoardManager} schedules,
 * at which interval, and how {@code BoardUpdateTask} gates per-player updates on online state.
 *
 * <p>Headless notes, pinned as such:
 * <ul>
 *   <li>The test source set's stub {@code org.bukkit.entity.Player} lacks {@code getScoreboard},
 *       so the non-Folia branch of {@code BoardManager.setup} (the main-scoreboard swap) cannot
 *       execute here; setup semantics are pinned on the Folia-flagged branch, and boards are
 *       injected reflectively where the non-Folia driver is under test.</li>
 *   <li>Folia threading is simulated via {@code FoliaScheduler.forcedFoliaThreading}, reset after
 *       every test exactly like {@code FoliaSchedulerTest} does.</li>
 * </ul>
 */
public class CharacterizationBoardManagerDriverTest {
    private Server server;
    private JavaPlugin plugin;
    private final List<Player> onlinePlayers = new ArrayList<>();
    private final Map<UUID, Player> playersById = new HashMap<>();
    private final List<Object[]> scheduledTimers = new ArrayList<>(); // {runnable, delay, period}
    private final boolean[] taskCancelled = new boolean[1];

    @Before
    public void setUp() throws Exception {
        resetStaticState();
        BukkitTask task = proxy(BukkitTask.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "cancel" -> {
                taskCancelled[0] = true;
                yield null;
            }
            case "isCancelled" -> taskCancelled[0];
            case "getTaskId" -> 1;
            default -> defaultValue(method.getReturnType());
        });
        BukkitScheduler scheduler = proxy(BukkitScheduler.class, (proxy, method, arguments) -> {
            if (method.getName().equals("runTaskTimer")
                    && arguments != null
                    && arguments.length == 4
                    && arguments[1] instanceof Runnable runnable) {
                scheduledTimers.add(new Object[]{runnable, arguments[2], arguments[3]});
                return task;
            }
            return defaultValue(method.getReturnType());
        });
        server = proxy(Server.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "getLogger" -> Logger.getLogger("CharacterizationBoardManager");
            case "getName" -> "CharacterizationServer";
            case "getVersion", "getBukkitVersion" -> "1.20.1-characterization";
            case "getScheduler" -> scheduler;
            case "getOnlinePlayers" -> new ArrayList<>(onlinePlayers);
            case "getPlayer" -> arguments != null && arguments.length == 1 && arguments[0] instanceof UUID id
                    ? playersById.get(id)
                    : null;
            case "getScoreboardManager" -> null;
            case "isPrimaryThread" -> true;
            case "equals" -> proxy == arguments[0];
            case "hashCode" -> System.identityHashCode(proxy);
            default -> defaultValue(method.getReturnType());
        });
        setStaticField(Bukkit.class, "server", server);
        plugin = mock(JavaPlugin.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.isEnabled()).thenReturn(true);
    }

    @After
    public void tearDown() throws Exception {
        resetStaticState();
    }

    @Test
    public void driverSchedulesUpdateTaskAtConfiguredCadence() {
        BoardSettings settings = new BoardSettings(null, ScoreDirection.DOWN, 7);
        new BoardManager<>(plugin, settings, (player, boardSettings) -> mock(Board.class));

        assertEquals(1, scheduledTimers.size());
        assertTrue(scheduledTimers.get(0)[0] instanceof BoardUpdateTask);
        assertEquals(2L, scheduledTimers.get(0)[1]);
        assertEquals(7L, scheduledTimers.get(0)[2]);
    }

    @Test
    public void nullSettingsDriveAtTwentyTickDefault() {
        new BoardManager<Board>(plugin, null, (player, boardSettings) -> mock(Board.class));

        assertEquals(1, scheduledTimers.size());
        assertEquals(2L, scheduledTimers.get(0)[1]);
        assertEquals(20L, scheduledTimers.get(0)[2]);
    }

    @Test
    public void constructionDoesNotInstallBoardsForUnselectedOnlinePlayers() {
        Player player = player(UUID.randomUUID());
        onlinePlayers.add(player);
        List<Player> factoryCalls = new ArrayList<>();

        BoardManager<Board> manager = new BoardManager<>(plugin,
                new BoardSettings(null, ScoreDirection.DOWN, 5),
                (candidate, boardSettings) -> {
                    factoryCalls.add(candidate);
                    return mock(Board.class);
                });

        assertTrue(factoryCalls.isEmpty());
        assertFalse(manager.hasBoard(player));
    }

    @Test
    public void boardSettingsClampIntervalToAtLeastOneTick() {
        assertEquals(1, new BoardSettings(null, ScoreDirection.DOWN, 0).getUpdateIntervalTicks());
        assertEquals(1, new BoardSettings(null, ScoreDirection.DOWN, -20).getUpdateIntervalTicks());
        assertEquals(20, new BoardSettings(null, ScoreDirection.DOWN).getUpdateIntervalTicks());
        assertEquals(20, BoardSettings.builder().scoreDirection(ScoreDirection.UP).build().getUpdateIntervalTicks());
    }

    @Test
    public void scheduledTaskUpdatesOnlyBoardsWhosePlayersAreOnline() throws Exception {
        BoardSettings settings = new BoardSettings(null, ScoreDirection.DOWN, 5);
        BoardManager<Board> manager = new BoardManager<>(plugin, settings, (player, boardSettings) -> mock(Board.class));

        Player online = player(UUID.randomUUID());
        Player offline = player(UUID.randomUUID());
        Board onlineBoard = mock(Board.class);
        Board offlineBoard = mock(Board.class);
        boardsOf(manager).put(online.getUniqueId(), onlineBoard);
        boardsOf(manager).put(offline.getUniqueId(), offlineBoard);
        playersById.put(online.getUniqueId(), online);

        ((Runnable) scheduledTimers.get(0)[0]).run();

        verify(onlineBoard).update();
        verify(offlineBoard, never()).update();
    }

    @Test
    public void foliaSetupInstallsBoardWithoutTouchingPlayerScoreboards() throws Exception {
        setStaticField(FoliaScheduler.class, "forcedFoliaThreading", true);
        BoardSettings settings = new BoardSettings(null, ScoreDirection.DOWN, 5);
        List<Object[]> factoryCalls = new ArrayList<>();
        BoardManager<Board> manager = new BoardManager<>(plugin, settings, (player, boardSettings) -> {
            Board board = mock(Board.class);
            factoryCalls.add(new Object[]{player, boardSettings, board});
            return board;
        });

        Player player = player(UUID.randomUUID());
        manager.setup(player);

        assertTrue(manager.hasBoard(player));
        assertEquals(1, factoryCalls.size());
        assertSame(player, factoryCalls.get(0)[0]);
        assertSame(settings, factoryCalls.get(0)[1]);
        assertSame(factoryCalls.get(0)[2], manager.getBoard(player).orElseThrow());
        verify((Board) factoryCalls.get(0)[2]).update();
    }

    @Test
    public void reSetupResetsAndReplacesTheExistingBoard() throws Exception {
        setStaticField(FoliaScheduler.class, "forcedFoliaThreading", true);
        BoardManager<Board> manager = new BoardManager<>(plugin,
                new BoardSettings(null, ScoreDirection.DOWN, 5),
                (player, boardSettings) -> mock(Board.class));

        Player player = player(UUID.randomUUID());
        manager.setup(player);
        Board first = manager.getBoard(player).orElseThrow();
        manager.setup(player);
        Board second = manager.getBoard(player).orElseThrow();

        verify(first).resetScoreboard();
        assertNotNull(second);
        assertFalse(first == second);
    }

    @Test
    public void removeDropsTheBoardAndResetsIt() throws Exception {
        setStaticField(FoliaScheduler.class, "forcedFoliaThreading", true);
        BoardManager<Board> manager = new BoardManager<>(plugin,
                new BoardSettings(null, ScoreDirection.DOWN, 5),
                (player, boardSettings) -> mock(Board.class));

        Player player = player(UUID.randomUUID());
        manager.setup(player);
        Board board = manager.getBoard(player).orElseThrow();

        manager.remove(player);

        verify(board).remove();
        assertFalse(manager.hasBoard(player));
        // Removing an absent player is harmless.
        manager.remove(player);
    }

    @Test
    public void setBoardSettingsPropagatesToLiveBoardsAndFutureSetups() throws Exception {
        setStaticField(FoliaScheduler.class, "forcedFoliaThreading", true);
        BoardSettings initial = new BoardSettings(null, ScoreDirection.DOWN, 5);
        List<Object[]> factoryCalls = new ArrayList<>();
        BoardManager<Board> manager = new BoardManager<>(plugin, initial, (player, boardSettings) -> {
            Board board = mock(Board.class);
            factoryCalls.add(new Object[]{player, boardSettings, board});
            return board;
        });
        Player existing = player(UUID.randomUUID());
        manager.setup(existing);
        Board existingBoard = manager.getBoard(existing).orElseThrow();

        BoardSettings replacement = new BoardSettings(null, ScoreDirection.UP, 9);
        manager.setBoardSettings(replacement);

        verify(existingBoard).setBoardSettings(replacement);
        Player later = player(UUID.randomUUID());
        manager.setup(later);
        assertSame(replacement, factoryCalls.get(factoryCalls.size() - 1)[1]);
    }

    @Test
    public void onDisableCancelsTheDriverAndRemovesOnlineBoards() throws Exception {
        BoardManager<Board> manager = new BoardManager<>(plugin,
                new BoardSettings(null, ScoreDirection.DOWN, 5),
                (player, boardSettings) -> mock(Board.class));

        Player player = player(UUID.randomUUID());
        Board board = mock(Board.class);
        boardsOf(manager).put(player.getUniqueId(), board);
        onlinePlayers.add(player);

        manager.onDisable();

        assertTrue(taskCancelled[0]);
        verify(board).remove();
        assertTrue(manager.getScoreboards().isEmpty());
    }

    // ------------------------------------------------------------------ helpers

    private static Player player(UUID id) {
        return new Player() {
            @Override
            public UUID getUniqueId() {
                return id;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Board> boardsOf(BoardManager<Board> manager) throws Exception {
        Field field = BoardManager.class.getDeclaredField("scoreboards");
        field.setAccessible(true);
        return (Map<UUID, Board>) field.get(manager);
    }

    private static void resetStaticState() throws Exception {
        setStaticField(Bukkit.class, "server", null);
        setStaticField(FoliaScheduler.class, "forcedFoliaThreading", false);
        setStaticField(FoliaScheduler.class, "globalRegionSchedulerHandle", null);
        setStaticField(FoliaScheduler.class, "regionSchedulerHandle", null);
        setStaticField(FoliaScheduler.class, "asyncSchedulerHandle", null);
    }

    private static void setStaticField(Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        return 0;
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}

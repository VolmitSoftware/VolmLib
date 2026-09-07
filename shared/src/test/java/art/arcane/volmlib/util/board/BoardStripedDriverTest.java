package art.arcane.volmlib.util.board;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins the striped board driver: the timer fires every tick, and each registered board is rendered
 * exactly once per configured interval with roughly {@code boards / interval} renders per tick
 * instead of the whole fleet inside one tick.
 */
public class BoardStripedDriverTest {
    private Server server;
    private JavaPlugin plugin;
    private final Map<UUID, Player> playersById = new HashMap<>();
    private final List<Object[]> scheduledTimers = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        resetStaticState();
        BukkitTask task = proxy(BukkitTask.class, (proxy, method, arguments) -> defaultValue(method.getReturnType()));
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
            case "getLogger" -> Logger.getLogger("BoardStripedDriverTest");
            case "getName" -> "CharacterizationServer";
            case "getVersion", "getBukkitVersion" -> "1.20.1-characterization";
            case "getScheduler" -> scheduler;
            case "getOnlinePlayers" -> new ArrayList<>(playersById.values());
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
        plugin = Mockito.mock(JavaPlugin.class);
        Mockito.when(plugin.getServer()).thenReturn(server);
        Mockito.when(plugin.isEnabled()).thenReturn(true);
    }

    @After
    public void tearDown() throws Exception {
        resetStaticState();
    }

    @Test
    public void theDriverTimerFiresEveryTickRegardlessOfTheConfiguredInterval() {
        new BoardManager<>(plugin, new BoardSettings(null, ScoreDirection.DOWN, 7),
                (player, boardSettings) -> countingBoard(new int[1]));

        assertEquals(1, scheduledTimers.size());
        assertEquals(2L, scheduledTimers.get(0)[1]);
        assertEquals(1L, scheduledTimers.get(0)[2]);
    }

    @Test
    public void everyBoardRendersExactlyOncePerIntervalAndTheCostIsSpread() throws Exception {
        BoardManager<Board> manager = manager(5);
        Map<UUID, int[]> counters = installBoards(manager, 10);
        Runnable driver = driver();

        List<Integer> perTick = new ArrayList<>();
        for (int tick = 0; tick < 5; tick++) {
            int before = total(counters);
            driver.run();
            perTick.add(total(counters) - before);
        }

        for (Map.Entry<UUID, int[]> entry : counters.entrySet()) {
            assertEquals("board " + entry.getKey(), 1, entry.getValue()[0]);
        }
        for (int count : perTick) {
            assertTrue("stripe size " + count + " should stay near boards/interval", count <= 2);
        }
    }

    @Test
    public void unevenFleetsStillCompleteWithinOneInterval() throws Exception {
        BoardManager<Board> manager = manager(4);
        Map<UUID, int[]> counters = installBoards(manager, 7);
        Runnable driver = driver();

        for (int tick = 0; tick < 4; tick++) {
            driver.run();
        }

        for (int[] counter : counters.values()) {
            assertEquals(1, counter[0]);
        }
    }

    @Test
    public void aSecondIntervalRendersEveryBoardAgainExactlyOnce() throws Exception {
        BoardManager<Board> manager = manager(3);
        Map<UUID, int[]> counters = installBoards(manager, 6);
        Runnable driver = driver();

        for (int tick = 0; tick < 6; tick++) {
            driver.run();
        }

        for (int[] counter : counters.values()) {
            assertEquals(2, counter[0]);
        }
    }

    @Test
    public void boardsRegisteredMidCycleJoinTheNextCycle() throws Exception {
        BoardManager<Board> manager = manager(4);
        Map<UUID, int[]> counters = installBoards(manager, 4);
        Runnable driver = driver();

        driver.run();
        int[] lateCounter = new int[1];
        UUID late = install(manager, lateCounter);
        for (int tick = 1; tick < 4; tick++) {
            driver.run();
        }
        assertEquals(0, lateCounter[0]);

        for (int tick = 0; tick < 4; tick++) {
            driver.run();
        }
        assertEquals(1, lateCounter[0]);
        assertEquals(2, counters.values().iterator().next()[0]);
        playersById.remove(late);
    }

    @Test
    public void boardsRemovedMidCycleAreNotRendered() throws Exception {
        BoardManager<Board> manager = manager(4);
        Map<UUID, int[]> counters = installBoards(manager, 4);
        Runnable driver = driver();

        driver.run();
        UUID doomed = null;
        for (Map.Entry<UUID, int[]> entry : counters.entrySet()) {
            if (entry.getValue()[0] == 0) {
                doomed = entry.getKey();
                break;
            }
        }
        boardsOf(manager).remove(doomed);
        for (int tick = 1; tick < 4; tick++) {
            driver.run();
        }

        assertEquals(0, counters.get(doomed)[0]);
    }

    @Test
    public void aOneTickIntervalRendersEverythingWithoutBuildingACycle() throws Exception {
        BoardManager<Board> manager = manager(1);
        Map<UUID, int[]> counters = installBoards(manager, 5);
        Runnable driver = driver();

        driver.run();
        driver.run();

        for (int[] counter : counters.values()) {
            assertEquals(2, counter[0]);
        }
        assertTrue("a one-stripe cycle needs no roster snapshot or sort", cycleOrderOf(manager).isEmpty());
    }

    @Test
    public void offlinePlayersAreSkippedForTheWholeInterval() throws Exception {
        BoardManager<Board> manager = manager(3);
        int[] onlineCounter = new int[1];
        int[] offlineCounter = new int[1];
        install(manager, onlineCounter);
        UUID offline = install(manager, offlineCounter);
        playersById.remove(offline);
        Runnable driver = driver();

        for (int tick = 0; tick < 3; tick++) {
            driver.run();
        }

        assertEquals(1, onlineCounter[0]);
        assertEquals(0, offlineCounter[0]);
    }

    // ------------------------------------------------------------------ helpers

    private BoardManager<Board> manager(int intervalTicks) {
        return new BoardManager<>(plugin, new BoardSettings(null, ScoreDirection.DOWN, intervalTicks),
                (player, boardSettings) -> countingBoard(new int[1]));
    }

    private Runnable driver() {
        return (Runnable) scheduledTimers.get(scheduledTimers.size() - 1)[0];
    }

    private Map<UUID, int[]> installBoards(BoardManager<Board> manager, int count) throws Exception {
        Map<UUID, int[]> counters = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            int[] counter = new int[1];
            counters.put(install(manager, counter), counter);
        }
        return counters;
    }

    private UUID install(BoardManager<Board> manager, int[] counter) throws Exception {
        UUID uuid = UUID.randomUUID();
        playersById.put(uuid, player(uuid));
        boardsOf(manager).put(uuid, countingBoard(counter));
        return uuid;
    }

    private static Board countingBoard(int[] counter) {
        Board board = Mockito.mock(Board.class);
        Mockito.doAnswer(invocation -> {
            counter[0]++;
            return null;
        }).when(board).update();
        return board;
    }

    private static int total(Map<UUID, int[]> counters) {
        int total = 0;
        for (int[] counter : counters.values()) {
            total += counter[0];
        }
        return total;
    }

    private static Player player(UUID id) {
        return new Player() {
            @Override
            public UUID getUniqueId() {
                return id;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static List<UUID> cycleOrderOf(BoardManager<Board> manager) throws Exception {
        Field field = BoardManager.class.getDeclaredField("stripeOrder");
        field.setAccessible(true);
        return (List<UUID>) field.get(manager);
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

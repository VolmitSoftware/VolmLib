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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A board render reads live player state and resolves placeholders. On a regionized runtime that
 * work belongs on the thread owning the player, but the Folia driver ticks on the async scheduler:
 * it used to call {@code Board.update()} straight from there. These pin the dispatch decision.
 */
public class BoardManagerFoliaDispatchTest {
    private Server server;
    private JavaPlugin plugin;
    private final Map<UUID, Player> playersById = new HashMap<>();

    @Before
    public void setUp() throws Exception {
        resetStaticState();
        BukkitTask task = proxy(BukkitTask.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "getTaskId" -> 1;
            default -> defaultValue(method.getReturnType());
        });
        BukkitScheduler scheduler = proxy(BukkitScheduler.class, (proxy, method, arguments) ->
                method.getName().equals("runTaskTimer") && arguments != null && arguments.length == 4
                        ? task
                        : defaultValue(method.getReturnType()));
        server = proxy(Server.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "getLogger" -> Logger.getLogger("BoardManagerFoliaDispatch");
            case "getName" -> "CharacterizationServer";
            case "getVersion", "getBukkitVersion" -> "1.20.1-characterization";
            case "getScheduler" -> scheduler;
            case "getOnlinePlayers" -> new ArrayList<Player>();
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
    public void foliaDriverNeverRendersABoardOnItsOwnThread() throws Exception {
        setStaticField(FoliaScheduler.class, "forcedFoliaThreading", true);
        BoardManager<Board> manager = manager();
        Board board = registerBoard(manager, player(UUID.randomUUID()));

        updateAll(manager);

        // The render was handed to the player's entity scheduler; nothing ran inline. (This fake
        // runtime has no entity scheduler, so the dispatch simply finds no home — which is exactly
        // the observation being pinned: the driver thread did not do the work itself.)
        verify(board, never()).update();
    }

    @Test
    public void thePaperDriverStillRendersInline() throws Exception {
        BoardManager<Board> manager = manager();
        Board board = registerBoard(manager, player(UUID.randomUUID()));

        updateAll(manager);

        verify(board).update();
    }

    @Test
    public void offlinePlayersAreSkippedOnBothPaths() throws Exception {
        BoardManager<Board> manager = manager();
        Player offline = player(UUID.randomUUID());
        Board board = mock(Board.class);
        boardsOf(manager).put(offline.getUniqueId(), board);

        updateAll(manager);

        verify(board, never()).update();
    }

    // ------------------------------------------------------------------ helpers

    private BoardManager<Board> manager() {
        return new BoardManager<>(plugin,
                new BoardSettings(null, ScoreDirection.DOWN, 5),
                (player, boardSettings) -> mock(Board.class));
    }

    private Board registerBoard(BoardManager<Board> manager, Player player) throws Exception {
        Board board = mock(Board.class);
        boardsOf(manager).put(player.getUniqueId(), board);
        playersById.put(player.getUniqueId(), player);
        return board;
    }

    private static void updateAll(BoardManager<Board> manager) throws Exception {
        Method updateAll = BoardManager.class.getDeclaredMethod("updateAll");
        updateAll.setAccessible(true);
        updateAll.invoke(manager);
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

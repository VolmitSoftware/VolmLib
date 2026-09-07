package art.arcane.volmlib.util.board;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

public class BoardManager<B extends Board> {
    private static final long DRIVER_PERIOD_TICKS = 1L;
    private static final int DEFAULT_INTERVAL_TICKS = 20;

    private final JavaPlugin plugin;
    private final BiFunction<Player, BoardSettings, B> boardFactory;
    private final Map<UUID, B> scoreboards;
    private final boolean foliaRuntime;
    private final List<UUID> stripeOrder;
    private int stripeIndex;
    private int stripeCursor;
    private volatile boolean stopped;
    private BukkitTask updateTask;
    private volatile BoardSettings boardSettings;

    public BoardManager(JavaPlugin plugin, BoardSettings boardSettings, BiFunction<Player, BoardSettings, B> boardFactory) {
        this.plugin = plugin;
        this.boardSettings = boardSettings;
        this.boardFactory = boardFactory;
        this.scoreboards = new ConcurrentHashMap<>();
        this.foliaRuntime = FoliaScheduler.isFolia(plugin.getServer());
        this.stripeOrder = new ArrayList<>();
        this.stripeIndex = 0;
        this.stripeCursor = 0;
        startDriver();
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public void setBoardSettings(BoardSettings boardSettings) {
        this.boardSettings = boardSettings;
        scoreboards.values().forEach(board -> board.setBoardSettings(boardSettings));
    }

    public boolean hasBoard(Player player) {
        return scoreboards.containsKey(player.getUniqueId());
    }

    public Optional<B> getBoard(Player player) {
        return Optional.ofNullable(scoreboards.get(player.getUniqueId()));
    }

    public void setup(Player player) {
        Optional.ofNullable(scoreboards.remove(player.getUniqueId())).ifPresent(Board::resetScoreboard);
        B board = boardFactory.apply(player, boardSettings);
        scoreboards.put(player.getUniqueId(), board);
        board.update();
    }

    public void remove(Player player) {
        Optional.ofNullable(scoreboards.remove(player.getUniqueId())).ifPresent(Board::remove);
    }

    public Map<UUID, B> getScoreboards() {
        return Collections.unmodifiableMap(scoreboards);
    }

    public void onDisable() {
        stopped = true;
        if (updateTask != null) {
            updateTask.cancel();
        }
        plugin.getServer().getOnlinePlayers().forEach(this::remove);
        scoreboards.clear();
    }

    /**
     * Renders the slice of boards owned by this tick. The driver fires every tick and walks a
     * cycle snapshot taken at the start of each interval, so every board still renders exactly once
     * per configured interval while the cost is spread over the interval instead of landing in one
     * tick. Boards registered mid-cycle join the next cycle; boards removed mid-cycle are skipped.
     */
    void updateStripe() {
        int intervalTicks = intervalTicks();
        // At a one-tick interval every tick is a whole cycle, so the roster snapshot and its sort
        // would be pure waste on the hottest path this driver has.
        if (intervalTicks <= 1) {
            stripeIndex = 0;
            stripeOrder.clear();
            stripeCursor = 0;
            for (Map.Entry<UUID, B> entry : scoreboards.entrySet()) {
                updateOne(entry.getKey(), entry.getValue());
            }
            return;
        }
        if (stripeIndex >= intervalTicks) {
            stripeIndex = 0;
        }
        if (stripeIndex == 0) {
            beginCycle();
        }
        int remainingStripes = intervalTicks - stripeIndex;
        int remaining = stripeOrder.size() - stripeCursor;
        if (remaining > 0) {
            int slice = (remaining + remainingStripes - 1) / remainingStripes;
            for (int i = 0; i < slice && stripeCursor < stripeOrder.size(); i++) {
                UUID uuid = stripeOrder.get(stripeCursor++);
                updateOne(uuid, scoreboards.get(uuid));
            }
        }
        stripeIndex++;
    }

    private void beginCycle() {
        stripeOrder.clear();
        stripeOrder.addAll(scoreboards.keySet());
        Collections.sort(stripeOrder);
        stripeCursor = 0;
    }

    private void updateOne(UUID uuid, B board) {
        if (board == null) {
            return;
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return;
        }
        // The Folia driver ticks on the async scheduler, but a board render reads live player state
        // and resolves placeholders, which on a regionized runtime must happen on the thread that
        // owns the player. Hand each render to that player's entity scheduler.
        if (foliaRuntime) {
            FoliaScheduler.runEntity(plugin, player, board::update);
            return;
        }
        board.update();
    }

    private int intervalTicks() {
        return boardSettings != null ? Math.max(1, boardSettings.getUpdateIntervalTicks()) : DEFAULT_INTERVAL_TICKS;
    }

    private void startDriver() {
        if (foliaRuntime) {
            scheduleFoliaTick();
            return;
        }
        updateTask = new BoardUpdateTask<>(this).runTaskTimer(plugin, 2L, DRIVER_PERIOD_TICKS);
    }

    private void scheduleFoliaTick() {
        boolean scheduled = FoliaScheduler.runAsync(plugin, () -> {
            if (stopped || !plugin.isEnabled()) {
                return;
            }
            updateStripe();
            scheduleFoliaTick();
        }, DRIVER_PERIOD_TICKS);
        if (!scheduled) {
            stopped = true;
        }
    }
}

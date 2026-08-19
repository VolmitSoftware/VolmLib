package art.arcane.volmlib.util.board;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

public class BoardManager<B extends Board> {
    private final JavaPlugin plugin;
    private final BiFunction<Player, BoardSettings, B> boardFactory;
    private final Map<UUID, B> scoreboards;
    private final boolean foliaRuntime;
    private volatile boolean stopped;
    private BukkitTask updateTask;
    private BoardSettings boardSettings;

    public BoardManager(JavaPlugin plugin, BoardSettings boardSettings, BiFunction<Player, BoardSettings, B> boardFactory) {
        this.plugin = plugin;
        this.boardSettings = boardSettings;
        this.boardFactory = boardFactory;
        this.scoreboards = new ConcurrentHashMap<>();
        this.foliaRuntime = FoliaScheduler.isFolia(plugin.getServer());
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

    private void startDriver() {
        long intervalTicks = boardSettings != null ? boardSettings.getUpdateIntervalTicks() : 20L;
        if (foliaRuntime) {
            scheduleFoliaTick(intervalTicks);
            return;
        }
        updateTask = new BoardUpdateTask<>(this).runTaskTimer(plugin, 2L, intervalTicks);
    }

    private void scheduleFoliaTick(long intervalTicks) {
        boolean scheduled = FoliaScheduler.runAsync(plugin, () -> {
            if (stopped || !plugin.isEnabled()) {
                return;
            }
            updateAll();
            scheduleFoliaTick(boardSettings != null ? boardSettings.getUpdateIntervalTicks() : intervalTicks);
        }, intervalTicks);
        if (!scheduled) {
            stopped = true;
        }
    }

    private void updateAll() {
        for (Map.Entry<UUID, B> entry : scoreboards.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }

            B board = entry.getValue();
            // This driver ticks on the async scheduler, but a board render reads live player state
            // and resolves placeholders, which on a regionized runtime must happen on the thread
            // that owns the player. Hand each render to that player's entity scheduler.
            if (foliaRuntime) {
                FoliaScheduler.runEntity(plugin, player, board::update);
                continue;
            }

            board.update();
        }
    }
}

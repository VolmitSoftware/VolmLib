package art.arcane.volmlib.util.board;

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
    private final BukkitTask updateTask;
    private BoardSettings boardSettings;

    public BoardManager(JavaPlugin plugin, BoardSettings boardSettings, BiFunction<Player, BoardSettings, B> boardFactory) {
        this.plugin = plugin;
        this.boardSettings = boardSettings;
        this.boardFactory = boardFactory;
        this.scoreboards = new ConcurrentHashMap<>();
        this.updateTask = new BoardUpdateTask<>(this).runTaskTimer(plugin, 2L, 20L);
        plugin.getServer().getOnlinePlayers().forEach(this::setup);
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
        if (player.getScoreboard().equals(Bukkit.getScoreboardManager().getMainScoreboard())) {
            player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        }

        scoreboards.put(player.getUniqueId(), boardFactory.apply(player, boardSettings));
    }

    public void remove(Player player) {
        Optional.ofNullable(scoreboards.remove(player.getUniqueId())).ifPresent(Board::remove);
    }

    public Map<UUID, B> getScoreboards() {
        return Collections.unmodifiableMap(scoreboards);
    }

    public void onDisable() {
        updateTask.cancel();
        plugin.getServer().getOnlinePlayers().forEach(this::remove);
        scoreboards.clear();
    }
}

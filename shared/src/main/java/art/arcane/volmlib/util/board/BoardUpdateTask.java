package art.arcane.volmlib.util.board;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

public class BoardUpdateTask<B extends Board> extends BukkitRunnable {
    private static final Predicate<UUID> PLAYER_IS_ONLINE = uuid -> Bukkit.getPlayer(uuid) != null;

    private final BoardManager<B> boardManager;

    public BoardUpdateTask(BoardManager<B> boardManager) {
        this.boardManager = boardManager;
    }

    @Override
    public void run() {
        for (Map.Entry<UUID, B> entry : boardManager.getScoreboards().entrySet()) {
            if (!PLAYER_IS_ONLINE.test(entry.getKey())) {
                continue;
            }

            entry.getValue().update();
        }
    }
}

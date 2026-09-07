package art.arcane.volmlib.util.board;

import org.bukkit.scheduler.BukkitRunnable;

public class BoardUpdateTask<B extends Board> extends BukkitRunnable {
    private final BoardManager<B> boardManager;

    public BoardUpdateTask(BoardManager<B> boardManager) {
        this.boardManager = boardManager;
    }

    @Override
    public void run() {
        boardManager.updateStripe();
    }
}

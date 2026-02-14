package art.arcane.volmlib.util.board;

import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

public class Board {
    private static final String[] CACHED_ENTRIES = new String[ChatColor.values().length];
    private static final UnaryOperator<String> APPLY_COLOR_TRANSLATION =
            s -> ChatColor.translateAlternateColorCodes('&', s);

    static {
        IntStream.range(0, 15).forEach(i -> CACHED_ENTRIES[i] = ChatColor.values()[i].toString() + ChatColor.RESET);
    }

    private final Player player;
    private final Objective objective;
    private BoardSettings boardSettings;
    private boolean ready;

    @SuppressWarnings("deprecation")
    public Board(@NonNull Player player, BoardSettings boardSettings) {
        this.player = player;
        this.boardSettings = boardSettings;
        Objective obj = getScoreboard().getObjective("board");
        this.objective = obj == null
                ? this.getScoreboard().registerNewObjective("board", "dummy", "Iris")
                : obj;
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        Team team = getScoreboard().getTeam("board");
        team = team == null ? getScoreboard().registerNewTeam("board") : team;
        team.setAllowFriendlyFire(true);
        team.setCanSeeFriendlyInvisibles(false);
        team.setPrefix("");
        team.setSuffix("");
        this.ready = true;
    }

    public Scoreboard getScoreboard() {
        return player != null ? player.getScoreboard() : null;
    }

    public void setBoardSettings(BoardSettings boardSettings) {
        this.boardSettings = boardSettings;
    }

    public void remove() {
        resetScoreboard();
    }

    public void update() {
        if (!ready) {
            return;
        }

        if (!player.isOnline()) {
            remove();
            return;
        }

        if (boardSettings == null) {
            return;
        }

        List<String> entries = boardSettings.getBoardProvider().getLines(player);
        entries.replaceAll(APPLY_COLOR_TRANSLATION);

        if (boardSettings.getScoreDirection() == ScoreDirection.UP) {
            Collections.reverse(entries);
        }

        String title = boardSettings.getBoardProvider().getTitle(player);
        if (title.length() > 32) {
            Bukkit.getLogger().warning("The title " + title + " is over 32 characters in length, substringing to prevent errors.");
            title = title.substring(0, 32);
        }
        objective.setDisplayName(ChatColor.translateAlternateColorCodes('&', title));

        if (this.getScoreboard().getEntries().size() != entries.size()) {
            this.getScoreboard().getEntries().forEach(this::removeEntry);
        }

        for (int i = 0; i < entries.size(); i++) {
            String str = entries.get(i);
            BoardEntry entry = BoardEntry.translateToEntry(str);
            Team team = getScoreboard().getTeam(CACHED_ENTRIES[i]);

            if (team == null) {
                team = this.getScoreboard().registerNewTeam(CACHED_ENTRIES[i]);
                team.addEntry(team.getName());
            }

            team.setPrefix(entry.getPrefix());
            team.setSuffix(entry.getSuffix());

            switch (boardSettings.getScoreDirection()) {
                case UP -> objective.getScore(team.getName()).setScore(1 + i);
                case DOWN -> objective.getScore(team.getName()).setScore(15 - i);
            }
        }
    }

    public void removeEntry(String id) {
        this.getScoreboard().resetScores(id);
    }

    public void resetScoreboard() {
        ready = false;
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }
}

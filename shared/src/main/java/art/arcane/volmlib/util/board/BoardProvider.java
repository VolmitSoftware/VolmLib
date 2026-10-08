package art.arcane.volmlib.util.board;

import org.bukkit.entity.Player;

import java.util.List;

public interface BoardProvider {
    String getTitle(Player player);

    List<String> getLines(Player player);

    default BoardTextFormat getTextFormat() {
        return BoardTextFormat.LEGACY;
    }

    default int[] getLineSlots(Player player) {
        return null;
    }

    default boolean hideScoreNumbers(Player player) {
        return true;
    }
}

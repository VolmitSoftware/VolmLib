package art.arcane.volmlib.util.board;

import org.bukkit.entity.Player;

import java.util.List;

public interface BoardProvider {
    String getTitle(Player player);

    List<String> getLines(Player player);
}

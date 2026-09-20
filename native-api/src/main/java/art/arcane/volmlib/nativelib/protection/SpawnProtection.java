package art.arcane.volmlib.nativelib.protection;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public interface SpawnProtection {
    boolean supported();
    Decision check(Player player, Block block);

    enum Decision {
        ALLOWED, PROTECTED, UNSUPPORTED
    }
}

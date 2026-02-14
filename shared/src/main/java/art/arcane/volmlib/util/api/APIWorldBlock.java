package art.arcane.volmlib.util.api;

import org.bukkit.block.Block;

@FunctionalInterface
public interface APIWorldBlock {
    void onWorldPlaced(Block block, String namespace, String key);
}

package art.arcane.volmlib.util.api;

import org.bukkit.block.data.BlockData;

@FunctionalInterface
public interface APIAwareBlock {
    void onPlaced(BlockData block, String namespace, String key, int x, int y, int z);
}

package art.arcane.volmlib.nativelib.monitor;

import org.bukkit.World;

@FunctionalInterface
public interface BrewingTickHook {
    BrewingTickResult decide(World world, int x, int y, int z);
}

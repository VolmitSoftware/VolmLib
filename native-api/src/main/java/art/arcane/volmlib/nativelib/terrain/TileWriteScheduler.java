package art.arcane.volmlib.nativelib.terrain;

import org.bukkit.Location;

public interface TileWriteScheduler {
    boolean owns(Location location);

    boolean schedule(Location location, Runnable update);
}

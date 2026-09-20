package art.arcane.volmlib.nativelib.monitor;

import org.bukkit.Location;
import org.bukkit.World;

public interface ExplosionHook {
    void observe(Location center);

    ExplosionDecision onExplodePacket(World world, double x, double y, double z, float radius);
}

package art.arcane.volmlib.nativelib.monitor;

import org.bukkit.World;

@FunctionalInterface
public interface ExplosionPacketSuppressor {
    boolean shouldSuppress(World world, double x, double y, double z, float radius, int packetCount);
}

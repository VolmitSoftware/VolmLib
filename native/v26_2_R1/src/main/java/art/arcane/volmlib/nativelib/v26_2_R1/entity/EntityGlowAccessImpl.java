package art.arcane.volmlib.nativelib.v26_2_R1.entity;

import art.arcane.volmlib.nativelib.common.entity.GlowingEntities;
import art.arcane.volmlib.nativelib.entity.EntityGlow;
import art.arcane.volmlib.nativelib.entity.EntityGlowAccess;
import org.bukkit.plugin.Plugin;

public final class EntityGlowAccessImpl implements EntityGlowAccess {
    @Override
    public EntityGlow create(Plugin plugin) {
        return new GlowingEntities(plugin);
    }
}

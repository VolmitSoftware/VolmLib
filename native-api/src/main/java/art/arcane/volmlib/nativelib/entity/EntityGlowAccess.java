package art.arcane.volmlib.nativelib.entity;

import art.arcane.volmlib.nativelib.NativeBinding;
import org.bukkit.plugin.Plugin;

@NativeBinding("entity.EntityGlowAccessImpl")
public interface EntityGlowAccess {
    EntityGlow create(Plugin plugin);
}

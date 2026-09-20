package art.arcane.volmlib.nativelib.entity;

import art.arcane.volmlib.nativelib.NativeBinding;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.util.UUID;

@NativeBinding("entity.NativeEntityVisibilityAccess")
public interface EntityVisibilityAccess {
    boolean isVisible(Player observer, UUID entityId, boolean visibleByDefault, Plugin excludedPlugin);
}

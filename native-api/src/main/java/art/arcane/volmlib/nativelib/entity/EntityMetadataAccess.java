package art.arcane.volmlib.nativelib.entity;

import art.arcane.volmlib.nativelib.NativeBinding;
import org.bukkit.entity.Entity;

@NativeBinding("entity.NativeEntityMetadataAccess")
public interface EntityMetadataAccess {
    long fingerprint(Entity entity);
}

package art.arcane.volmlib.nativelib.protection;

import art.arcane.volmlib.nativelib.NativeBinding;
import org.bukkit.Server;

@NativeBinding("protection.NativeSpawnProtectionAccess")
public interface SpawnProtectionAccess {
    SpawnProtection create(Server server);
}

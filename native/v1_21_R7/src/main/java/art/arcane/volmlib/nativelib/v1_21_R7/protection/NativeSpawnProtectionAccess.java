package art.arcane.volmlib.nativelib.v1_21_R7.protection;

import art.arcane.volmlib.nativelib.common.protection.ReflectiveSpawnProtection;
import art.arcane.volmlib.nativelib.protection.SpawnProtection;
import art.arcane.volmlib.nativelib.protection.SpawnProtectionAccess;
import org.bukkit.Server;

public final class NativeSpawnProtectionAccess implements SpawnProtectionAccess {
    @Override
    public SpawnProtection create(Server server) {
        return ReflectiveSpawnProtection.create(server);
    }
}

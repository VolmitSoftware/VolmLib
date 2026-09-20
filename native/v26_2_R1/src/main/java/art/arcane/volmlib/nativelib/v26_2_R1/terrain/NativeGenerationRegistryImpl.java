package art.arcane.volmlib.nativelib.v26_2_R1.terrain;

import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeRegistryDefinitions;
import net.minecraft.core.RegistryAccess;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;

import java.util.function.Supplier;

public final class NativeGenerationRegistryImpl extends NativeRegistryDefinitions {
    public NativeGenerationRegistryImpl() {
        this(() -> ((CraftServer) Bukkit.getServer()).getServer().registryAccess());
    }

    public NativeGenerationRegistryImpl(Supplier<RegistryAccess> registryAccess) {
        super(registryAccess);
    }
}

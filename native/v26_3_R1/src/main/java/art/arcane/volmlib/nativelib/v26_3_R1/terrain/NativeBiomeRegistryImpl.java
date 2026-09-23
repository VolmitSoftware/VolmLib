package art.arcane.volmlib.nativelib.v26_3_R1.terrain;

import art.arcane.volmlib.nativelib.terrain.NativeBiomeRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;

import java.util.Optional;

public final class NativeBiomeRegistryImpl implements NativeBiomeRegistry<Holder<Biome>, Biome> {
    private final Registry<Biome> registry;

    public NativeBiomeRegistryImpl() {
        this(((CraftServer) Bukkit.getServer()).getServer().registryAccess().lookup(Registries.BIOME).orElse(null));
    }

    public NativeBiomeRegistryImpl(Registry<Biome> registry) {
        this.registry = registry;
    }

    @Override
    public Holder<Biome> lookup(String resourceKey) {
        if (registry == null || resourceKey == null || resourceKey.isBlank()) {
            return null;
        }
        Identifier identifier = Identifier.tryParse(resourceKey);
        return identifier == null ? null : registry.get(ResourceKey.create(Registries.BIOME, identifier)).orElse(null);
    }

    @Override
    public Holder<Biome> lookup(org.bukkit.block.Biome biome) {
        return NativeBiomeAccessImpl.biomeToBiomeBase(registry, biome);
    }

    @Override
    public Holder<Biome> lookupCustom(String resourceKey) {
        if (registry == null) {
            return null;
        }
        Identifier identifier = Identifier.tryParse(resourceKey);
        if (identifier == null) {
            throw new IllegalStateException("Invalid custom biome resource key '" + resourceKey + "'.");
        }
        Biome biome = registry.getValue(identifier);
        if (biome == null) {
            return null;
        }
        return registry.getResourceKey(biome).flatMap(registry::get).orElse(null);
    }

    @Override
    public Holder<Biome> first() {
        if (registry == null) {
            return null;
        }
        for (Biome biome : registry) {
            Optional<Holder.Reference<Biome>> holder = registry.getResourceKey(biome).flatMap(registry::get);
            if (holder.isPresent()) {
                return holder.get();
            }
        }
        return null;
    }

    @Override
    public Biome value(Holder<Biome> holder) {
        return holder.value();
    }

    @Override
    public NativeBiomeRegistry<Holder<Biome>, Biome> forWorld(World world) {
        return new NativeBiomeRegistryImpl(((CraftWorld) world).getHandle().registryAccess()
                .lookup(Registries.BIOME).orElse(null));
    }
}

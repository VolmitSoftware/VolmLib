package art.arcane.volmlib.nativelib.terrain;

import org.bukkit.World;
import org.bukkit.block.Biome;

public interface NativeBiomeRegistry<H, V> {
    H lookup(String resourceKey);

    H lookup(Biome biome);

    H lookupCustom(String resourceKey);

    H first();

    V value(H holder);

    NativeBiomeRegistry<H, V> forWorld(World world);
}

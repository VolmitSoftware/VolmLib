package art.arcane.volmlib.nativelib.terrain;

import org.bukkit.NamespacedKey;
import org.bukkit.World;

import java.io.File;

public record WorldRuntimeOptions(String worldName, NamespacedKey worldKey, World.Environment environment,
                                  NamespacedKey dimensionTypeKey, String generatorId, boolean persistent,
                                  long seed, boolean existingWorldData, File levelRoot) {
}

package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.DimensionType;

import java.nio.file.Path;
import java.util.Optional;

public final class NativeWorldPaths {
    private NativeWorldPaths() {
    }

    public static Optional<Path> root(NativeModdedServer server) {
        return server == null ? Optional.empty()
                : Optional.of(server.root().toAbsolutePath().normalize());
    }

    public static Optional<Path> dimensionStorage(Path worldRoot, String dimensionKey) {
        Identifier identifier = Identifier.tryParse(dimensionKey);
        return identifier == null ? Optional.empty() : Optional.of(DimensionType.getStorageFolder(
                ResourceKey.create(Registries.DIMENSION, identifier), worldRoot).toAbsolutePath().normalize());
    }
}

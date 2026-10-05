package art.arcane.volmlib.nativelib.chunk;

import art.arcane.volmlib.nativelib.NativeBinding;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Optional;

@NativeBinding("chunk.NativeChunkPacketAccess")
public interface ChunkPacketAccess {
    boolean supported();

    boolean sendChunk(Player player, World world, int chunkX, int chunkZ) throws ReflectiveOperationException;

    default boolean snapshotSupported() {
        return false;
    }

    default Optional<ChunkWorldContext> context(World world) {
        return Optional.empty();
    }

    default Optional<ChunkPacketSnapshot> snapshot(World world, ChunkPosition position) {
        return Optional.empty();
    }
}

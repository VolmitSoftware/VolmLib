package art.arcane.volmlib.nativelib.chunk;

import art.arcane.volmlib.nativelib.NativeBinding;
import org.bukkit.World;
import org.bukkit.entity.Player;

@NativeBinding("chunk.NativeChunkPacketAccess")
public interface ChunkPacketAccess {
    boolean supported();

    boolean sendChunk(Player player, World world, int chunkX, int chunkZ) throws ReflectiveOperationException;
}

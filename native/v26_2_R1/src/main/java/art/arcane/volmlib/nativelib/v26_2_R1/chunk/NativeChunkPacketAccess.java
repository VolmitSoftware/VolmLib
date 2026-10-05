package art.arcane.volmlib.nativelib.v26_2_R1.chunk;

import art.arcane.volmlib.nativelib.chunk.ChunkPacketSnapshot;
import art.arcane.volmlib.nativelib.chunk.ChunkPosition;
import art.arcane.volmlib.nativelib.chunk.ChunkWorldContext;
import art.arcane.volmlib.nativelib.common.chunk.ReflectiveChunkPacketAccess;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.LevelChunk;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;

import java.util.Objects;
import java.util.Optional;

public final class NativeChunkPacketAccess extends ReflectiveChunkPacketAccess {
    @Override
    public boolean snapshotSupported() {
        try {
            return ClientboundLevelChunkWithLightPacket.STREAM_CODEC != null
                && CraftWorld.class.getMethod("getHandle").getReturnType() == ServerLevel.class;
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return false;
        }
    }

    @Override
    public Optional<ChunkWorldContext> context(World world) {
        Objects.requireNonNull(world, "world");
        if (!snapshotSupported() || !(world instanceof CraftWorld craft)) {
            return Optional.empty();
        }
        ServerLevel level = craft.getHandle();
        return level.dimensionTypeRegistration().unwrapKey().map(type -> new ChunkWorldContext(
            level.dimension().identifier().toString(), type.identifier().toString(), BiomeManager.obfuscateSeed(level.getSeed()),
            level.isDebug(), level.isFlat(), level.getSeaLevel(), level.getMinY(), level.getHeight(),
            level.getGameTime(), level.getDefaultClockTime(), level.getRainLevel(1), level.getThunderLevel(1)));
    }

    @Override
    public Optional<ChunkPacketSnapshot> snapshot(World world, ChunkPosition position) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(position, "position");
        if (!snapshotSupported() || !(world instanceof CraftWorld craft)) {
            return Optional.empty();
        }
        ServerLevel level = craft.getHandle();
        LevelChunk chunk = level.getChunkSource().getChunkNow(position.x(), position.z());
        if (chunk == null) {
            return Optional.empty();
        }
        ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null);
        ByteBuf storage = Unpooled.buffer(4096, ChunkPacketSnapshot.MAX_BYTES);
        try {
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(storage, level.registryAccess());
            ClientboundLevelChunkWithLightPacket.STREAM_CODEC.encode(buffer, packet);
            ClientboundLevelChunkWithLightPacket normalized = ClientboundLevelChunkWithLightPacket.STREAM_CODEC.decode(buffer);
            buffer.clear();
            ClientboundLevelChunkWithLightPacket.STREAM_CODEC.encode(buffer, normalized);
            byte[] payload = new byte[storage.readableBytes()];
            storage.readBytes(payload);
            return Optional.of(new ChunkPacketSnapshot(position, payload));
        } finally {
            storage.release();
        }
    }

}

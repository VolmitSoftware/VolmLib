package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.UUID;

public final class NativeProtocolDelivery {
    private final MinecraftServer server;
    private final NativeProtocolChannel channel;

    public NativeProtocolDelivery(MinecraftServer server, NativeProtocolChannel channel) {
        this.server = Objects.requireNonNull(server, "server");
        this.channel = Objects.requireNonNull(channel, "protocol channel");
    }

    public void send(UUID playerId, byte[] frame) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null && channel.canReceive(player)) {
            channel.send(player, frame);
        }
    }
}

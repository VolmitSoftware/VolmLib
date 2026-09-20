package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class NativeProtocolServer {
    private final MinecraftServer server;
    private final Map<UUID, NativeProtocolPlayer> players = new HashMap<>();

    private NativeProtocolServer(MinecraftServer server) {
        this.server = server;
    }

    public static NativeProtocolServer fromServer(NativeModdedServer server) {
        return new NativeProtocolServer(server.server());
    }

    public NativeProtocolDelivery delivery(NativeProtocolChannel channel) {
        return new NativeProtocolDelivery(server, channel);
    }

    public void forEachPlayer(Consumer<NativeProtocolPlayer> consumer) {
        if (server.getPlayerList() == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            NativeProtocolPlayer context = players.get(player.getUUID());
            if (context == null || !context.represents(player)) {
                context = NativeProtocolPlayer.fromHandle(player);
                players.put(player.getUUID(), context);
            }
            consumer.accept(context);
        }
    }

    public void forget(UUID playerId) {
        players.remove(playerId);
    }
}

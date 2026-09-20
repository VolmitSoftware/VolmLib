package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import net.minecraft.server.level.ServerPlayer;

public interface NativeProtocolChannel {
    boolean canReceive(ServerPlayer player);

    void send(ServerPlayer player, byte[] frame);

    void sendToServer(byte[] frame);
}

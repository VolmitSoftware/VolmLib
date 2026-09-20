package art.arcane.volmlib.nativelib.minecraft26_2.fabric;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativePayloadProtocol;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolCallbacks;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolPlayer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolChannel;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class NativeFabricProtocolNetworking implements NativeProtocolChannel {
    private final NativePayloadProtocol protocol;

    private NativeFabricProtocolNetworking(NativePayloadProtocol protocol) {
        this.protocol = protocol;
    }

    public static NativeProtocolChannel install(NativePayloadProtocol protocol, NativeProtocolCallbacks callbacks) {
        PayloadTypeRegistry.clientboundPlay().register(protocol.type(), protocol.codec());
        PayloadTypeRegistry.serverboundPlay().register(protocol.type(), protocol.codec());
        ServerPlayNetworking.registerGlobalReceiver(protocol.type(),
                (payload, context) -> callbacks.inbound().accept(NativeProtocolPlayer.fromHandle(context.player()), payload.data()));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> callbacks.joined().accept(NativeProtocolPlayer.fromHandle(handler.player)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> callbacks.disconnected().accept(NativeProtocolPlayer.fromHandle(handler.player)));
        return new NativeFabricProtocolNetworking(protocol);
    }

    public static void installClient(NativePayloadProtocol protocol, Consumer<byte[]> inbound) {
        ClientPlayNetworking.registerGlobalReceiver(protocol.type(), (payload, context) -> inbound.accept(payload.data()));
    }

    public static void sendClient(NativePayloadProtocol protocol, byte[] frame) {
        if (ClientPlayNetworking.canSend(protocol.type())) {
            ClientPlayNetworking.send(protocol.payload(frame));
        }
    }

    @Override
    public boolean canReceive(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, protocol.type());
    }

    @Override
    public void send(ServerPlayer player, byte[] frame) {
        ServerPlayNetworking.send(player, protocol.payload(frame));
    }

    @Override
    public void sendToServer(byte[] frame) {
        sendClient(protocol, frame);
    }
}

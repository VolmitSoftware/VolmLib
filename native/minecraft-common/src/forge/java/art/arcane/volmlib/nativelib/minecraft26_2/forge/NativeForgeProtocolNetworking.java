package art.arcane.volmlib.nativelib.minecraft26_2.forge;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativePayloadProtocol;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolCallbacks;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolPlayer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolChannel;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkProtocol;
import net.minecraftforge.network.PacketDistributor;

public final class NativeForgeProtocolNetworking implements NativeProtocolChannel {
    private final NativePayloadProtocol protocol;
    private final Channel<CustomPacketPayload> channel;

    private NativeForgeProtocolNetworking(NativePayloadProtocol protocol, Channel<CustomPacketPayload> channel) {
        this.protocol = protocol;
        this.channel = channel;
    }

    public static NativeProtocolChannel register(NativePayloadProtocol protocol, NativeProtocolCallbacks callbacks,
                                                 Consumer<byte[]> clientInbound) {
        Channel<CustomPacketPayload> channel = ChannelBuilder.named(protocol.type().id())
                .optional().payloadChannel().protocol(NetworkProtocol.PLAY).bidirectional()
                .add(protocol.type(), protocol.codec(), (payload, context) -> onPayload(payload, context, callbacks, clientInbound))
                .build();
        return new NativeForgeProtocolNetworking(protocol, channel);
    }

    private static void onPayload(NativePayloadProtocol.Payload payload, CustomPayloadEvent.Context context,
                                  NativeProtocolCallbacks callbacks, Consumer<byte[]> clientInbound) {
        context.setPacketHandled(true);
        if (context.isClientSide()) {
            context.enqueueWork(() -> clientInbound.accept(payload.data()));
            return;
        }
        ServerPlayer player = context.getSender();
        if (player != null) {
            callbacks.inbound().accept(NativeProtocolPlayer.fromHandle(player), payload.data());
        }
    }

    @Override
    public boolean canReceive(ServerPlayer player) {
        return channel.isRemotePresent(player.connection.getConnection());
    }

    @Override
    public void send(ServerPlayer player, byte[] frame) {
        channel.send(protocol.payload(frame), PacketDistributor.PLAYER.with(player));
    }

    @Override
    public void sendToServer(byte[] frame) {
        channel.send(protocol.payload(frame), PacketDistributor.SERVER.noArg());
    }
}

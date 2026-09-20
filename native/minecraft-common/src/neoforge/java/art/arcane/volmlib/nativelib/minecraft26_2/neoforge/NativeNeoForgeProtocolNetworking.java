package art.arcane.volmlib.nativelib.minecraft26_2.neoforge;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativePayloadProtocol;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolCallbacks;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolPlayer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolChannel;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NativeNeoForgeProtocolNetworking implements NativeProtocolChannel {
    private final NativePayloadProtocol protocol;

    private NativeNeoForgeProtocolNetworking(NativePayloadProtocol protocol) {
        this.protocol = protocol;
    }

    public static NativeProtocolChannel register(IEventBus modBus, NativePayloadProtocol protocol,
                                                 NativeProtocolCallbacks callbacks, Consumer<byte[]> clientInbound) {
        modBus.addListener((RegisterPayloadHandlersEvent event) -> registerPayloads(event, protocol, callbacks, clientInbound));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                callbacks.joined().accept(NativeProtocolPlayer.fromHandle(player));
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                callbacks.disconnected().accept(NativeProtocolPlayer.fromHandle(player));
            }
        });
        return new NativeNeoForgeProtocolNetworking(protocol);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event, NativePayloadProtocol protocol,
                                          NativeProtocolCallbacks callbacks, Consumer<byte[]> clientInbound) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            registrar.playBidirectional(protocol.type(), protocol.codec(),
                    (payload, context) -> inbound(payload, context, callbacks),
                    (payload, context) -> context.enqueueWork(() -> clientInbound.accept(payload.data())));
        } else {
            registrar.playBidirectional(protocol.type(), protocol.codec(),
                    (payload, context) -> inbound(payload, context, callbacks));
        }
    }

    private static void inbound(NativePayloadProtocol.Payload payload, IPayloadContext context, NativeProtocolCallbacks callbacks) {
        if (context.player() instanceof ServerPlayer player) {
            callbacks.inbound().accept(NativeProtocolPlayer.fromHandle(player), payload.data());
        }
    }

    public static void sendClient(NativePayloadProtocol protocol, byte[] frame) {
        ClientPacketDistributor.sendToServer(protocol.payload(frame));
    }

    @Override
    public boolean canReceive(ServerPlayer player) {
        return ((ICommonPacketListener) player.connection).hasChannel(protocol.type());
    }

    @Override
    public void send(ServerPlayer player, byte[] frame) {
        PacketDistributor.sendToPlayer(player, protocol.payload(frame));
    }

    @Override
    public void sendToServer(byte[] frame) {
        sendClient(protocol, frame);
    }
}

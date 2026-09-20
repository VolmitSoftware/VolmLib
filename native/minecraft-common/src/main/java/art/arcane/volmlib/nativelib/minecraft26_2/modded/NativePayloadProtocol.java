package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class NativePayloadProtocol {
    private final CustomPacketPayload.Type<Payload> type;
    private final StreamCodec<RegistryFriendlyByteBuf, Payload> codec;

    public NativePayloadProtocol(String channel, int maximumFrameBytes) {
        type = new CustomPacketPayload.Type<>(Identifier.parse(channel));
        codec = CustomPacketPayload.codec((payload, buffer) -> buffer.writeByteArray(payload.data()),
                buffer -> new Payload(this, buffer.readByteArray(maximumFrameBytes)));
    }

    public CustomPacketPayload.Type<Payload> type() {
        return type;
    }

    public StreamCodec<RegistryFriendlyByteBuf, Payload> codec() {
        return codec;
    }

    public Payload payload(byte[] frame) {
        return new Payload(this, frame);
    }

    public record Payload(NativePayloadProtocol protocol, byte[] data) implements CustomPacketPayload {
        @Override
        public CustomPacketPayload.Type<Payload> type() {
            return protocol.type;
        }
    }
}

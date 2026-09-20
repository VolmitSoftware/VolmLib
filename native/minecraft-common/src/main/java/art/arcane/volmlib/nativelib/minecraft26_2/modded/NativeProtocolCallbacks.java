package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public record NativeProtocolCallbacks(BiConsumer<NativeProtocolPlayer, byte[]> inbound,
                                      Consumer<NativeProtocolPlayer> joined,
                                      Consumer<NativeProtocolPlayer> disconnected) {
}

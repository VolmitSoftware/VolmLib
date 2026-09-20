package art.arcane.volmlib.nativelib.client;

import java.util.List;
import java.util.function.Consumer;

public record ClientHudBinding(String id, List<ClientKeyBinding> keys, Consumer<ClientGraphics> render,
                               Runnable tick, Runnable pollKeys) {
    public ClientHudBinding {
        keys = List.copyOf(keys);
    }
}

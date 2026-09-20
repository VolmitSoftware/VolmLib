package art.arcane.volmlib.nativelib.minecraft26_2.fabric;

import art.arcane.volmlib.nativelib.client.ClientHudBinding;
import art.arcane.volmlib.nativelib.client.ClientKeyBinding;
import art.arcane.volmlib.nativelib.minecraft26_2.client.NativeClientAccess;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

public final class NativeFabricClientHooks {
    private NativeFabricClientHooks() {
    }

    public static void install(ClientHudBinding binding, Runnable onJoin, Runnable onDisconnect) {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onJoin.run());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onDisconnect.run());
        for (ClientKeyBinding key : binding.keys()) {
            KeyMappingHelper.registerKeyMapping(NativeClientAccess.keyHandle(key));
        }
        HudElementRegistry.addLast(Identifier.parse(binding.id()), (graphics, delta) -> NativeClientAccess.render(graphics, binding.render()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            binding.tick().run();
            binding.pollKeys().run();
        });
    }
}

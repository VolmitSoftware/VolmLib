package art.arcane.volmlib.nativelib.minecraft26_2.neoforge;

import art.arcane.volmlib.nativelib.client.ClientHudBinding;
import art.arcane.volmlib.nativelib.client.ClientKeyBinding;
import art.arcane.volmlib.nativelib.minecraft26_2.client.NativeClientAccess;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class NativeNeoForgeClientHooks {
    private NativeNeoForgeClientHooks() {
    }

    public static void install(IEventBus modBus, ClientHudBinding binding, Runnable onJoin, Runnable onDisconnect) {
        modBus.addListener((RegisterGuiLayersEvent event) ->
                event.registerAboveAll(Identifier.parse(binding.id()), (graphics, delta) -> NativeClientAccess.render(graphics, binding.render())));
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            for (ClientKeyBinding key : binding.keys()) {
                event.register(NativeClientAccess.keyHandle(key));
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> onJoin.run());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> onDisconnect.run());
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            binding.tick().run();
            binding.pollKeys().run();
        });
    }
}

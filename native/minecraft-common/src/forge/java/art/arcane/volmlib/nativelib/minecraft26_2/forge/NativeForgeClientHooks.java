package art.arcane.volmlib.nativelib.minecraft26_2.forge;

import art.arcane.volmlib.nativelib.client.ClientHudBinding;
import art.arcane.volmlib.nativelib.client.ClientKeyBinding;
import art.arcane.volmlib.nativelib.minecraft26_2.client.NativeClientAccess;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;

public final class NativeForgeClientHooks {
    private NativeForgeClientHooks() {
    }

    public static void install(ClientHudBinding binding, Runnable onJoin, Runnable onDisconnect) {
        AddGuiOverlayLayersEvent.BUS.addListener((AddGuiOverlayLayersEvent event) ->
                event.getLayeredDraw().add(Identifier.parse(binding.id()), (graphics, delta) -> NativeClientAccess.render(graphics, binding.render())));
        RegisterKeyMappingsEvent.BUS.addListener((RegisterKeyMappingsEvent event) -> {
            for (ClientKeyBinding key : binding.keys()) {
                event.register(NativeClientAccess.keyHandle(key));
            }
        });
        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> onJoin.run());
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> onDisconnect.run());
        InputEvent.Key.BUS.addListener((InputEvent.Key event) -> binding.pollKeys().run());
        TickEvent.ClientTickEvent.Post.BUS.addListener((TickEvent.ClientTickEvent.Post event) -> {
            binding.pollKeys().run();
            binding.tick().run();
        });
    }
}

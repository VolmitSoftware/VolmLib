package art.arcane.volmlib.nativelib.minecraft26_2.client.mixin;

import art.arcane.volmlib.nativelib.client.ClientWorldPolicy;
import art.arcane.volmlib.nativelib.client.ClientWorldPolicies;

import art.arcane.volmlib.nativelib.modded.NativeMixinFlags;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * CLIENT DIST ONLY. Registered from volmlib.client.mixins.json, whose "client" block already restricts
 * application to the client dist.
 */
@Mixin(WorldCreationUiState.WorldTypeEntry.class)
public class NativeWorldTypeEntryMixin {
    @Shadow
    @Final
    private Holder<WorldPreset> preset;

    @Inject(method = "describePreset", at = @At("HEAD"), cancellable = true)
    private void volmlib$describePreset(CallbackInfoReturnable<Component> info) {
        NativeMixinFlags.markWorldTypeEntry();
        Optional<ResourceKey<WorldPreset>> key = preset == null
                ? Optional.empty()
                : preset.unwrapKey();
        ClientWorldPolicy policy = key.isEmpty() ? null
                : ClientWorldPolicies.forNamespace(key.get().identifier().getNamespace());
        if (policy == null) {
            return;
        }
        String label = policy.presetLabel().apply(key.get().identifier().getPath());
        if (label != null) {
            info.setReturnValue(Component.literal(label));
        }
    }
}

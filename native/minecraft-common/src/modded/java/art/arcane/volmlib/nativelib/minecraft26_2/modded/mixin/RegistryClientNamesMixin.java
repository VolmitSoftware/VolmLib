package art.arcane.volmlib.nativelib.minecraft26_2.modded.mixin;

import art.arcane.volmlib.nativelib.terrain.RegistryClientNames;
import art.arcane.volmlib.nativelib.modded.NativeMixinFlags;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.configuration.ClientboundRegistryDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(ClientboundRegistryDataPacket.class)
public abstract class RegistryClientNamesMixin {
    @Shadow
    @Final
    @Mutable
    private List<RegistrySynchronization.PackedRegistryEntry> entries;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void volmlib$clientRegistryNames(ResourceKey<? extends Registry<?>> registry,
                                           List<RegistrySynchronization.PackedRegistryEntry> original,
                                           CallbackInfo callback) {
        if (!registry.equals(Registries.BIOME)) {
            return;
        }
        NativeMixinFlags.markRegistryClientNames();
        List<String> physicalKeys = new ArrayList<>(original.size());
        for (RegistrySynchronization.PackedRegistryEntry entry : original) {
            physicalKeys.add(entry.id().toString());
        }
        List<String> clientKeys = RegistryClientNames.resolve(registry.identifier().toString(), physicalKeys);
        if (clientKeys.equals(physicalKeys)) {
            return;
        }
        List<RegistrySynchronization.PackedRegistryEntry> renamed = new ArrayList<>(original.size());
        for (int index = 0; index < original.size(); index++) {
            RegistrySynchronization.PackedRegistryEntry entry = original.get(index);
            String clientKey = clientKeys.get(index);
            renamed.add(clientKey.equals(physicalKeys.get(index)) ? entry
                    : new RegistrySynchronization.PackedRegistryEntry(Identifier.parse(clientKey), entry.data()));
        }
        entries = List.copyOf(renamed);
    }
}

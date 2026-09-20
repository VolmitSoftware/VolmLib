package art.arcane.volmlib.nativelib.minecraft26_2.modded.mixin;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeEntityBehavior;
import art.arcane.volmlib.nativelib.modded.NativeMixinFlags;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityPersistenceMixin {
    @Inject(method = "shouldBeSaved", at = @At("RETURN"), cancellable = true)
    private void iris$applyGeneratedPersistence(CallbackInfoReturnable<Boolean> info) {
        NativeMixinFlags.markEntityPersistence();
        Entity entity = (Entity) (Object) this;
        info.setReturnValue(NativeEntityBehavior.shouldSave(entity, info.getReturnValue()));
    }
}

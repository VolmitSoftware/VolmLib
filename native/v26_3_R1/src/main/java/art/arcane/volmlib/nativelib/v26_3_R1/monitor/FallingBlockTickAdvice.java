package art.arcane.volmlib.nativelib.v26_3_R1.monitor;

import net.bytebuddy.asm.Advice;
import net.minecraft.world.entity.item.FallingBlockEntity;

public final class FallingBlockTickAdvice {
    private FallingBlockTickAdvice() {}

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static boolean enter(@Advice.This FallingBlockEntity self) {
        return FallingBlockTickRuntime.enter(self);
    }
}

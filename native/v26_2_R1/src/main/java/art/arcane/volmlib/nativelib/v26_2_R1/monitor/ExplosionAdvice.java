package art.arcane.volmlib.nativelib.v26_2_R1.monitor;

import net.bytebuddy.asm.Advice;
import net.minecraft.world.level.ServerExplosion;

public final class ExplosionAdvice {
    private ExplosionAdvice() {}

    @Advice.OnMethodEnter
    public static void enter(@Advice.This ServerExplosion self) {
        ExplosionRuntime.enter(self);
    }
}

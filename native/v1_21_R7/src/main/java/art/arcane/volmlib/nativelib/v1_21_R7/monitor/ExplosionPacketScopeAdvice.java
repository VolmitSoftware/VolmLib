package art.arcane.volmlib.nativelib.v1_21_R7.monitor;

import net.bytebuddy.asm.Advice;

public final class ExplosionPacketScopeAdvice {
    private ExplosionPacketScopeAdvice() {}

    @Advice.OnMethodEnter
    public static void enter() {
        ExplosionPacketSubstitution.enterExplosion();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit() {
        ExplosionPacketSubstitution.exitExplosion();
    }
}

package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.border.WorldBorder;

public final class NativeWorldBoundary {
    private NativeWorldBoundary() {
    }

    public static void apply(NativeWorld world, Settings settings) {
        apply(((ServerLevel) world.nativeHandle()).getWorldBorder(), settings);
    }

    static void apply(WorldBorder border, Settings settings) {
        border.setCenter(settings.x(), settings.z());
        border.setSize(settings.size());
        border.setWarningBlocks(settings.warningDistance());
        border.setSafeZone(settings.damageBuffer());
        border.setDamagePerBlock(settings.damageAmount());
    }

    public record Settings(double x, double z, double size, int warningDistance,
                           double damageBuffer, double damageAmount) {
    }
}

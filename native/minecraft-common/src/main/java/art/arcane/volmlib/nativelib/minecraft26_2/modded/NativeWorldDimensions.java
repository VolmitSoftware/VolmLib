package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;

public final class NativeWorldDimensions {
    private NativeWorldDimensions() {
    }

    public static Settings settings(NativeWorld world) {
        ServerLevel level = (ServerLevel) world.nativeHandle();
        DimensionType type = level.dimensionType();
        String key = level.dimensionTypeRegistration().unwrapKey()
                .map(value -> value.identifier().toString()).orElse("inline");
        return new Settings(key, type.minY(), type.minY() + type.height(), type.logicalHeight());
    }

    public record Settings(String key, int minimumHeight, int maximumHeight, int logicalHeight) {
    }
}

package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import net.minecraft.server.level.ServerLevel;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;

import java.util.function.BiConsumer;

public final class NativeItemContext {
    private final ServerLevel level;
    private final Options options;

    public NativeItemContext(NativeWorld world, Options options) {
        this.level = world == null ? null : (ServerLevel) world.nativeHandle();
        this.options = options;
    }

    ServerLevel level() {
        return level;
    }

    String modifierNamespace() {
        return options.modifierNamespace();
    }

    void warn(String key, String message) {
        options.warnings().accept(key, message);
    }

    public record Options(String modifierNamespace, BiConsumer<String, String> warnings) {
    }
}

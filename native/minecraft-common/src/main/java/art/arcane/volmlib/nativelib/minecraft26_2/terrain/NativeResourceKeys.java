package art.arcane.volmlib.nativelib.minecraft26_2.terrain;

import net.minecraft.resources.Identifier;

import java.util.Locale;

public final class NativeResourceKeys {
    private NativeResourceKeys() {
    }

    public static String normalize(String raw) {
        Identifier identifier = raw == null ? null : Identifier.tryParse(raw);
        return identifier == null ? null : identifier.toString().toLowerCase(Locale.ROOT);
    }
}

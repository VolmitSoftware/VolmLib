package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;

import java.util.function.IntBinaryOperator;

public final class ModdedHeightmaps {
    private ModdedHeightmaps() {
    }

    public static long[] terrainRawData(int height, IntBinaryOperator heightResolver) {
        SimpleBitStorage storage = new SimpleBitStorage(Mth.ceillog2(height + 1), 256);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int value = Mth.clamp(heightResolver.applyAsInt(x, z), 0, height);
                storage.set(x + z * 16, value);
            }
        }
        return storage.getRaw();
    }
}

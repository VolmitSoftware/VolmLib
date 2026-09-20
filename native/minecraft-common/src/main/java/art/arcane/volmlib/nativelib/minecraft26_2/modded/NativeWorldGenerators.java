package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;

public final class NativeWorldGenerators {
    private NativeWorldGenerators() {
    }

    public static <T> T find(NativeWorld world, Class<T> type) {
        if (world == null) {
            return null;
        }
        ChunkGenerator generator = ((ServerLevel) world.nativeHandle()).getChunkSource().getGenerator();
        if (type.isInstance(generator)) {
            return type.cast(generator);
        }
        if (generator instanceof NativeGeneratorHandle nativeGenerator && type.isInstance(nativeGenerator.owner())) {
            return type.cast(nativeGenerator.owner());
        }
        return null;
    }
}

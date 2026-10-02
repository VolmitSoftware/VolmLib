package art.arcane.volmlib.nativelib.minecraft26_2.modded.mixin;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedChunkGenerator;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerLevel.class)
public abstract class ServerLevelStructureBootstrapMixin {
    @Shadow
    public abstract ServerChunkCache getChunkSource();

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;ensureStructuresGenerated()V"))
    void iris$initializeStructureState(ChunkGeneratorStructureState state) {
        if (!(getChunkSource().getGenerator() instanceof NativeModdedChunkGenerator<?, ?, ?>)) {
            state.ensureStructuresGenerated();
        }
    }
}

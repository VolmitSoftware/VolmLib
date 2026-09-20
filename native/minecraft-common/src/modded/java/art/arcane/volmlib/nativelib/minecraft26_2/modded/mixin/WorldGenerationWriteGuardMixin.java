package art.arcane.volmlib.nativelib.minecraft26_2.modded.mixin;

import art.arcane.volmlib.nativelib.terrain.NativeChunkWritePolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldGenRegion.class)
public abstract class WorldGenerationWriteGuardMixin {
    @Inject(method = "ensureCanWrite", at = @At("RETURN"), cancellable = true)
    private void iris$guardCompletedTerrain(BlockPos position, CallbackInfoReturnable<Boolean> result) {
        WorldGenRegion region = (WorldGenRegion) (Object) this;
        if (result.getReturnValue() && region.getLevel().getChunkSource().getGenerator()
                instanceof NativeChunkWritePolicy generator
                && region.getChunk(position.getX() >> 4, position.getZ() >> 4)
                .getPersistedStatus().isOrAfter(ChunkStatus.FULL)
                && !generator.allowsNativeChunkWrite(position.getX() >> 4, position.getZ() >> 4)) {
            result.setReturnValue(false);
        }
    }
}

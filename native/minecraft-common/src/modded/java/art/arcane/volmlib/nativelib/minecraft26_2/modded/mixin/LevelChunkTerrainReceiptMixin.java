package art.arcane.volmlib.nativelib.minecraft26_2.modded.mixin;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeTerrainReceipts;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public abstract class LevelChunkTerrainReceiptMixin {
    @Inject(method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ProtoChunk;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;)V", at = @At("RETURN"))
    private void volmlib$transferTerrain(ServerLevel level, ProtoChunk source, LevelChunk.PostLoadProcessor processor,
                                      CallbackInfo callback) {
        NativeTerrainReceipts.setStructureActivation((LevelChunk) (Object) this,
                NativeTerrainReceipts.structureActivation(source));
        NativeTerrainReceipts.set((LevelChunk) (Object) this, NativeTerrainReceipts.get(source));
    }
}

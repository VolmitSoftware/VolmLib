package art.arcane.volmlib.nativelib.minecraft26_2.modded.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface ChunkMapTerrainReceiptAccess {
    @Invoker("save")
    boolean volmlib$saveTerrainCheckpoint(ChunkAccess chunk);

    @Accessor("updatingChunkMap")
    Long2ObjectLinkedOpenHashMap<ChunkHolder> volmlib$updatingChunks();
}

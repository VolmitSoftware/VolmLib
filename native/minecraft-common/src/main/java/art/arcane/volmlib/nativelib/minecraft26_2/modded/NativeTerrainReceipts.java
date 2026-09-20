package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeTerrainReceiptHolder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;

public final class NativeTerrainReceipts {
    private NativeTerrainReceipts() {
    }

    public static void persist(ChunkAccess chunk, byte[] receipt) {
        if (receipt == null) {
            return;
        }
        set(chunk, receipt);
        chunk.markUnsaved();
    }

    public static void persistStructureActivation(ChunkAccess chunk, long activation) {
        setStructureActivation(chunk, activation);
        chunk.markUnsaved();
    }

    public static long structureActivation(ChunkAccess chunk) {
        return holder(chunk).volmlib$getStructureActivation();
    }

    public static void setStructureActivation(ChunkAccess chunk, long activation) {
        holder(chunk).volmlib$setStructureActivation(activation);
    }

    public static byte[] get(ChunkAccess chunk) {
        return holder(chunk).volmlib$getNaturalTerrain();
    }

    public static void set(ChunkAccess chunk, byte[] receipt) {
        holder(chunk).volmlib$setNaturalTerrain(receipt);
    }

    private static NativeTerrainReceiptHolder holder(ChunkAccess chunk) {
        ChunkAccess target = chunk instanceof ImposterProtoChunk imposter ? imposter.getWrapped() : chunk;
        if (!(target instanceof NativeTerrainReceiptHolder holder)) {
            throw new IllegalStateException("Native terrain receipt storage is unavailable for " + chunk.getPos());
        }
        return holder;
    }
}

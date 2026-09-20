package art.arcane.volmlib.nativelib.minecraft26_2.modded.mixin;

import java.util.Arrays;

import art.arcane.volmlib.nativelib.terrain.NativeTerrainReceiptHolder;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ChunkAccess.class)
public abstract class ChunkTerrainReceiptMixin implements NativeTerrainReceiptHolder {
    @Unique
    private byte[] volmlib$naturalTerrain;
    @Unique
    private long volmlib$structureActivation;

    @Override
    public long volmlib$getStructureActivation() {
        return volmlib$structureActivation;
    }

    @Override
    public void volmlib$setStructureActivation(long activation) {
        if (activation < 0) {
            throw new IllegalArgumentException("Native structure activation cannot be negative");
        }
        volmlib$structureActivation = activation;
    }

    @Override
    public byte[] volmlib$getNaturalTerrain() {
        return volmlib$naturalTerrain == null ? null : Arrays.copyOf(volmlib$naturalTerrain, volmlib$naturalTerrain.length);
    }

    @Override
    public void volmlib$setNaturalTerrain(byte[] receipt) {
        volmlib$naturalTerrain = receipt == null ? null : Arrays.copyOf(receipt, receipt.length);
    }
}

package art.arcane.volmlib.nativelib.minecraft26_2.modded.mixin;

import java.util.Arrays;

import art.arcane.volmlib.nativelib.terrain.NativeTerrainReceiptStorage;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeTerrainReceipts;
import art.arcane.volmlib.nativelib.terrain.NativeTerrainReceiptHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SerializableChunkData.class)
public abstract class SerializableTerrainReceiptMixin implements NativeTerrainReceiptHolder {
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

    @Inject(method = "parse", at = @At("RETURN"))
    private static void volmlib$parseTerrain(LevelHeightAccessor height, PalettedContainerFactory containers,
                                          CompoundTag tag, CallbackInfoReturnable<SerializableChunkData> callback) {
        SerializableChunkData parsed = callback.getReturnValue();
        if (parsed != null) {
            ((NativeTerrainReceiptHolder) (Object) parsed).volmlib$setStructureActivation(
                    tag.getLongOr(NativeTerrainReceiptStorage.STRUCTURE_ACTIVATION_KEY, 0));
            ((NativeTerrainReceiptHolder) (Object) parsed).volmlib$setNaturalTerrain(
                    tag.getByteArray(NativeTerrainReceiptStorage.NBT_KEY).orElse(null));
        }
    }

    @Inject(method = "copyOf", at = @At("RETURN"))
    private static void volmlib$copyTerrain(ServerLevel level, ChunkAccess chunk,
                                         CallbackInfoReturnable<SerializableChunkData> callback) {
        ((NativeTerrainReceiptHolder) (Object) callback.getReturnValue()).volmlib$setStructureActivation(
                NativeTerrainReceipts.structureActivation(chunk));
        ((NativeTerrainReceiptHolder) (Object) callback.getReturnValue()).volmlib$setNaturalTerrain(
                NativeTerrainReceipts.get(chunk));
    }

    @Inject(method = "read", at = @At("RETURN"))
    private void volmlib$restoreTerrain(ServerLevel level, PoiManager points, RegionStorageInfo storage, ChunkPos position,
                                     CallbackInfoReturnable<ProtoChunk> callback) {
        NativeTerrainReceipts.set(callback.getReturnValue(), volmlib$naturalTerrain);
        NativeTerrainReceipts.setStructureActivation(callback.getReturnValue(), volmlib$structureActivation);
    }

    @Inject(method = "write", at = @At("RETURN"))
    private void volmlib$writeTerrain(CallbackInfoReturnable<CompoundTag> callback) {
        if (volmlib$structureActivation > 0) {
            callback.getReturnValue().putLong(NativeTerrainReceiptStorage.STRUCTURE_ACTIVATION_KEY, volmlib$structureActivation);
        }
        if (volmlib$naturalTerrain != null) {
            callback.getReturnValue().putByteArray(NativeTerrainReceiptStorage.NBT_KEY, Arrays.copyOf(volmlib$naturalTerrain, volmlib$naturalTerrain.length));
        }
    }
}

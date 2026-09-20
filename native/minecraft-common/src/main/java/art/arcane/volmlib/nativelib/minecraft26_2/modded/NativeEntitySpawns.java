package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.entity.NativeEntityType;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;

public final class NativeEntitySpawns {
    private NativeEntitySpawns() {
    }

    public static boolean command(NativeWorld world, NativeEntityType type, double x, double y, double z) {
        if (!(world.nativeHandle() instanceof ServerLevel level)) {
            return false;
        }
        return spawn((EntityType<?>) type.nativeHandle(), level, BlockPos.containing(x, y, z), EntitySpawnReason.COMMAND) != null;
    }

    public static Entity spawn(EntityType<?> type, ServerLevel level, BlockPos position, EntitySpawnReason reason) {
        if (level.getDifficulty() == Difficulty.PEACEFUL && !type.isAllowedInPeaceful()) {
            return null;
        }
        return type.spawn(level, position, reason);
    }
}

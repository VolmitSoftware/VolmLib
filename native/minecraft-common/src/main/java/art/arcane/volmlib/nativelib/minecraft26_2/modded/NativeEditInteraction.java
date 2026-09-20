package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public record NativeEditInteraction(NativeEditPlayer player, NativeEditWorld world, NativeBlockPoint position) {
    public static NativeEditInteraction create(Player player, Level level, InteractionHand hand, BlockPos position) {
        if (hand != InteractionHand.MAIN_HAND || level.isClientSide()
                || !(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return new NativeEditInteraction(new NativeEditPlayer(serverPlayer),
                new NativeEditWorld(new ModdedPlatformWorld(serverLevel)),
                new NativeBlockPoint(position.getX(), position.getY(), position.getZ()));
    }
}

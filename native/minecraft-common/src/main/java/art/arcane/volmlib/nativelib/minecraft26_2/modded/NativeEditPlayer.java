package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.UUID;

public final class NativeEditPlayer {
    private final ServerPlayer player;
    private ServerLevel currentLevel;
    private NativeEditWorld world;

    public NativeEditPlayer(ServerPlayer player) {
        this.player = player;
    }

    public UUID id() {
        return player.getUUID();
    }

    public NativeBlockPoint blockPosition() {
        BlockPos pos = player.blockPosition();
        return new NativeBlockPoint(pos.getX(), pos.getY(), pos.getZ());
    }

    public NativeEditWorld world() {
        ServerLevel level = player.level();
        if (currentLevel != level) {
            currentLevel = level;
            world = new NativeEditWorld(new ModdedPlatformWorld(level));
        }
        return world;
    }

    public NativeBlockPoint pickBlock(double range) {
        HitResult hit = player.pick(range, 1.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
            return null;
        }
        BlockPos pos = blockHit.getBlockPos();
        return new NativeBlockPoint(pos.getX(), pos.getY(), pos.getZ());
    }

    public LookDirection lookDirection() {
        Direction direction = Direction.getApproximateNearest(player.getLookAngle());
        int axis = switch (direction.getAxis()) {
            case X -> 0;
            case Y -> 1;
            case Z -> 2;
        };
        return new LookDirection(axis, direction.getAxisDirection().getStep(), direction.getName());
    }

    public boolean give(NativeItemStack stack) {
        return player.getInventory().add(stack.stack());
    }

    public boolean holdingFlaggedItem(String flag) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr(flag, false);
    }

    public void sendSystemMessage(NativeCommandText text) {
        player.sendSystemMessage(text.component());
    }

    public void sendOverlayMessage(NativeCommandText text) {
        player.sendOverlayMessage(text.component());
    }

    public boolean activeIn(NativeEditWorld world) {
        return !player.hasDisconnected() && !player.isRemoved() && world.sameWorld(world());
    }

    public void dust(Dust dust, double x, double y, double z, ParticleSpread spread) {
        player.level().sendParticles(player, dust.options, true, true, x, y, z, spread.count(),
                spread.x(), spread.y(), spread.z(), spread.speed());
    }

    public void coloredDust(int color, float size, double x, double y, double z) {
        player.level().sendParticles(player, new DustParticleOptions(color, size), true, true,
                x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    public double x() {
        return player.getX();
    }

    public double y() {
        return player.getY();
    }

    public double z() {
        return player.getZ();
    }

    public int ticks() {
        return player.tickCount;
    }

    public record LookDirection(int axis, int step, String name) {
    }

    public record ParticleSpread(int count, double x, double y, double z, double speed) {
    }

    public static final class Dust {
        private final DustParticleOptions options;

        public Dust(int color, float size) {
            options = new DustParticleOptions(color, size);
        }
    }
}

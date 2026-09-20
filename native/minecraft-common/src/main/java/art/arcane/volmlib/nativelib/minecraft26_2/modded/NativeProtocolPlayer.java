package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.world.entity.Relative;
import java.util.Set;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class NativeProtocolPlayer {
    private final ServerPlayer player;
    private ServerLevel currentLevel;
    private NativeProtocolWorld world;

    private NativeProtocolPlayer(ServerPlayer player) {
        this.player = player;
    }

    public static NativeProtocolPlayer fromHandle(Object player) {
        return new NativeProtocolPlayer((ServerPlayer) player);
    }

    public NativeEditPlayer editing() {
        return new NativeEditPlayer(player);
    }

    ServerPlayer player() {
        return player;
    }

    public boolean removed() {
        return player.isRemoved();
    }

    public double y() {
        return player.getY();
    }

    public double x() {
        return player.getX();
    }

    public double z() {
        return player.getZ();
    }

    public boolean isInWorld(NativeWorld world) {
        return world != null && player.level() == world.nativeHandle();
    }

    public boolean connected() {
        return !player.hasDisconnected() && !player.isRemoved();
    }

    public boolean inWorld(NativeWorld world) {
        return player.level() == world.nativeHandle();
    }

    public boolean teleport(NativeWorld world, double x, double y, double z) {
        return player.teleportTo((ServerLevel) world.nativeHandle(), x, y, z,
                Set.<Relative>of(), player.getYRot(), player.getXRot(), false);
    }

    public int blockY() {
        return player.blockPosition().getY();
    }

    public int blockX() {
        return player.blockPosition().getX();
    }

    public int blockZ() {
        return player.blockPosition().getZ();
    }

    public String name() {
        return player.getScoreboardName();
    }

    public UUID id() {
        return player.getUUID();
    }

    public boolean isGameMaster() {
        return Commands.LEVEL_GAMEMASTERS.check(player.permissions());
    }

    public void sendMessage(String message) {
        player.sendSystemMessage(Component.literal(message));
    }

    public NativeProtocolWorld world() {
        ServerLevel level = player.level();
        if (currentLevel != level) {
            currentLevel = level;
            world = new NativeProtocolWorld(level);
        }
        return world;
    }

    boolean represents(ServerPlayer player) {
        return this.player == player;
    }
}

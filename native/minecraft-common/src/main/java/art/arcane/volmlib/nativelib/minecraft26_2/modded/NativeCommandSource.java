package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import java.util.function.Supplier;

public final class NativeCommandSource {
    private final CommandSourceStack source;

    NativeCommandSource(CommandSourceStack source) {
        this.source = source;
    }

    public NativeModdedServer server() {
        return NativeModdedServer.fromHandle(source.getServer());
    }

    public NativeWorld world() {
        return new ModdedPlatformWorld(source.getLevel());
    }

    public UUID playerId() {
        ServerPlayer player = source.getPlayer();
        return player == null ? null : player.getUUID();
    }

    public NativeEditPlayer editingPlayer() {
        ServerPlayer player = source.getPlayer();
        return player == null ? null : new NativeEditPlayer(player);
    }

    public NativeProtocolPlayer player() {
        ServerPlayer player = source.getPlayer();
        return player == null ? null : NativeProtocolPlayer.fromHandle(player);
    }

    public Position position() {
        Vec3 position = source.getPosition();
        return new Position(position.x, position.y, position.z);
    }

    public boolean isGameMaster() {
        return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source);
    }

    public void execute(Runnable action) {
        source.getServer().execute(action);
    }

    public void sendSuccess(Supplier<NativeCommandText> text, boolean broadcast) {
        source.sendSuccess(() -> text.get().component(), broadcast);
    }

    public void sendFailure(NativeCommandText text) {
        source.sendFailure(text.component());
    }

    public void playSound(String key, float volume, float pitch) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return;
        }
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse(key));
        if (sound != null) {
            player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, volume, pitch);
        }
    }

    CommandSourceStack source() {
        return source;
    }

    public record Position(double x, double y, double z) {
    }
}

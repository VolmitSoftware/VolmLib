package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class NativeDropEffects {
    private final ServerLevel level;

    public NativeDropEffects(NativeWorld world) {
        level = (ServerLevel) world.nativeHandle();
    }

    public void particle(Particle particle, Emission emission, NativeBlockState state) {
        ParticleOptions options = switch (particle) {
            case ENCHANT -> ParticleTypes.ENCHANT;
            case END_ROD -> ParticleTypes.END_ROD;
            case BLOCK -> new BlockParticleOption(ParticleTypes.BLOCK, (BlockState) state.nativeHandle());
        };
        level.sendParticles(options, emission.x(), emission.y(), emission.z(), emission.count(),
                emission.spreadX(), emission.spreadY(), emission.spreadZ(), emission.speed());
    }

    public void sound(String key, SoundEmission emission) {
        level.playSound(null, emission.x(), emission.y(), emission.z(),
                SoundEvent.createVariableRangeEvent(Identifier.parse(key)), SoundSource.PLAYERS,
                emission.volume(), emission.pitch());
    }

    public boolean drop(NativeItemStack stack, DropPosition position) {
        ItemEntity entity = new ItemEntity(level, position.x(), position.y(), position.z(), stack.stack(),
                position.velocityX(), position.velocityY(), position.velocityZ());
        entity.setDefaultPickUpDelay();
        return level.addFreshEntity(entity);
    }

    public enum Particle {
        ENCHANT, END_ROD, BLOCK
    }

    public record Emission(double x, double y, double z, int count,
                           double spreadX, double spreadY, double spreadZ, double speed) {
    }

    public record SoundEmission(double x, double y, double z, float volume, float pitch) {
    }

    public record DropPosition(double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
    }
}

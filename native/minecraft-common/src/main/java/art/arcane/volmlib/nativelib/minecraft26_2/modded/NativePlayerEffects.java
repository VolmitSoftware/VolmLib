package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class NativePlayerEffects {
    private NativePlayerEffects() {
    }

    public static List<NativeProtocolPlayer> players(NativeWorld world) {
        List<ServerPlayer> players = ((ServerLevel) world.nativeHandle()).players();
        List<NativeProtocolPlayer> result = new ArrayList<>(players.size());
        for (ServerPlayer player : players) {
            result.add(NativeProtocolPlayer.fromHandle(player));
        }
        return result;
    }

    public static boolean alive(NativeProtocolPlayer player) {
        return player.player().isAlive();
    }

    public static Direction look(NativeProtocolPlayer player) {
        Vec3 direction = player.player().getLookAngle();
        return new Direction(direction.x, direction.z);
    }

    public static Integer loadedSurface(NativeWorld world, int x, int z) {
        ServerLevel level = (ServerLevel) world.nativeHandle();
        return level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null ? null
                : level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
    }

    public static Sound sound(String key) {
        Identifier identifier = Identifier.tryParse(key);
        Optional<Holder.Reference<SoundEvent>> sound = identifier == null ? Optional.empty()
                : BuiltInRegistries.SOUND_EVENT.get(identifier);
        return sound.map(Sound::new).orElse(null);
    }

    public static Particle particle(String key) {
        Identifier identifier = Identifier.tryParse(key);
        ParticleType<?> type = identifier == null ? null : BuiltInRegistries.PARTICLE_TYPE.getValue(identifier);
        return type == null ? null : new Particle(type);
    }

    public static Potion potion(String key) {
        Identifier identifier = Identifier.tryParse(key);
        return identifier == null ? null : BuiltInRegistries.MOB_EFFECT.get(identifier).map(Potion::new).orElse(null);
    }

    public static Integer amplifier(NativeProtocolPlayer player, Potion potion) {
        MobEffectInstance current = player.player().getEffect(potion.type);
        return current == null ? null : current.getAmplifier();
    }

    public static void removePotion(NativeProtocolPlayer player, Potion potion) {
        player.player().removeEffect(potion.type);
    }

    public static void potion(NativeProtocolPlayer player, Potion potion, PotionEmission emission) {
        player.player().addEffect(new MobEffectInstance(potion.type, emission.duration(), emission.strength(),
                emission.ambient(), emission.particles(), emission.icon()));
    }

    public static void sound(NativeProtocolPlayer player, Sound sound, SoundEmission emission) {
        player.player().connection.send(new ClientboundSoundPacket(sound.type, SoundSource.MASTER,
                emission.x(), emission.y(), emission.z(), emission.volume(), emission.pitch(), emission.seed()));
    }

    public static void particles(NativeProtocolPlayer player, Particle particle, ParticleEmission emission) {
        player.player().level().sendParticles(player.player(), (SimpleParticleType) particle.type, false, false,
                emission.x(), emission.y(), emission.z(), emission.count(), emission.altX(), emission.altY(),
                emission.altZ(), emission.extra());
    }

    public record Direction(double x, double z) {
    }

    public record SoundEmission(double x, double y, double z, float volume, float pitch, long seed) {
    }

    public record ParticleEmission(double x, double y, double z, int count,
                                   double altX, double altY, double altZ, double extra) {
    }

    public record PotionEmission(int duration, int strength, boolean ambient, boolean particles, boolean icon) {
    }

    public static final class Sound {
        private final Holder<SoundEvent> type;
        private Sound(Holder<SoundEvent> type) { this.type = type; }
    }

    public static final class Particle {
        private final ParticleType<?> type;
        private Particle(ParticleType<?> type) { this.type = type; }
        public boolean simple() { return type instanceof SimpleParticleType; }
    }

    public static final class Potion {
        private final Holder<MobEffect> type;
        private Potion(Holder<MobEffect> type) { this.type = type; }
    }
}

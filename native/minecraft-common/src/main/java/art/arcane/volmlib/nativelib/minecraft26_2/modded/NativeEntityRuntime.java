package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeBlockPositionPredicate;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

public final class NativeEntityRuntime {
    private final ServerLevel level;
    private volatile ModdedPlatformWorld world;

    public NativeEntityRuntime(ServerLevel level) {
        this.level = Objects.requireNonNull(level, "level");
    }

    public static NativeEntityRuntime forWorld(NativeWorld world) {
        return new NativeEntityRuntime((ServerLevel) world.nativeHandle());
    }

    ServerLevel level() {
        return level;
    }

    public NativeWorld world() {
        ModdedPlatformWorld current = world;
        if (current == null) {
            current = new ModdedPlatformWorld(level);
            world = current;
        }
        return current;
    }

    public NativeSpawnedEntity spawn(Type type, Position position, String reason) {
        Entity entity = NativeEntitySpawns.spawn(type.type, level,
                BlockPos.containing(position.x(), position.y(), position.z()), reasonFor(reason));
        if (entity == null) {
            return null;
        }
        entity.snapTo(position.x(), position.y(), position.z(), 0F, 0F);
        return new NativeSpawnedEntity(entity);
    }

    public NativeSpawnedEntity spawnCustom(String key, Position position, Function<CustomSpawn, NativeSpawnedEntity> factory) {
        return factory.apply(new CustomSpawn(this, position, key));
    }

    public boolean chunksSafe(int chunkX, int chunkZ) {
        return allNeighborChunksLoaded(chunkX, chunkZ, this::chunkLoaded);
    }

    public boolean chunkLoaded(int chunkX, int chunkZ) {
        return level.getChunkSource().getChunkNow(chunkX, chunkZ) != null;
    }

    public boolean air(int x, int y, int z) {
        return level.getBlockState(new BlockPos(x, y, z)).is(Blocks.AIR);
    }

    public boolean solid(int x, int y, int z) {
        return NativeBlockProperties.isSolid(level.getBlockState(new BlockPos(x, y, z)));
    }

    public boolean fluid(int x, int y, int z, boolean lava) {
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        if (NativeBlockProperties.isSolid(state)) {
            return false;
        }
        if (state.is(Blocks.LAVA)) {
            return lava;
        }
        return !lava && (NativeBlockProperties.isWater(state) || NativeBlockProperties.isWaterLogged(state)
                || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS)
                || state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT));
    }

    public boolean hasPlayersNearby(Position position, double radius) {
        AABB bounds = new AABB(position.x() - radius, position.y() - radius, position.z() - radius,
                position.x() + radius, position.y() + radius, position.z() + radius);
        for (ServerPlayer player : level.players()) {
            if (player.getBoundingBox().intersects(bounds)) {
                return true;
            }
        }
        return false;
    }

    public void sound(Sound sound, SoundEmission emission) {
        level.playSound(null, emission.x(), emission.y(), emission.z(), sound.sound, SoundSource.MASTER,
                emission.volume(), emission.pitch());
    }

    public void particle(Particle particle, ParticleEmission emission) {
        sendParticles((SimpleParticleType) particle.type, emission);
    }

    public void blockItemParticles(Position source, ParticleEmission emission) {
        BlockState state = level.getBlockState(BlockPos.containing(source.x(), source.y(), source.z()));
        sendParticles(new ItemParticleOption(ParticleTypes.ITEM, state.getBlock().asItem()), emission);
    }

    private void sendParticles(ParticleOptions particle, ParticleEmission emission) {
        level.sendParticles(particle, emission.x(), emission.y(), emission.z(), emission.count(),
                emission.offsetX(), emission.offsetY(), emission.offsetZ(), emission.extra());
    }

    public static Type resolveType(String key) {
        Identifier id = identifier(key);
        return id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id) ? null
                : new Type(BuiltInRegistries.ENTITY_TYPE.getValue(id));
    }

    public static Sound resolveSound(String key) {
        Identifier id = identifier(key);
        return id == null || !BuiltInRegistries.SOUND_EVENT.containsKey(id) ? null
                : new Sound(BuiltInRegistries.SOUND_EVENT.getValue(id));
    }

    public static Particle resolveParticle(String key) {
        Identifier id = identifier(key);
        return id == null || !BuiltInRegistries.PARTICLE_TYPE.containsKey(id) ? null
                : new Particle(BuiltInRegistries.PARTICLE_TYPE.getValue(id));
    }

    private static Identifier identifier(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return Identifier.tryParse(normalized.indexOf(':') >= 0 ? normalized : "minecraft:" + normalized);
    }

    public static boolean isFluidAreaClearForSpawn(int blockX, int blockY, int blockZ,
                                           float width, float height, NativeBlockPositionPredicate matchesFluid) {
        int startX = (int) Math.floor(blockX + 0.5 - width / 2D);
        int endX = (int) Math.floor(Math.nextDown(blockX + 0.5 + width / 2D));
        int endY = (int) Math.floor(Math.nextDown(blockY + 0.5 + height));
        int startZ = (int) Math.floor(blockZ + 0.5 - width / 2D);
        int endZ = (int) Math.floor(Math.nextDown(blockZ + 0.5 + width / 2D));
        for (int x = startX; x <= endX; x++) {
            for (int y = blockY; y <= endY; y++) {
                for (int z = startZ; z <= endZ; z++) {
                    if (!matchesFluid.test(x, y, z)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static boolean isAreaClearForSpawn(int blockX, int blockY, int blockZ, float width, float height, NativeBlockPositionPredicate isAir) {
        int radius = (int) (width / 2F);
        int endY = blockY + (int) height;
        for (int x = blockX - radius; x <= blockX + radius; x++) {
            for (int y = blockY; y <= endY; y++) {
                for (int z = blockZ - radius; z <= blockZ + radius; z++) {
                    if (!isAir.test(x, y, z)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static boolean allNeighborChunksLoaded(int chunkX, int chunkZ, ChunkLoadedLookup lookup) {
        for (int x = chunkX - 1; x <= chunkX + 1; x++) {
            for (int z = chunkZ - 1; z <= chunkZ + 1; z++) {
                if (!lookup.loaded(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean shouldDisableAi(boolean ai) {
        return !ai;
    }

    public static EntitySpawnReason reasonFor(String reason) {
        if (reason == null || reason.isBlank()) {
            return EntitySpawnReason.NATURAL;
        }
        String value = reason.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "NATURAL", "DEFAULT" -> EntitySpawnReason.NATURAL;
            case "JOCKEY", "MOUNT" -> EntitySpawnReason.JOCKEY;
            case "CHUNK_GEN" -> EntitySpawnReason.CHUNK_GENERATION;
            case "SPAWNER" -> EntitySpawnReason.SPAWNER;
            case "TRIAL_SPAWNER" -> EntitySpawnReason.TRIAL_SPAWNER;
            case "EGG", "BUILD_SNOWMAN", "BUILD_IRONGOLEM", "BUILD_COPPERGOLEM", "BUILD_WITHER",
                    "SLIME_SPLIT", "SILVERFISH_BLOCK", "TRAP", "ENDER_PEARL", "EXPLOSION",
                    "ENCHANTMENT", "OMINOUS_ITEM_SPAWNER", "POTION_EFFECT", "REANIMATE" -> EntitySpawnReason.TRIGGERED;
            case "SPAWNER_EGG" -> EntitySpawnReason.SPAWN_ITEM_USE;
            case "LIGHTNING", "VILLAGE_INVASION", "RAID", "CUSTOM" -> EntitySpawnReason.EVENT;
            case "VILLAGE_DEFENSE", "SPELL" -> EntitySpawnReason.MOB_SUMMONED;
            case "BREEDING", "OCELOT_BABY", "DUPLICATION", "REHYDRATION" -> EntitySpawnReason.BREEDING;
            case "REINFORCEMENTS" -> EntitySpawnReason.REINFORCEMENT;
            case "NETHER_PORTAL" -> EntitySpawnReason.STRUCTURE;
            case "DISPENSE_EGG" -> EntitySpawnReason.DISPENSER;
            case "INFECTION", "CURED", "DROWNED", "SHEARED", "PIGLIN_ZOMBIFIED", "FROZEN",
                    "METAMORPHOSIS" -> EntitySpawnReason.CONVERSION;
            case "SHOULDER_ENTITY", "BEEHIVE" -> EntitySpawnReason.LOAD;
            case "PATROL" -> EntitySpawnReason.PATROL;
            case "COMMAND" -> EntitySpawnReason.COMMAND;
            case "BUCKET" -> EntitySpawnReason.BUCKET;
            default -> EntitySpawnReason.NATURAL;
        };
    }


    public record Position(double x, double y, double z) {
    }

    public record SoundEmission(double x, double y, double z, float volume, float pitch) {
    }

    public record ParticleEmission(double x, double y, double z, int count,
                                   double offsetX, double offsetY, double offsetZ, double extra) {
    }

    public record CustomSpawn(NativeEntityRuntime runtime, Position position, String key) {
    }

    @FunctionalInterface
    public interface ChunkLoadedLookup {
        boolean loaded(int chunkX, int chunkZ);
    }

    public static final class Type {
        private final EntityType<?> type;

        private Type(EntityType<?> type) {
            this.type = type;
        }

        public float width() {
            return type.getWidth();
        }

        public float height() {
            return type.getHeight();
        }
    }

    public static final class Sound {
        private final SoundEvent sound;

        private Sound(SoundEvent sound) {
            this.sound = sound;
        }
    }

    public static final class Particle {
        private final ParticleType<?> type;

        private Particle(ParticleType<?> type) {
            this.type = type;
        }

        public boolean simple() {
            return type instanceof SimpleParticleType;
        }
    }
}

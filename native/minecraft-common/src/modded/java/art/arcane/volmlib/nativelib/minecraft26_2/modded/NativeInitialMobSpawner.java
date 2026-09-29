package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.Optional;

/**
 * Vanilla's chunk-generation creature pass with the spawn table taken from an explicit biome. Minecraft 26.3 reads
 * the table from the environment attributes at a block position, which cannot express a vanilla spawn biome
 * substituted for a custom Iris biome. The final position check goes through the loader, which fires its
 * spawn-position event on Forge and NeoForge.
 */
final class NativeInitialMobSpawner {
    private static final System.Logger LOGGER = System.getLogger(NativeInitialMobSpawner.class.getName());

    private NativeInitialMobSpawner() {
    }

    static void spawn(WorldGenRegion level, Holder<Biome> biome, ChunkPos chunkPos, RandomSource random,
                      NativeModdedLoader loader) {
        if (!level.getLevel().getGameRules().get(GameRules.SPAWN_MOBS)) {
            return;
        }
        MobSpawnSettings mobSettings = biome.value().getAttributes().applyModifier(
                EnvironmentAttributes.NATURAL_MOB_SPAWNS, EnvironmentAttributes.NATURAL_MOB_SPAWNS.defaultValue());
        WeightedList<MobSpawnSettings.SpawnerData> mobs = mobSettings.getMobsToSpawn(MobCategory.CREATURE);
        if (mobs.isEmpty()) {
            return;
        }
        float creatureProbability = biome.value().getAttributes().applyModifier(
                EnvironmentAttributes.CREATURE_WORLD_GEN_SPAWN_PROBABILITY,
                EnvironmentAttributes.CREATURE_WORLD_GEN_SPAWN_PROBABILITY.defaultValue());
        int xo = chunkPos.getMinBlockX();
        int zo = chunkPos.getMinBlockZ();

        while (random.nextFloat() < creatureProbability) {
            Optional<MobSpawnSettings.SpawnerData> nextSpawnerData = mobs.getRandom(random);
            if (nextSpawnerData.isEmpty()) {
                continue;
            }
            MobSpawnSettings.SpawnerData spawnerData = nextSpawnerData.get();
            int count = spawnerData.count().sample(random);
            SpawnGroupData groupSpawnData = null;
            int x = xo + random.nextInt(16);
            int z = zo + random.nextInt(16);
            int startX = x;
            int startZ = z;

            for (int i = 0; i < count; i++) {
                boolean success = false;

                for (int attempts = 0; !success && attempts < 4; attempts++) {
                    BlockPos pos = topNonCollidingPos(level, spawnerData.type(), x, z);
                    if (spawnerData.type().canSummon() && SpawnPlacements.isSpawnPositionOk(spawnerData.type(), level, pos)) {
                        float width = spawnerData.type().getWidth();
                        double fx = Mth.clamp(x, (double) xo + width, xo + 16.0 - width);
                        double fz = Mth.clamp(z, (double) zo + width, zo + 16.0 - width);
                        if (!level.noCollision(spawnerData.type().getSpawnAABB(fx, pos.getY(), fz))
                                || !SpawnPlacements.checkSpawnRules(spawnerData.type(), level,
                                EntitySpawnReason.CHUNK_GENERATION, BlockPos.containing(fx, pos.getY(), fz),
                                level.getRandom())) {
                            continue;
                        }

                        Entity entity;
                        try {
                            entity = spawnerData.type().create(level.getLevel(), EntitySpawnReason.NATURAL);
                        } catch (Exception error) {
                            LOGGER.log(System.Logger.Level.WARNING, "Failed to create mob", error);
                            continue;
                        }

                        if (entity == null) {
                            continue;
                        }

                        entity.snapTo(fx, pos.getY(), fz, random.nextFloat() * 360.0F, 0.0F);
                        if (entity instanceof Mob mob
                                && loader.checkSpawnPosition(mob, level, EntitySpawnReason.CHUNK_GENERATION)) {
                            groupSpawnData = mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()),
                                    EntitySpawnReason.CHUNK_GENERATION, groupSpawnData);
                            level.addFreshEntityWithPassengers(mob);
                            success = true;
                        }
                    }

                    x += random.nextInt(5) - random.nextInt(5);

                    for (z += random.nextInt(5) - random.nextInt(5);
                         x < xo || x >= xo + 16 || z < zo || z >= zo + 16;
                         z = startZ + random.nextInt(5) - random.nextInt(5)) {
                        x = startX + random.nextInt(5) - random.nextInt(5);
                    }
                }
            }
        }
    }

    private static BlockPos topNonCollidingPos(LevelReader level, EntityType<?> type, int x, int z) {
        int levelHeight = level.getHeight(SpawnPlacements.getHeightmapType(type), x, z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, levelHeight, z);
        if (level.dimensionType().hasCeiling()) {
            do {
                pos.move(Direction.DOWN);
            } while (!level.getBlockState(pos).isAir());

            do {
                pos.move(Direction.DOWN);
            } while (level.getBlockState(pos).isAir() && pos.getY() > level.getMinY());
        }

        return SpawnPlacements.getPlacementType(type).adjustSpawnPosition(level, pos.immutable());
    }
}

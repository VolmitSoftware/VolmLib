package art.arcane.volmlib.nativelib.v26_2_R1.terrain;

import art.arcane.volmlib.nativelib.terrain.BiomeColor;
import art.arcane.volmlib.nativelib.terrain.BlockProperty;
import art.arcane.volmlib.nativelib.terrain.NativeBlockAccess;
import art.arcane.volmlib.nativelib.terrain.TileWriteScheduler;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.json.JSONObject;
import art.arcane.volmlib.util.math.Vector3d;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.awt.Color;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.commands.data.BlockDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.block.CraftBlockStates;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Contract;

public class NativeBlockAccessImpl extends NativeChunkAccessImpl implements NativeBlockAccess {
    private static final Logger LOG = Logger.getLogger("VolmLib-Native");

    private volatile RegistryAccess registryAccess;

    protected RegistryAccess registry() {
        RegistryAccess access = registryAccess;
        if (access != null) {
            return access;
        }
        synchronized (this) {
            access = registryAccess;
            if (access == null) {
                access = ((CraftServer) Bukkit.getServer()).getHandle().getServer().registryAccess();
                registryAccess = access;
            }
        }
        return access;
    }

    @Override
    public boolean hasTile(Location l) {
        return ((CraftWorld) l.getWorld()).getHandle().getBlockEntity(new BlockPos(l.getBlockX(), l.getBlockY(), l.getBlockZ())) != null;
    }

    @Override
    public boolean hasTile(Material material) {
        return !CraftBlockState.class.equals(CraftBlockStates.getBlockStateType(material));
    }

    @Override
    public String getEntitySpawnCategory(String key) {
        Identifier identifier = Identifier.parse(key);
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(identifier)) {
            throw new IllegalArgumentException("Unknown native entity type: " + key);
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(identifier);
        return type.getCategory().getSerializedName().toLowerCase(Locale.ROOT);
    }

    @Override
    @SuppressWarnings("unchecked")
    public KMap<String, Object> serializeTile(Location location) {
        BlockEntity e = ((CraftWorld) location.getWorld()).getHandle().getBlockEntity(new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()));

        if (e == null) {
            return null;
        }

        net.minecraft.nbt.CompoundTag tag = e.saveWithoutMetadata(registry());
        return (KMap<String, Object>) convertFromTag(tag, 0, 64);
    }

    @Contract(value = "null, _, _ -> null", pure = true)
    protected Object convertFromTag(Tag tag, int depth, int maxDepth) {
        if (tag == null || depth > maxDepth) return null;
        return switch (tag) {
            case CollectionTag collection -> {
                KList<Object> list = new KList<>();

                for (Object i : collection) {
                    if (i instanceof Tag t)
                        list.add(convertFromTag(t, depth + 1, maxDepth));
                    else list.add(i);
                }
                yield  list;
            }
            case net.minecraft.nbt.CompoundTag compound -> {
                KMap<String, Object> map = new KMap<>();

                for (String key : compound.keySet()) {
                    Tag child = compound.get(key);
                    if (child == null) continue;
                    Object value = convertFromTag(child, depth + 1, maxDepth);
                    if (value == null) continue;
                    map.put(key, value);
                }
                yield map;
            }
            case NumericTag numeric -> numeric.box();
            default -> tag.asString().orElse(null);
        };
    }

    protected void merge(ServerLevel level, BlockPos blockPos, net.minecraft.nbt.CompoundTag tag) {
        if (level == null || blockPos == null || tag == null) {
            return;
        }

        try {
            BlockEntity blockEntity = level.getBlockEntity(blockPos);
            if (blockEntity == null) {
                LOG.warning("[NMS] BlockEntity not found at " + blockPos);
                BlockState state = level.getBlockState(blockPos);
                if (!state.hasBlockEntity()) {
                    return;
                }

                blockEntity = ((EntityBlock) state.getBlock())
                        .newBlockEntity(blockPos, state);
            }

            BlockDataAccessor accessor = new BlockDataAccessor(blockEntity, blockPos);
            accessor.setData(accessor.getData().merge(tag));
        } catch (Throwable e) {
            LOG.warning("[NMS] Failed to merge tile data at " + blockPos + ": " + e.getMessage());
            LOG.log(Level.SEVERE, "Native block or entity operation failed", e);
        }
    }

    protected Tag convertToTag(Object object, int depth, int maxDepth) {
        if (object == null || depth > maxDepth) return EndTag.INSTANCE;
        return switch (object) {
            case Map<?, ?> map -> {
                net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                for (Map.Entry<?, ?> i : map.entrySet()) {
                    tag.put(i.getKey().toString(), convertToTag(i.getValue(), depth + 1, maxDepth));
                }
                yield tag;
            }
            case List<?> list -> {
                ListTag tag = new ListTag();
                for (Object i : list) {
                    tag.add(convertToTag(i, depth + 1, maxDepth));
                }
                yield tag;
            }
            case Byte number -> ByteTag.valueOf(number);
            case Short number -> ShortTag.valueOf(number);
            case Integer number -> IntTag.valueOf(number);
            case Long number -> LongTag.valueOf(number);
            case Float number -> FloatTag.valueOf(number);
            case Double number -> DoubleTag.valueOf(number);
            case String string -> StringTag.valueOf(string);
            default -> EndTag.INSTANCE;
        };
    }

    public ItemStack applyCustomNbt(ItemStack itemStack, KMap<String, Object> customNbt) throws IllegalArgumentException {
        if (customNbt != null && !customNbt.isEmpty()) {
            net.minecraft.world.item.ItemStack s = CraftItemStack.asNMSCopy(itemStack);

            try {
                net.minecraft.nbt.CompoundTag tag = TagParser.parseCompoundFully((new JSONObject(customNbt)).toString());
                tag.merge(s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
                s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            } catch (CommandSyntaxException var5) {
                throw new IllegalArgumentException(var5);
            }

            return CraftItemStack.asBukkitCopy(s);
        } else {
            return itemStack;
        }
    }

    public Vector3d getBoundingbox(org.bukkit.entity.EntityType entity) {
        if (entity == null) {
            return null;
        }

        try {
            // Registry lookup instead of an EntityType static-field scan: 26.2 moved the constants
            // to a separate EntityTypes holder class that 26.1.2 does not have, while the registry
            // resolves identically on both. ENTITY_TYPE is a DefaultedRegistry, so guard containsKey
            // to avoid silently resolving unknown keys to the default entry.
            Identifier key = Identifier.fromNamespaceAndPath(entity.getKey().getNamespace(), entity.getKey().getKey());
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(key)) {
                return null;
            }
            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getValue(key);
            if (entityType == null) {
                return null;
            }
            return new Vector3d(entityType.getWidth(), entityType.getHeight(), entityType.getWidth());
        } catch (Throwable e) {
            LOG.severe("Unable to get entity dimensions for " + entity + "!");
            LOG.log(Level.SEVERE, "Native block or entity operation failed", e);
            return null;
        }
    }

    @Override
    public Entity spawnEntity(Location location,  org.bukkit.entity.EntityType type, CreatureSpawnEvent.SpawnReason reason) {
        if (location == null || location.getWorld() == null || type == null || type.getEntityClass() == null) {
            return null;
        }
        CraftWorld world = (CraftWorld) location.getWorld();
        if (world.getDifficulty() == Difficulty.PEACEFUL) {
            EntityType<?> nativeType = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(type.getKey().toString()));
            if (nativeType == null || !nativeType.isAllowedInPeaceful()) {
                return null;
            }
        }
        return world.spawn(location, type.getEntityClass(), null, reason);
    }

    @Override
    public Color getBiomeColor(Location location, BiomeColor type) {
        ServerLevel reader = ((CraftWorld) location.getWorld()).getHandle();
        BlockPos pos = new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        Holder<Biome> holder = reader.getBiome(pos);
        Biome biome = holder.value();
        if (biome == null) throw new IllegalArgumentException("Invalid biome: " + holder.unwrapKey().orElse(null));

        EnvironmentAttributeSystem attributes = reader.environmentAttributes();
        int rgba = switch (type) {
            case FOG -> attributes.getValue(EnvironmentAttributes.FOG_COLOR, pos);
            case WATER -> biome.getWaterColor();
            case WATER_FOG -> attributes.getValue(EnvironmentAttributes.WATER_FOG_COLOR, pos);
            case SKY -> attributes.getValue(EnvironmentAttributes.SKY_COLOR, pos);
            case FOLIAGE -> biome.getFoliageColor();
            case GRASS -> biome.getGrassColor(location.getBlockX(), location.getBlockZ());
        };
        if (rgba == 0) {
            if (BiomeColor.FOLIAGE == type && biome.getSpecialEffects().foliageColorOverride().isEmpty())
                return null;
            if (BiomeColor.GRASS == type && biome.getSpecialEffects().grassColorOverride().isEmpty())
                return null;
        }
        return new Color(rgba, true);
    }

    @Override
    public KMap<Material, List<BlockProperty>> getBlockProperties() {
        KMap<Material, List<BlockProperty>> states = new KMap<>();

        for (Block block : registry().lookupOrThrow(Registries.BLOCK)) {
            BlockState state = block.defaultBlockState();
            if (state == null) state = block.getStateDefinition().any();
            BlockState finalState = state;

            states.put(CraftMagicNumbers.getMaterial(block), block.getStateDefinition()
                    .getProperties()
                    .stream()
                    .map(p -> createProperty(p, finalState))
                    .toList());
        }
        return states;
    }

    private <T extends Comparable<T>> BlockProperty createProperty(Property<T> property, BlockState state) {
        return new BlockProperty(property.getName(), property.getValueClass(), state.getValue(property), property.getPossibleValues(), property::getName);
    }

    @Override
    public void deserializeTile(KMap<String, Object> map, Location pos, TileWriteScheduler scheduler) {
        if (map == null || pos == null || pos.getWorld() == null) {
            return;
        }

        Tag converted = convertToTag(map, 0, 64);
        if (!(converted instanceof net.minecraft.nbt.CompoundTag tag)) {
            return;
        }

        ServerLevel level = ((CraftWorld) pos.getWorld()).getHandle();
        BlockPos blockPos = new BlockPos(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ());
        if (scheduler.owns(pos)) {
            merge(level, blockPos, tag);
            return;
        }
        if (!scheduler.schedule(pos, () -> merge(level, blockPos, tag))) {
            LOG.warning("[NMS] Failed to schedule tile deserialize at " + blockPos + " in world " + pos.getWorld().getName());
        }
    }
}

package art.arcane.volmlib.nativelib.v26_3_R1.terrain;

import art.arcane.volmlib.nativelib.terrain.NativeBiomeAccess;
import art.arcane.volmlib.util.cache.AtomicCache;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterBiomeInject;
import art.arcane.volmlib.util.nbt.mca.NBTWorldSupport;
import art.arcane.volmlib.util.nbt.mca.palette.MCABiomeContainer;
import art.arcane.volmlib.util.nbt.mca.palette.MCAChunkBiomeContainer;
import art.arcane.volmlib.util.nbt.mca.palette.MCAGlobalPalette;
import art.arcane.volmlib.util.nbt.mca.palette.MCAIdMap;
import art.arcane.volmlib.util.nbt.mca.palette.MCAIdMapper;
import art.arcane.volmlib.util.nbt.mca.palette.MCAPalette;
import art.arcane.volmlib.util.nbt.mca.palette.MCAPaletteAccess;
import art.arcane.volmlib.util.nbt.mca.palette.MCAPalettedContainer;
import art.arcane.volmlib.util.nbt.mca.palette.MCAWrappedPalettedContainer;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.jetbrains.annotations.NotNull;

public class NativeBiomeAccessImpl extends NativeRegistryAccessImpl implements NativeBiomeAccess {
    private static final Logger LOG = Logger.getLogger("VolmLib-Native");

    private final KMap<Biome, Holder<net.minecraft.world.level.biome.Biome>> baseBiomeCache = new KMap<>();
    private final AtomicCache<MCAIdMap<net.minecraft.world.level.biome.Biome>> biomeMapCache = new AtomicCache<>();
    private final AtomicCache<MCAIdMapper<BlockState>> registryCache = new AtomicCache<>();
    private final AtomicCache<MCAPalette<BlockState>> globalCache = new AtomicCache<>();

    protected Registry<net.minecraft.world.level.biome.Biome> getCustomBiomeRegistry() {
        return registry().lookup(Registries.BIOME).orElseThrow(() -> new IllegalStateException(
                "Iris cannot resolve the Minecraft biome registry on this server version"));
    }

    private Registry<Block> getBlockRegistry() {
        return registry().lookup(Registries.BLOCK).orElse(null);
    }

    private net.minecraft.world.level.biome.Biome getBiomeBaseFromId(int id) {
        return getCustomBiomeRegistry().get(id).map(Holder::value).orElse(null);
    }

    @Override
    public String getTrueBiomeBaseKey(Location location) {
        Holder<net.minecraft.world.level.biome.Biome> biome = ((CraftWorld) location.getWorld()).getHandle()
                .getBiome(new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
        Identifier key = getCustomBiomeRegistry().getKey(biome.value());
        if (key == null) {
            throw new IllegalStateException("No registry key exists for biome " + biome.value());
        }
        return key.getPath();
    }

    @Override
    public int getBiomeId(Location location) {
        Holder<net.minecraft.world.level.biome.Biome> biome = ((CraftWorld) location.getWorld()).getHandle()
                .getBiome(new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
        return getCustomBiomeRegistry().getId(biome.value());
    }

    @Override
    public boolean hasBiome(String key) {
        return getCustomBiomeRegistry().getValue(Identifier.parse(key)) != null;
    }

    public int getBiomeId(String key) {
        return getCustomBiomeRegistry().getId(getCustomBiomeRegistry().get(net.minecraft.resources.Identifier.parse(key)).map(Holder::value).orElse(null));
    }

    private Holder<net.minecraft.world.level.biome.Biome> getBiomeBase(Registry<net.minecraft.world.level.biome.Biome> registry, Biome biome) {
        Holder<net.minecraft.world.level.biome.Biome> v = baseBiomeCache.get(biome);

        if (v != null) {
            return v;
        }
        //noinspection unchecked
        v = biomeToBiomeBase(registry, biome);
        if (v == null) {
            // Ok so there is this new biome name called "CUSTOM" in Paper's new releases.
            // But, this does NOT exist within CraftBukkit which makes it return an error.
            // So, we will just return the ID that the plains biome returns instead.
            //noinspection unchecked
            return biomeToBiomeBase(registry, Biome.PLAINS);
        }
        baseBiomeCache.put(biome, v);
        return v;
    }

    @Override
    public int getBiomeId(Biome biome) {
        for (World i : Bukkit.getWorlds()) {
            if (i.getEnvironment().equals(World.Environment.NORMAL)) {
                Registry<net.minecraft.world.level.biome.Biome> registry = ((CraftWorld) i).getHandle().registryAccess().lookup(Registries.BIOME).orElse(null);
                if (registry == null) {
                    continue;
                }
                Holder<net.minecraft.world.level.biome.Biome> base = getBiomeBase(registry, biome);
                if (base != null) {
                    return registry.getId(base.value());
                }
            }
        }

        List<Biome> biomes = new ArrayList<>();
        for (Biome entry : org.bukkit.Registry.BIOME) {
            biomes.add(entry);
        }
        int index = biomes.indexOf(biome);
        return Math.max(index, 0);
    }

    private MCAIdMap<net.minecraft.world.level.biome.Biome> getBiomeMapping() {
        return biomeMapCache.aquire(() -> new MCAIdMap<>() {
            @NotNull
                    public Iterator<net.minecraft.world.level.biome.Biome> iterator() {
                return getCustomBiomeRegistry().iterator();
            }

            @Override
            public int getId(net.minecraft.world.level.biome.Biome paramT) {
                return getCustomBiomeRegistry().getId(paramT);
            }

            @Override
            public net.minecraft.world.level.biome.Biome byId(int paramInt) {
                return getBiomeBaseFromId(paramInt);
            }
        });
    }

    @NotNull
    private MCABiomeContainer getBiomeContainerInterface(MCAIdMap<net.minecraft.world.level.biome.Biome> biomeMapping, MCAChunkBiomeContainer<net.minecraft.world.level.biome.Biome> base) {
        return new MCABiomeContainer() {
                    public int[] getData() {
                return base.writeBiomes();
            }

            @Override
            public void setBiome(int x, int y, int z, int id) {
                base.setBiome(x, y, z, biomeMapping.byId(id));
            }

            @Override
            public int getBiome(int x, int y, int z) {
                return biomeMapping.getId(base.getBiome(x, y, z));
            }
        };
    }

    @Override
    public MCABiomeContainer newBiomeContainer(int min, int max, int[] data) {
        MCAChunkBiomeContainer<net.minecraft.world.level.biome.Biome> base = new MCAChunkBiomeContainer<>(getBiomeMapping(), min, max, data);
        return getBiomeContainerInterface(getBiomeMapping(), base);
    }

    @Override
    public MCABiomeContainer newBiomeContainer(int min, int max) {
        MCAChunkBiomeContainer<net.minecraft.world.level.biome.Biome> base = new MCAChunkBiomeContainer<>(getBiomeMapping(), min, max);
        return getBiomeContainerInterface(getBiomeMapping(), base);
    }

    @Override
    public int countCustomBiomes() {
        AtomicInteger a = new AtomicInteger(0);

        getCustomBiomeRegistry().keySet().forEach((i) -> {
            if (i.getNamespace().equals("minecraft")) {
                return;
            }

            a.incrementAndGet();
            LOG.fine("Custom Biome: " + i);
        });

        return a.get();
    }

    public void setBiomes(int cx, int cz, World world, Hunk<Object> biomes) {
        LevelChunk c = ((CraftWorld) world).getHandle().getChunk(cx, cz);
        biomes.iterateSync((x, y, z, b) -> c.setNoiseBiome(x, y, z, (Holder<net.minecraft.world.level.biome.Biome>) b));
        c.markUnsaved();
    }

    @Override
    public MCAPaletteAccess createPalette(NBTWorldSupport.BlockStateCodec<BlockData> codec) {
        MCAIdMapper<BlockState> registry = registryCache.aquireNastyPrint(() -> {
            Field cf = IdMapper.class.getDeclaredField("tToId");
            Field df = IdMapper.class.getDeclaredField("idToT");
            Field bf = IdMapper.class.getDeclaredField("nextId");
            cf.setAccessible(true);
            df.setAccessible(true);
            bf.setAccessible(true);
            IdMapper<BlockState> blockData = Block.BLOCK_STATE_REGISTRY;
            int b = bf.getInt(blockData);
            Object2IntMap<BlockState> c = (Object2IntMap<BlockState>) cf.get(blockData);
            List<BlockState> d = (List<BlockState>) df.get(blockData);
            return new MCAIdMapper<BlockState>(c, d, b);
        });
        if (registry == null) {
            throw new IllegalStateException("Iris cannot mirror the Minecraft block state id map on this server version");
        }
        MCAPalette<BlockState> global = globalCache.aquireNastyPrint(() -> new MCAGlobalPalette<>(registry, ((CraftBlockData) AIR).getState()));
        if (global == null) {
            throw new IllegalStateException("Iris cannot build the global block state palette on this server version");
        }
        java.util.Map<art.arcane.volmlib.util.nbt.tag.Tag<?>, BlockState> innerDecodeCache = new java.util.concurrent.ConcurrentHashMap<>(64);
        java.util.Map<CompoundTag, BlockState> outerDecodeCache = new java.util.concurrent.ConcurrentHashMap<>(64);
        MCAPalettedContainer<BlockState> container = new MCAPalettedContainer<>(global, registry,
                i -> innerDecodeCache.computeIfAbsent(i, t -> ((CraftBlockData) codec.decode(t)).getState()),
                i -> codec.encode(CraftBlockData.createData(i)),
                ((CraftBlockData) AIR).getState());
        return new MCAWrappedPalettedContainer<>(container,
                i -> codec.encode(CraftBlockData.createData(i)),
                i -> outerDecodeCache.computeIfAbsent(i, t -> ((CraftBlockData) codec.decode(t)).getState()));
    }

    @Override
    public void injectBiomesFromMantle(Chunk e, Mantle<Matter> mantle) {
        ChunkAccess chunk = ((CraftChunk) e).getHandle(ChunkStatus.FULL);
        AtomicInteger c = new AtomicInteger();
        AtomicInteger r = new AtomicInteger();
        mantle.iterateChunk(e.getX(), e.getZ(), MatterBiomeInject.class, (x, y, z, b) -> {
            if (b != null) {
                if (b.isCustom()) {
                    chunk.setNoiseBiome(x, y, z, getCustomBiomeRegistry().get(b.getBiomeId()).get());
                    c.getAndIncrement();
                } else {
                    chunk.setNoiseBiome(x, y, z, getCustomBiomeRegistry()
                            .get(net.minecraft.resources.Identifier.parse(b.getBiomeKey()))
                            .orElseGet(() -> getCustomBiomeRegistry()
                                    .get(net.minecraft.resources.Identifier.parse("minecraft:plains"))
                                    .orElseThrow()));
                    r.getAndIncrement();
                }
            }
        });
    }

    public static Holder<net.minecraft.world.level.biome.Biome> biomeToBiomeBase(Registry<net.minecraft.world.level.biome.Biome> registry, Biome biome) {
        if (registry == null || biome == null) {
            return null;
        }

        NamespacedKey biomeKey = resolveBiomeKey(biome);
        if (biomeKey == null) {
            return null;
        }

        ResourceKey<net.minecraft.world.level.biome.Biome> key = ResourceKey.create(Registries.BIOME, CraftNamespacedKey.toMinecraft(biomeKey));
        return registry.get(key).orElse(null);
    }

    private static NamespacedKey resolveBiomeKey(Biome biome) {
        Object keyOrNullValue = invokeNoThrow(biome, "getKeyOrNull", new Class<?>[0]);
        if (keyOrNullValue instanceof NamespacedKey namespacedKey) {
            return namespacedKey;
        }

        Object keyOrThrowValue = invokeNoThrow(biome, "getKeyOrThrow", new Class<?>[0]);
        if (keyOrThrowValue instanceof NamespacedKey namespacedKey) {
            return namespacedKey;
        }

        Object keyValue = invokeNoThrow(biome, "getKey", new Class<?>[0]);
        if (keyValue instanceof NamespacedKey namespacedKey) {
            return namespacedKey;
        }

        return null;
    }

    private static Object invokeNoThrow(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        if (target == null) {
            return null;
        }

        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }
}

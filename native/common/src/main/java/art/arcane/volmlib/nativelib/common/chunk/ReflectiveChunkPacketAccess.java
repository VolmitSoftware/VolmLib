package art.arcane.volmlib.nativelib.common.chunk;

import art.arcane.volmlib.nativelib.chunk.ChunkPacketAccess;

import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ReflectiveChunkPacketAccess implements ChunkPacketAccess {
    private static final String CHUNK_PACKET_CLASS =
        "net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket";
    private static final String[] CHUNK_SOURCE_LOOKUPS = {"getChunkAtIfLoadedImmediately", "getChunkNow"};
    private static final String[] LEVEL_LOOKUPS = {"getChunkIfLoaded"};
    private static final Constructor<?>[] NO_CONSTRUCTORS = new Constructor<?>[0];

    private static final NmsMemberCache.Resolver<Method> HANDLE_RESOLVER =
        owner -> findMethod(owner, "getHandle");
    private static final NmsMemberCache.Resolver<Method> CHUNK_SOURCE_RESOLVER =
        owner -> findMethod(owner, "getChunkSource");
    private static final NmsMemberCache.Resolver<Method> LIGHT_ENGINE_RESOLVER =
        owner -> findMethod(owner, "getLightEngine");
    private static final NmsMemberCache.Resolver<Method> CHUNK_SOURCE_LOOKUP_RESOLVER =
        owner -> findCoordinateLookup(owner, CHUNK_SOURCE_LOOKUPS);
    private static final NmsMemberCache.Resolver<Method> LEVEL_LOOKUP_RESOLVER =
        owner -> findCoordinateLookup(owner, LEVEL_LOOKUPS);
    private static final NmsMemberCache.Resolver<Field> CONNECTION_RESOLVER =
        ReflectiveChunkPacketAccess::findConnectionField;

    private final Class<?> packetClass;
    private final Constructor<?>[] packetConstructors;
    private final NmsMemberCache<Method> playerHandles;
    private final NmsMemberCache<Method> worldHandles;
    private final NmsMemberCache<Method> chunkSources;
    private final NmsMemberCache<Method> chunkSourceLookups;
    private final NmsMemberCache<Method> levelLookups;
    private final NmsMemberCache<Method> lightEngines;
    private final NmsMemberCache<Field> connections;
    private final NmsMemberCache<Method> senders;
    private final NmsMemberCache.Resolver<Method> senderResolver;
    private volatile PacketConstructorSelection packetConstructorSelection;

    public ReflectiveChunkPacketAccess() {
        this(resolvePacketClass());
    }

    ReflectiveChunkPacketAccess(Class<?> packetClass) {
        this.packetClass = packetClass;
        this.packetConstructors = resolvePacketConstructors(packetClass);
        this.playerHandles = new NmsMemberCache<>();
        this.worldHandles = new NmsMemberCache<>();
        this.chunkSources = new NmsMemberCache<>();
        this.chunkSourceLookups = new NmsMemberCache<>();
        this.levelLookups = new NmsMemberCache<>();
        this.lightEngines = new NmsMemberCache<>();
        this.connections = new NmsMemberCache<>();
        this.senders = new NmsMemberCache<>();
        this.senderResolver = owner -> findSend(owner, packetClass);
    }

    @Override
    public boolean supported() {
        return packetClass != null;
    }

    @Override
    public boolean sendChunk(Player player, World world, int chunkX, int chunkZ) throws ReflectiveOperationException {
        if (player == null || world == null || packetClass == null) {
            return false;
        }
        Object serverPlayer = invoke(playerHandles.get(player.getClass(), HANDLE_RESOLVER), player);
        Object serverLevel = invoke(worldHandles.get(world.getClass(), HANDLE_RESOLVER), world);
        if (serverPlayer == null || serverLevel == null) {
            return false;
        }
        Object levelChunk = lookupChunk(serverLevel, chunkX, chunkZ);
        if (levelChunk == null) {
            return false;
        }
        Method lightEngineMethod = lightEngines.get(serverLevel.getClass(), LIGHT_ENGINE_RESOLVER);
        if (lightEngineMethod == null) {
            return false;
        }
        Object lightEngine = lightEngineMethod.invoke(serverLevel);
        Object packet = buildPacket(levelChunk, lightEngine);
        if (packet == null) {
            return false;
        }
        Field connectionField = connections.get(serverPlayer.getClass(), CONNECTION_RESOLVER);
        if (connectionField == null) {
            return false;
        }
        Object connection = connectionField.get(serverPlayer);
        if (connection == null) {
            return false;
        }
        Method send = senders.get(connection.getClass(), senderResolver);
        if (send == null) {
            return false;
        }
        send.invoke(connection, packet);
        return true;
    }

    Method handleMethod(Class<?> owner) {
        return playerHandles.get(owner, HANDLE_RESOLVER);
    }

    Field connectionField(Class<?> owner) {
        return connections.get(owner, CONNECTION_RESOLVER);
    }

    Constructor<?> packetConstructor(Class<?> chunkType, Class<?> lightEngineType) {
        return constructorSelection(chunkType, lightEngineType).constructor;
    }

    private Object lookupChunk(Object serverLevel, int chunkX, int chunkZ) throws ReflectiveOperationException {
        Method chunkSourceMethod = chunkSources.get(serverLevel.getClass(), CHUNK_SOURCE_RESOLVER);
        if (chunkSourceMethod != null) {
            Object chunkSource = chunkSourceMethod.invoke(serverLevel);
            if (chunkSource != null) {
                Method lookup = chunkSourceLookups.get(chunkSource.getClass(), CHUNK_SOURCE_LOOKUP_RESOLVER);
                if (lookup != null) {
                    return lookup.invoke(chunkSource, chunkX, chunkZ);
                }
            }
        }
        Method levelLookup = levelLookups.get(serverLevel.getClass(), LEVEL_LOOKUP_RESOLVER);
        return levelLookup == null ? null : levelLookup.invoke(serverLevel, chunkX, chunkZ);
    }

    private Object buildPacket(Object levelChunk, Object lightEngine) throws ReflectiveOperationException {
        PacketConstructorSelection selection = constructorSelection(
            levelChunk.getClass(),
            lightEngine.getClass()
        );
        Constructor<?> selected = selection.constructor;
        if (selected == null) {
            return null;
        }
        Object[] arguments = selection.fiveParameters
            ? new Object[]{levelChunk, lightEngine, null, null, Boolean.FALSE}
            : new Object[]{levelChunk, lightEngine, null, null};
        return selected.newInstance(arguments);
    }

    private PacketConstructorSelection constructorSelection(Class<?> chunkType, Class<?> lightEngineType) {
        PacketConstructorSelection cached = packetConstructorSelection;
        if (cached != null && cached.chunkType == chunkType && cached.lightEngineType == lightEngineType) {
            return cached;
        }
        Constructor<?> selected = selectPacketConstructor(chunkType, lightEngineType);
        PacketConstructorSelection resolved = new PacketConstructorSelection(
            chunkType,
            lightEngineType,
            selected,
            selected != null && selected.getParameterCount() == 5
        );
        packetConstructorSelection = resolved;
        return resolved;
    }

    private Constructor<?> selectPacketConstructor(Class<?> chunkType, Class<?> lightEngineType) {
        Constructor<?> selected = null;
        for (Constructor<?> candidate : packetConstructors) {
            Class<?>[] parameters = candidate.getParameterTypes();
            if (!parameters[0].isAssignableFrom(chunkType) || !parameters[1].isAssignableFrom(lightEngineType)) {
                continue;
            }
            if (selected == null || candidate.getParameterCount() < selected.getParameterCount()) {
                selected = candidate;
            }
        }
        return selected;
    }

    private static Object invoke(Method method, Object target) throws ReflectiveOperationException {
        return method == null ? null : method.invoke(target);
    }

    private static Field findConnectionField(Class<?> owner) {
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!"connection".equals(field.getName())) {
                    continue;
                }
                field.setAccessible(true);
                return field;
            }
        }
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!field.getType().getSimpleName().contains("PacketListener")) {
                    continue;
                }
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    private static Method findSend(Class<?> connectionType, Class<?> packetType) {
        for (Class<?> type = connectionType; type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                boolean named = "send".equals(method.getName()) || "sendPacket".equals(method.getName());
                if (!named || method.getParameterCount() != 1) {
                    continue;
                }
                if (!method.getParameterTypes()[0].isAssignableFrom(packetType)) {
                    continue;
                }
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static Method findCoordinateLookup(Class<?> owner, String[] names) {
        for (String name : names) {
            for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
                for (Method method : type.getDeclaredMethods()) {
                    if (!name.equals(method.getName()) || method.getParameterCount() != 2) {
                        continue;
                    }
                    Class<?>[] parameters = method.getParameterTypes();
                    if (parameters[0] != int.class || parameters[1] != int.class) {
                        continue;
                    }
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> owner, String name) {
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!name.equals(method.getName()) || method.getParameterCount() != 0) {
                    continue;
                }
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static Constructor<?>[] resolvePacketConstructors(Class<?> packetType) {
        if (packetType == null) {
            return NO_CONSTRUCTORS;
        }
        Constructor<?>[] declared = packetType.getDeclaredConstructors();
        int matched = 0;
        for (Constructor<?> candidate : declared) {
            if (accepts(candidate)) {
                matched++;
            }
        }
        Constructor<?>[] selected = new Constructor<?>[matched];
        int index = 0;
        for (Constructor<?> candidate : declared) {
            if (!accepts(candidate)) {
                continue;
            }
            candidate.setAccessible(true);
            selected[index] = candidate;
            index++;
        }
        return selected;
    }

    private static boolean accepts(Constructor<?> candidate) {
        Class<?>[] parameters = candidate.getParameterTypes();
        if (parameters.length != 4 && parameters.length != 5) {
            return false;
        }
        return parameters.length != 5 || parameters[4] == boolean.class;
    }

    private static Class<?> resolvePacketClass() {
        try {
            return Class.forName(CHUNK_PACKET_CLASS, false, ReflectiveChunkPacketAccess.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError missing) {
            return null;
        }
    }

    private record PacketConstructorSelection(
        Class<?> chunkType,
        Class<?> lightEngineType,
        Constructor<?> constructor,
        boolean fiveParameters
    ) {
    }
}

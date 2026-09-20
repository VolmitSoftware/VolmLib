package art.arcane.volmlib.nativelib.common.terrain;

import art.arcane.volmlib.nativelib.terrain.NativeWorldClock;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;

public final class RuntimeWorldClock implements NativeWorldClock {
    private final AtomicReference<TimeAccessStrategy> timeAccessStrategy = new AtomicReference<>();

    public String description() {
        TimeAccessStrategy strategy = timeAccessStrategy.get();
        return strategy == null ? "deferred" : strategy.description();
    }

    public OptionalLong readDayTime(World world) throws ReflectiveOperationException {
        if (world == null) {
            return OptionalLong.empty();
        }
        TimeAccessStrategy strategy = resolveTimeAccessStrategy(world);
        if (strategy.handleMethod() == null || strategy.readMethod() == null) {
            return OptionalLong.empty();
        }
        Object handle = strategy.handleMethod().invoke(world);
        if (handle == null) {
            return OptionalLong.empty();
        }
        Object value = strategy.readMethod().invoke(strategy.readOwner(handle), strategy.readArguments(handle));
        return value instanceof Number number ? OptionalLong.of(number.longValue()) : OptionalLong.empty();
    }

    public boolean writeDayTime(World world, long dayTime) throws ReflectiveOperationException {
        if (world == null) {
            return false;
        }
        TimeAccessStrategy strategy = resolveTimeAccessStrategy(world);
        if (!strategy.writable()) {
            return false;
        }
        Object handle = strategy.handleMethod().invoke(world);
        if (handle == null) {
            return false;
        }
        Object owner = strategy.writeOwner(handle);
        if (owner == null) {
            return false;
        }
        strategy.writeMethod().invoke(owner, dayTime);
        return true;
    }

    public void syncTime(World world) throws ReflectiveOperationException {
        TimeAccessStrategy strategy = timeAccessStrategy.get();
        if (strategy == null || strategy.syncMethod() == null || Bukkit.getServer() == null) {
            return;
        }
        Object handle = strategy.serverHandleMethod().invoke(Bukkit.getServer());
        if (handle != null) {
            strategy.syncMethod().invoke(handle);
        }
    }

    public boolean hasMutableClock(World world) throws ReflectiveOperationException {
        Object handle = invokeNoArg(world, "getHandle");
        if (handle == null) {
            return false;
        }

        Object dimensionTypeHolder = invokeNoArg(handle, "dimensionTypeRegistration");
        Object dimensionType = unwrapDimensionType(dimensionTypeHolder);
        if (dimensionType == null) {
            return false;
        }

        return !dimensionTypeHasFixedTime(dimensionType);
    }

    private TimeAccessStrategy resolveTimeAccessStrategy(World world) throws ReflectiveOperationException {
        TimeAccessStrategy current = timeAccessStrategy.get();
        if (current != null) {
            return current;
        }

        synchronized (timeAccessStrategy) {
            current = timeAccessStrategy.get();
            if (current != null) {
                return current;
            }

            TimeAccessStrategy resolved = resolveTimeAccess(world);
            timeAccessStrategy.set(resolved);
            return resolved;
        }
    }

    private TimeAccessStrategy resolveTimeAccess(World world) throws ReflectiveOperationException {
        Method handleMethod = resolveZeroArgMethod(world.getClass(), "getHandle");
        if (handleMethod == null) {
            return TimeAccessStrategy.unsupported();
        }

        Object handle = handleMethod.invoke(world);
        if (handle == null) {
            return TimeAccessStrategy.unsupported();
        }

        Method readMethod = resolveZeroArgMethod(handle.getClass(), "getDayTime");
        Method writeMethod = resolveLongArgMethod(handle.getClass(), "setDayTime");
        if (readMethod != null && writeMethod != null) {
            return TimeAccessStrategy.forHandle(handleMethod, readMethod, writeMethod, "runtime_handle#setDayTime");
        }

        Method levelDataMethod = resolveZeroArgMethod(handle.getClass(), "serverLevelData");
        if (levelDataMethod == null) {
            levelDataMethod = resolveZeroArgMethod(handle.getClass(), "getLevelData");
        }
        if (levelDataMethod != null) {
            Object levelData = levelDataMethod.invoke(handle);
            if (levelData != null) {
                Method levelDataReadMethod = resolveZeroArgMethod(levelData.getClass(), "getDayTime");
                Method levelDataWriteMethod = resolveLongArgMethod(levelData.getClass(), "setDayTime");
                if (levelDataReadMethod != null && levelDataWriteMethod != null) {
                    return TimeAccessStrategy.forLevelData(handleMethod, levelDataMethod, levelDataReadMethod, levelDataWriteMethod, "world_data#setDayTime");
                }
            }
        }
        return TimeAccessStrategy.unsupported(handleMethod);
    }

    private static Method resolveZeroArgMethod(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }

        return null;
    }

    private static Method resolveLongArgMethod(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, long.class);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }

        return null;
    }

    private record TimeAccessStrategy(
            Method handleMethod,
            Method levelDataMethod,
            Method readMethod,
            Method writeMethod,
            Method serverHandleMethod,
            Method syncMethod,
            String description
    ) {
        static TimeAccessStrategy forHandle(Method handleMethod, Method readMethod, Method writeMethod, String description) throws ReflectiveOperationException {
            Method serverHandleMethod = resolveCraftServerMethod("getHandle");
            Method syncMethod = resolveServerMethod(serverHandleMethod, "forceTimeSynchronization");
            return new TimeAccessStrategy(handleMethod, null, readMethod, writeMethod, serverHandleMethod, syncMethod, description);
        }

        static TimeAccessStrategy forLevelData(Method handleMethod, Method levelDataMethod, Method readMethod, Method writeMethod, String description) throws ReflectiveOperationException {
            Method serverHandleMethod = resolveCraftServerMethod("getHandle");
            Method syncMethod = resolveServerMethod(serverHandleMethod, "forceTimeSynchronization");
            return new TimeAccessStrategy(handleMethod, levelDataMethod, readMethod, writeMethod, serverHandleMethod, syncMethod, description);
        }

        static TimeAccessStrategy unsupported() {
            return new TimeAccessStrategy(null, null, null, null, null, null, "unsupported");
        }

        static TimeAccessStrategy unsupported(Method handleMethod) {
            return new TimeAccessStrategy(handleMethod, null, null, null, null, null, "unsupported");
        }

        boolean writable() {
            return handleMethod != null && readMethod != null && writeMethod != null;
        }

        Object readOwner(Object handle) throws ReflectiveOperationException {
            if (levelDataMethod == null) {
                return handle;
            }

            return levelDataMethod.invoke(handle);
        }

        Object[] readArguments(Object handle) {
            return new Object[0];
        }

        Object writeOwner(Object handle) throws ReflectiveOperationException {
            if (levelDataMethod == null) {
                return handle;
            }

            return levelDataMethod.invoke(handle);
        }

        private static Method resolveCraftServerMethod(String name) {
            return Bukkit.getServer() == null ? null : resolveZeroArgMethod(Bukkit.getServer().getClass(), name);
        }

        private static Method resolveServerMethod(Method serverHandleMethod, String name) throws ReflectiveOperationException {
            if (serverHandleMethod == null || Bukkit.getServer() == null) {
                return null;
            }
            Object serverHandle = serverHandleMethod.invoke(Bukkit.getServer());
            return serverHandle == null ? null : resolveZeroArgMethod(serverHandle.getClass(), name);
        }
    }
    private static boolean dimensionTypeHasFixedTime(Object dimensionType) throws ReflectiveOperationException {
        Object fixedTimeFlag;
        try {
            fixedTimeFlag = invokeNoArg(dimensionType, "hasFixedTime");
        } catch (NoSuchMethodException ignored) {
            Object fixedTime = invokeNoArg(dimensionType, "fixedTime");
            if (fixedTime instanceof OptionalLong optionalLong) {
                return optionalLong.isPresent();
            }
            if (fixedTime instanceof Optional<?> optional) {
                return optional.isPresent();
            }
            return false;
        }

        return fixedTimeFlag instanceof Boolean && (Boolean) fixedTimeFlag;
    }

    private static Object unwrapDimensionType(Object dimensionTypeHolder) throws ReflectiveOperationException {
        if (dimensionTypeHolder == null) {
            return null;
        }

        Class<?> holderClass = dimensionTypeHolder.getClass();
        if (holderClass.getName().startsWith("net.minecraft.world.level.dimension.")) {
            return dimensionTypeHolder;
        }

        Method valueMethod = holderClass.getMethod("value");
        return valueMethod.invoke(dimensionTypeHolder);
    }

    private static Object invokeNoArg(Object instance, String methodName) throws ReflectiveOperationException {
        Method method = instance.getClass().getMethod(methodName);
        return method.invoke(instance);
    }

}

package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;

public final class NativeChunkSystemProbe {
    private static final String[] C2ME_MARKERS = {
            "com.ishland.c2me.base.ModProperties",
            "com.ishland.c2me.base.common.config.C2MEConfig",
            "com.ishland.c2me.opts.chunkio.ModProperties"
    };
    private static final String C2ME_CONFIG = "com.ishland.c2me.base.common.config.C2MEConfig";
    private static final String[] C2ME_SECTIONS = {"asyncScheduling", "asyncSchedulingConfig", "threadedWorldGen"};
    private static final String[] C2ME_FLAGS = {"enabled", "isEnabled", "shouldEnable"};
    private static final String MOONRISE_MARKER = "ca.spottedleaf.moonrise.common.util.MoonriseCommon";
    private static final String[] MOONRISE_WORKER_COUNTS = {"getWorkerThreads", "workerThreads"};
    private static final String[] MOONRISE_POOLS = {"WORKER_POOL", "workerPool"};
    private static final String[] MOONRISE_POOL_COUNTS = {"getCoreThreads", "getThreadCount", "getThreads", "coreThreads", "threadCount"};

    private final ClassLoader classLoader;
    private final Diagnostics diagnostics;

    public NativeChunkSystemProbe(ClassLoader classLoader, Diagnostics diagnostics) {
        this.classLoader = Objects.requireNonNull(classLoader);
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }

    public ChunkSystem detect() {
        ChunkSystem detected = probeC2ME();
        if (detected == null) {
            detected = probeMoonrise();
        }
        return detected == null ? new ChunkSystem(false, "vanilla") : detected;
    }

    private ChunkSystem probeC2ME() {
        if (!anyPresent(C2ME_MARKERS)) {
            return null;
        }
        Class<?> config = loadOrNull(C2ME_CONFIG);
        if (config == null) {
            return new ChunkSystem(false, "c2me present, config class missing");
        }
        Boolean enabled = readSectionFlag(config, C2ME_SECTIONS, C2ME_FLAGS);
        if (enabled == null) {
            return new ChunkSystem(false, "c2me present, async scheduling unreadable");
        }
        return new ChunkSystem(enabled, enabled ? "c2me async scheduling on" : "c2me async scheduling off");
    }

    private ChunkSystem probeMoonrise() {
        Class<?> marker = loadOrNull(MOONRISE_MARKER);
        if (marker == null) {
            return null;
        }
        Integer workers = readMoonriseWorkers(marker);
        if (workers == null) {
            return new ChunkSystem(false, "moonrise present, worker pool unreadable");
        }
        if (workers <= 0) {
            return new ChunkSystem(false, "moonrise worker pool empty");
        }
        return new ChunkSystem(true, "moonrise workers=" + workers);
    }

    /**
     * Reads {@code <configClass>.<section>.<flag>} where the section may itself be the boolean.
     * Returns null when nothing along the path is readable.
     */
    private Boolean readSectionFlag(Class<?> configClass, String[] sections, String[] flags) {
        for (String section : sections) {
            Field field = staticFieldOrNull(configClass, section);
            if (field == null) {
                continue;
            }
            Object value;
            try {
                value = field.get(null);
            } catch (Throwable e) {
                diagnostics.debug("Chunk system probe could not read {}.{}: {}", configClass.getName(), section, e.toString());
                continue;
            }
            if (value instanceof Boolean flag) {
                return flag;
            }
            if (value == null) {
                continue;
            }
            Boolean nested = readBooleanMember(value, flags);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private Boolean readBooleanMember(Object owner, String[] names) {
        for (String name : names) {
            for (Class<?> type = owner.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
                Field field = declaredFieldOrNull(type, name);
                if (field != null && (field.getType() == boolean.class || field.getType() == Boolean.class)) {
                    try {
                        if (field.get(owner) instanceof Boolean flag) {
                            return flag;
                        }
                    } catch (Throwable e) {
                        diagnostics.debug("Chunk system probe could not read {}.{}: {}", type.getName(), name, e.toString());
                    }
                }
                Method method = declaredMethodOrNull(type, name);
                if (method != null && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
                    try {
                        if (method.invoke(owner) instanceof Boolean flag) {
                            return flag;
                        }
                    } catch (Throwable e) {
                        diagnostics.debug("Chunk system probe could not call {}.{}(): {}", type.getName(), name, e.toString());
                    }
                }
            }
        }
        return null;
    }

    private Integer readMoonriseWorkers(Class<?> marker) {
        for (String name : MOONRISE_WORKER_COUNTS) {
            Integer direct = readIntMember(marker, null, name);
            if (direct != null) {
                return direct;
            }
            Field field = staticFieldOrNull(marker, name);
            if (field != null) {
                try {
                    Object value = field.get(null);
                    if (value instanceof Number number) {
                        return number.intValue();
                    }
                } catch (Throwable e) {
                    diagnostics.debug("Chunk system probe could not read {}.{}: {}", marker.getName(), name, e.toString());
                }
            }
        }
        for (String poolName : MOONRISE_POOLS) {
            Field field = staticFieldOrNull(marker, poolName);
            if (field == null) {
                continue;
            }
            Object pool;
            try {
                pool = field.get(null);
            } catch (Throwable e) {
                diagnostics.debug("Chunk system probe could not read {}.{}: {}", marker.getName(), poolName, e.toString());
                continue;
            }
            if (pool == null) {
                continue;
            }
            for (String countName : MOONRISE_POOL_COUNTS) {
                Integer count = readIntMember(pool.getClass(), pool, countName);
                if (count != null) {
                    return count;
                }
            }
        }
        return null;
    }

    private Integer readIntMember(Class<?> type, Object owner, String name) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            Method method = declaredMethodOrNull(current, name);
            if (method == null) {
                continue;
            }
            try {
                if (method.invoke(owner) instanceof Number number) {
                    return number.intValue();
                }
            } catch (Throwable e) {
                diagnostics.debug("Chunk system probe could not call {}.{}(): {}", current.getName(), name, e.toString());
            }
        }
        return null;
    }

    private Field staticFieldOrNull(Class<?> type, String name) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            Field field = declaredFieldOrNull(current, name);
            if (field != null) {
                return field;
            }
        }
        return null;
    }

    private Field declaredFieldOrNull(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            return null;
        } catch (Throwable e) {
            diagnostics.debug("Chunk system probe could not access field {}.{}: {}", type.getName(), name, e.toString());
            return null;
        }
    }

    private Method declaredMethodOrNull(Class<?> type, String name) {
        try {
            Method method = type.getDeclaredMethod(name);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            return null;
        } catch (Throwable e) {
            diagnostics.debug("Chunk system probe could not access method {}.{}(): {}", type.getName(), name, e.toString());
            return null;
        }
    }

    private boolean anyPresent(String[] markers) {
        for (String marker : markers) {
            if (loadOrNull(marker) != null) {
                return true;
            }
        }
        return false;
    }

    private Class<?> loadOrNull(String name) {
        try {
            return Class.forName(name, false, classLoader);
        } catch (Throwable e) {
            diagnostics.debug("Chunk system probe: {} absent ({})", name, e.getClass().getSimpleName());
            return null;
        }
    }

    public record ChunkSystem(boolean parallel, String description) {
    }

    public interface Diagnostics {
        void debug(String message, Object... values);
    }
}

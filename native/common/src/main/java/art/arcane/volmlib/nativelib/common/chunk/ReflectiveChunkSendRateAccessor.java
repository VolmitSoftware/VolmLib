package art.arcane.volmlib.nativelib.common.chunk;

import art.arcane.volmlib.nativelib.chunk.ChunkSendRateAccessor;
import art.arcane.volmlib.nativelib.chunk.ChunkSendRateLimit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

public class ReflectiveChunkSendRateAccessor implements ChunkSendRateAccessor {
    public static final String GLOBAL_CONFIGURATION_CLASS = "io.papermc.paper.configuration.GlobalConfiguration";

    private final Map<ChunkSendRateLimit, Binding> bindings;
    private final String description;
    private final boolean available;

    public ReflectiveChunkSendRateAccessor() {
        this(resolve(ReflectiveChunkSendRateAccessor.class.getClassLoader()));
    }

    private ReflectiveChunkSendRateAccessor(ReflectiveChunkSendRateAccessor resolved) {
        this(resolved.bindings, resolved.description, resolved.available);
    }

    private ReflectiveChunkSendRateAccessor(Map<ChunkSendRateLimit, Binding> bindings, String description, boolean available) {
        this.bindings = bindings;
        this.description = description;
        this.available = available;
    }

    public static ReflectiveChunkSendRateAccessor resolve() {
        return resolve(ReflectiveChunkSendRateAccessor.class.getClassLoader());
    }

    static ReflectiveChunkSendRateAccessor resolve(ClassLoader loader) {
        Object configuration;
        try {
            Class<?> type = Class.forName(GLOBAL_CONFIGURATION_CLASS, true, Objects.requireNonNull(loader));
            Method get = type.getMethod("get");
            configuration = get.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException error) {
            return unsupported(GLOBAL_CONFIGURATION_CLASS + " absent");
        }

        if (configuration == null) {
            return unsupported(GLOBAL_CONFIGURATION_CLASS + " uninitialised");
        }

        return bind(configuration, "Paper GlobalConfiguration");
    }

    static ReflectiveChunkSendRateAccessor bind(Object configuration, String description) {
        Objects.requireNonNull(configuration);
        Map<ChunkSendRateLimit, Binding> bindings = new EnumMap<>(ChunkSendRateLimit.class);
        for (ChunkSendRateLimit limit : ChunkSendRateLimit.values()) {
            Binding binding = bindLimit(configuration, limit);
            if (binding != null) {
                bindings.put(limit, binding);
            }
        }
        return new ReflectiveChunkSendRateAccessor(bindings, description, true);
    }

    static ReflectiveChunkSendRateAccessor unsupported(String description) {
        return new ReflectiveChunkSendRateAccessor(Map.of(), description, false);
    }

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public String describe() {
        return description;
    }

    @Override
    public OptionalDouble read(ChunkSendRateLimit limit) {
        Binding binding = bindings.get(Objects.requireNonNull(limit));
        if (binding == null) {
            return OptionalDouble.empty();
        }

        try {
            return OptionalDouble.of(binding.field().getDouble(binding.owner()));
        } catch (IllegalAccessException | RuntimeException error) {
            return OptionalDouble.empty();
        }
    }

    @Override
    public boolean write(ChunkSendRateLimit limit, double value) {
        Binding binding = bindings.get(Objects.requireNonNull(limit));
        if (binding == null) {
            return false;
        }

        try {
            binding.field().setDouble(binding.owner(), value);
            return true;
        } catch (IllegalAccessException | RuntimeException error) {
            return false;
        }
    }

    private static Binding bindLimit(Object configuration, ChunkSendRateLimit limit) {
        try {
            Object owner = configuration.getClass().getField("chunkLoadingBasic").get(configuration);
            Field field = owner.getClass().getField(switch (limit) {
                case SEND -> "playerMaxChunkSendRate";
                case LOAD -> "playerMaxChunkLoadRate";
            });
            if (field.getType() != double.class || Modifier.isFinal(field.getModifiers()) || Modifier.isStatic(field.getModifiers())) {
                return null;
            }

            return new Binding(owner, field);
        } catch (ReflectiveOperationException | RuntimeException error) {
            return null;
        }
    }

    private record Binding(Object owner, Field field) {
    }
}

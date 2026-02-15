package art.arcane.volmlib.util.director.context;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class DirectorContextMap {
    private final Map<Class<?>, Object> context = new ConcurrentHashMap<>();

    public <T> void put(Class<T> type, T value) {
        if (type == null || value == null) {
            return;
        }

        context.put(type, value);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(Class<T> type) {
        return Optional.ofNullable((T) context.get(type));
    }

    public boolean contains(Class<?> type) {
        return context.containsKey(type);
    }
}

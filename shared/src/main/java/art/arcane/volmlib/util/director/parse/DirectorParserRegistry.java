package art.arcane.volmlib.util.director.parse;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class DirectorParserRegistry {
    private final Map<Class<?>, DirectorParser<?>> parsers = new ConcurrentHashMap<>();

    public <T> void register(Class<T> type, DirectorParser<T> parser) {
        if (type == null || parser == null) {
            return;
        }

        parsers.put(type, parser);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<DirectorParser<T>> get(Class<T> type) {
        return Optional.ofNullable((DirectorParser<T>) parsers.get(type));
    }

    public boolean isRegistered(Class<?> type) {
        return parsers.containsKey(type);
    }
}

package art.arcane.volmlib.util.director.context;

import art.arcane.volmlib.util.director.runtime.DirectorInvocation;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class DirectorContextRegistry {
    private final Map<Class<?>, DirectorContextResolver<?>> resolvers = new ConcurrentHashMap<>();

    public <T> void register(Class<T> type, DirectorContextResolver<T> resolver) {
        if (type == null || resolver == null) {
            return;
        }

        resolvers.put(type, resolver);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<DirectorContextResolver<T>> getResolver(Class<T> type) {
        return Optional.ofNullable((DirectorContextResolver<T>) resolvers.get(type));
    }

    public <T> Optional<T> resolve(Class<T> type, DirectorInvocation invocation, DirectorContextMap context) {
        return getResolver(type).map(resolver -> resolver.resolve(invocation, context));
    }
}

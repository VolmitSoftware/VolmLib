package art.arcane.volmlib.util.director.context;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public final class DirectorContextHandlers {
    private DirectorContextHandlers() {
    }

    @SuppressWarnings("unchecked")
    public static <H> Map<Class<?>, H> build(Iterable<?> instances, Class<?> handlerType, Function<H, Class<?>> typeResolver) {
        Map<Class<?>, H> handlers = new HashMap<>();
        for (Object instance : instances) {
            if (handlerType.isInstance(instance)) {
                H handler = (H) instance;
                handlers.put(typeResolver.apply(handler), handler);
            }
        }

        return handlers;
    }

    public static <H> Map<Class<?>, H> buildOrEmpty(
            Iterable<?> instances,
            Class<?> handlerType,
            Function<H, Class<?>> typeResolver,
            Consumer<Throwable> errorReporter) {
        try {
            return build(instances, handlerType, typeResolver);
        } catch (Throwable e) {
            if (errorReporter != null) {
                errorReporter.accept(e);
            } else {
                e.printStackTrace();
            }
            return new HashMap<>();
        }
    }
}

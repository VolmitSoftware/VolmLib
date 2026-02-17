package art.arcane.volmlib.util.director;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

import java.lang.reflect.Parameter;
import java.util.concurrent.atomic.AtomicReference;

public abstract class DirectorParameterBase {
    private final Parameter parameter;
    private final Param param;
    private final transient AtomicReference<DirectorParameterHandler<?>> handlerCache = new AtomicReference<>();

    protected DirectorParameterBase(Parameter parameter) {
        this.parameter = parameter;
        this.param = parameter.getDeclaredAnnotation(Param.class);
        if (param == null) {
            throw new RuntimeException("Cannot instantiate DirectorParameter on " + parameter.getName() + " in method "
                    + parameter.getDeclaringExecutable().getName() + "(...) in class "
                    + parameter.getDeclaringExecutable().getDeclaringClass().getCanonicalName()
                    + " not annotated by @Param");
        }
    }

    protected abstract boolean useSystemHandler(Class<?> customHandler);

    protected abstract DirectorParameterHandler<?> getSystemHandler(Class<?> type);

    protected void onHandlerFailure(Throwable throwable) {
        throwable.printStackTrace();
    }

    public DirectorParameterHandler<?> getHandler() {
        DirectorParameterHandler<?> cached = handlerCache.get();
        if (cached != null) {
            return cached;
        }

        synchronized (handlerCache) {
            cached = handlerCache.get();
            if (cached != null) {
                return cached;
            }

            DirectorParameterHandler<?> resolved = null;
            try {
                Class<?> customHandler = param.customHandler();
                if (useSystemHandler(customHandler)) {
                    resolved = getSystemHandler(getType());
                } else {
                    Object instance = customHandler.getConstructor().newInstance();
                    if (instance instanceof DirectorParameterHandler<?>) {
                        resolved = (DirectorParameterHandler<?>) instance;
                    } else {
                        throw new IllegalStateException("Custom handler " + customHandler.getName()
                                + " does not implement DirectorParameterHandler");
                    }
                }
            } catch (Throwable e) {
                onHandlerFailure(e);
            }

            handlerCache.set(resolved);
            return resolved;
        }
    }

    public Class<?> getType() {
        return parameter.getType();
    }

    public String getName() {
        return param.name().isEmpty() ? parameter.getName() : param.name();
    }

    public String getDescription() {
        return param.description().isEmpty() ? Param.DEFAULT_DESCRIPTION : param.description();
    }

    public boolean isRequired() {
        return !hasDefault();
    }

    public KList<String> getNames() {
        KList<String> d = new KList<>();

        for (String i : param.aliases()) {
            if (!i.isEmpty()) {
                d.add(i);
            }
        }

        d.add(getName());
        d.removeDuplicates();

        return d;
    }

    public Object getDefaultValue() throws DirectorParsingException {
        DirectorParameterHandler<?> handler = getHandler();
        return param.defaultValue().trim().isEmpty() || handler == null
                ? null
                : handler.parse(param.defaultValue().trim(), true);
    }

    public boolean hasDefault() {
        return !param.defaultValue().trim().isEmpty();
    }

    public String example() {
        DirectorParameterHandler<?> handler = getHandler();
        if (handler == null) {
            return "NOEXAMPLE";
        }

        KList<?> ff = handler.getPossibilities();
        ff = ff != null ? ff : new KList<>();
        KList<String> f = ff.convert(handler::toStringForce);
        if (f.isEmpty()) {
            f = new KList<>();
            f.add(handler.getRandomDefault());
        }

        return f.getRandom();
    }

    public boolean isContextual() {
        return param.contextual();
    }

    public Parameter getParameter() {
        return parameter;
    }

    public Param getParam() {
        return param;
    }
}

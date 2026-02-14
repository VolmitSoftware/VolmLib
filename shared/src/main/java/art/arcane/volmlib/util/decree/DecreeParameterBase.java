package art.arcane.volmlib.util.decree;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.decree.annotations.Param;
import art.arcane.volmlib.util.decree.exceptions.DecreeParsingException;

import java.lang.reflect.Parameter;
import java.util.concurrent.atomic.AtomicReference;

public abstract class DecreeParameterBase {
    private final Parameter parameter;
    private final Param param;
    private final transient AtomicReference<DecreeParameterHandler<?>> handlerCache = new AtomicReference<>();

    protected DecreeParameterBase(Parameter parameter) {
        this.parameter = parameter;
        this.param = parameter.getDeclaredAnnotation(Param.class);
        if (param == null) {
            throw new RuntimeException("Cannot instantiate DecreeParameter on " + parameter.getName() + " in method "
                    + parameter.getDeclaringExecutable().getName() + "(...) in class "
                    + parameter.getDeclaringExecutable().getDeclaringClass().getCanonicalName()
                    + " not annotated by @Param");
        }
    }

    protected abstract boolean useSystemHandler(Class<?> customHandler);

    protected abstract DecreeParameterHandler<?> getSystemHandler(Class<?> type);

    protected void onHandlerFailure(Throwable throwable) {
        throwable.printStackTrace();
    }

    public DecreeParameterHandler<?> getHandler() {
        DecreeParameterHandler<?> cached = handlerCache.get();
        if (cached != null) {
            return cached;
        }

        synchronized (handlerCache) {
            cached = handlerCache.get();
            if (cached != null) {
                return cached;
            }

            DecreeParameterHandler<?> resolved = null;
            try {
                Class<?> customHandler = param.customHandler();
                if (useSystemHandler(customHandler)) {
                    resolved = getSystemHandler(getType());
                } else {
                    Object instance = customHandler.getConstructor().newInstance();
                    if (instance instanceof DecreeParameterHandler<?>) {
                        resolved = (DecreeParameterHandler<?>) instance;
                    } else {
                        throw new IllegalStateException("Custom handler " + customHandler.getName()
                                + " does not implement DecreeParameterHandler");
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

    public Object getDefaultValue() throws DecreeParsingException {
        DecreeParameterHandler<?> handler = getHandler();
        return param.defaultValue().trim().isEmpty() || handler == null
                ? null
                : handler.parse(param.defaultValue().trim(), true);
    }

    public boolean hasDefault() {
        return !param.defaultValue().trim().isEmpty();
    }

    public String example() {
        DecreeParameterHandler<?> handler = getHandler();
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

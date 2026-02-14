package art.arcane.volmlib.util.decree;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.decree.annotations.Decree;
import art.arcane.volmlib.util.decree.annotations.Param;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public abstract class DecreeNodeBase<P> {
    private final Method method;
    private final Object instance;
    private final Decree decree;

    protected DecreeNodeBase(Object instance, Method method) {
        this.instance = instance;
        this.method = method;
        this.decree = method.getDeclaredAnnotation(Decree.class);
        if (decree == null) {
            throw new RuntimeException("Cannot instantiate DecreeNode on method " + method.getName() + " in "
                    + method.getDeclaringClass().getCanonicalName() + " not annotated by @Decree");
        }
    }

    protected abstract P createParameter(Parameter parameter);

    /**
     * Get the parameters of this decree node.
     *
     * @return The list of parameters if all are annotated by @{@link Param}
     */
    public KList<P> getParameters() {
        KList<P> required = new KList<>();
        KList<P> optional = new KList<>();

        for (Parameter i : method.getParameters()) {
            P p = createParameter(i);
            if (p instanceof DecreeParameterBase dp && dp.isRequired()) {
                required.add(p);
            } else {
                optional.add(p);
            }
        }

        required.addAll(optional);
        return required;
    }

    public String getName() {
        return decree.name().isEmpty() ? method.getName() : decree.name();
    }

    public DecreeOrigin getOrigin() {
        return decree.origin();
    }

    public String getDescription() {
        return decree.description().isEmpty() ? Decree.DEFAULT_DESCRIPTION : decree.description();
    }

    public KList<String> getNames() {
        KList<String> d = new KList<>();
        d.add(getName());

        for (String i : decree.aliases()) {
            if (!i.isEmpty()) {
                d.add(i);
            }
        }

        d.removeDuplicates();
        return d;
    }

    public boolean isSync() {
        return decree.sync();
    }

    public Method getMethod() {
        return method;
    }

    public Object getInstance() {
        return instance;
    }

    public Decree getDecree() {
        return decree;
    }
}

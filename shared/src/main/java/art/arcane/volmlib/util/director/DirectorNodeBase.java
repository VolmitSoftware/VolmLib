package art.arcane.volmlib.util.director;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public abstract class DirectorNodeBase<P> {
    private final Method method;
    private final Object instance;
    private final Director director;

    protected DirectorNodeBase(Object instance, Method method) {
        this.instance = instance;
        this.method = method;
        this.director = method.getDeclaredAnnotation(Director.class);
        if (director == null) {
            throw new RuntimeException("Cannot instantiate DirectorNode on method " + method.getName() + " in "
                    + method.getDeclaringClass().getCanonicalName() + " not annotated by @Director");
        }
    }

    protected abstract P createParameter(Parameter parameter);

    /**
     * Get the parameters of this director node.
     *
     * @return The list of parameters if all are annotated by @{@link Param}
     */
    public KList<P> getParameters() {
        KList<P> required = new KList<>();
        KList<P> optional = new KList<>();

        for (Parameter i : method.getParameters()) {
            P p = createParameter(i);
            if (p instanceof DirectorParameterBase dp && dp.isRequired()) {
                required.add(p);
            } else {
                optional.add(p);
            }
        }

        required.addAll(optional);
        return required;
    }

    public String getName() {
        return director.name().isEmpty() ? method.getName() : director.name();
    }

    public DirectorOrigin getOrigin() {
        return director.origin();
    }

    public String getDescription() {
        return director.description().isEmpty() ? Director.DEFAULT_DESCRIPTION : director.description();
    }

    public KList<String> getNames() {
        KList<String> d = new KList<>();
        d.add(getName());

        for (String i : director.aliases()) {
            if (!i.isEmpty()) {
                d.add(i);
            }
        }

        d.removeDuplicates();
        return d;
    }

    public boolean isSync() {
        return director.sync();
    }

    public Method getMethod() {
        return method;
    }

    public Object getInstance() {
        return instance;
    }

    public Director getDirector() {
        return director;
    }
}

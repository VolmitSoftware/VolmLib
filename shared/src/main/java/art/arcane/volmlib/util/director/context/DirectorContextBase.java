package art.arcane.volmlib.util.director.context;

public class DirectorContextBase<T> {
    private final DirectorThreadContext<T> context = new DirectorThreadContext<>();

    public T get() {
        return context.get();
    }

    public void touch(T value) {
        context.touch(value);
    }

    public void remove() {
        context.remove();
    }
}

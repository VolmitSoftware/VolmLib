package art.arcane.volmlib.util.decree.context;

public class DecreeThreadContext<T> {
    private final ThreadLocal<T> context = new ThreadLocal<>();

    public T get() {
        return context.get();
    }

    public void touch(T value) {
        context.set(value);
    }

    public void remove() {
        context.remove();
    }
}

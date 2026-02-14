package art.arcane.volmlib.util.decree.context;

public class DecreeContextBase<T> {
    private final DecreeThreadContext<T> context = new DecreeThreadContext<>();

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

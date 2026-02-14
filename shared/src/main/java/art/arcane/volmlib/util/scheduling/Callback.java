package art.arcane.volmlib.util.scheduling;

@FunctionalInterface
public interface Callback<T> {
    void run(T value);
}

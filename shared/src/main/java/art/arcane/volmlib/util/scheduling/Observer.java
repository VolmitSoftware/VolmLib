package art.arcane.volmlib.util.scheduling;

@FunctionalInterface
public interface Observer<T> {
    void onChanged(T from, T to);
}

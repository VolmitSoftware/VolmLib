package art.arcane.volmlib.util.scheduling;

public interface Observable<T> {
    T get();

    Observable<T> set(T value);

    boolean has();

    Observable<T> clearObservers();

    Observable<T> observe(Observer<T> observer);
}

package art.arcane.volmlib.util.io;

@FunctionalInterface
public interface ExceptionTriConsumer<T, U, V, E extends Exception> {
    void accept(T t, U u, V v) throws E;
}

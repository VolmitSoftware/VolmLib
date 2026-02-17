package art.arcane.volmlib.util.director.context;

public interface DirectorContextHandlerType<T, S> {
    Class<T> getType();

    T handle(S sender);
}

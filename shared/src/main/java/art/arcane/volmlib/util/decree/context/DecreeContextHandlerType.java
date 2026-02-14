package art.arcane.volmlib.util.decree.context;

public interface DecreeContextHandlerType<T, S> {
    Class<T> getType();

    T handle(S sender);
}

package art.arcane.volmlib.util.director.runtime;

@FunctionalInterface
public interface DirectorExecutionDispatcher {
    DirectorExecutionDispatcher IMMEDIATE = (mode, runnable) -> runnable.run();

    void dispatch(DirectorExecutionMode mode, Runnable runnable);
}

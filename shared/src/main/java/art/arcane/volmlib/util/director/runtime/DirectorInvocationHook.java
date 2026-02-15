package art.arcane.volmlib.util.director.runtime;

public interface DirectorInvocationHook {
    DirectorInvocationHook NOOP = new DirectorInvocationHook() {
    };

    default void beforeInvoke(DirectorInvocation invocation, DirectorRuntimeNode node) {
    }

    default void afterInvoke(DirectorInvocation invocation, DirectorRuntimeNode node) {
    }
}

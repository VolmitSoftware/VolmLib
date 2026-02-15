package art.arcane.volmlib.util.director.context;

import art.arcane.volmlib.util.director.runtime.DirectorInvocation;

@FunctionalInterface
public interface DirectorContextResolver<T> {
    T resolve(DirectorInvocation invocation, DirectorContextMap context);
}

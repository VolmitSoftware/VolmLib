package art.arcane.volmlib.nativelib.common.terrain;

import art.arcane.volmlib.nativelib.terrain.NativeWorkerPool;

import java.lang.reflect.Method;

public final class RuntimeWorkerPool implements NativeWorkerPool {
    private volatile Binding binding;

    public int threadCount() throws ReflectiveOperationException {
        Binding active = binding();
        return ((Thread[]) active.threads().invoke(active.pool())).length;
    }

    public void adjustThreadCount(int count) throws ReflectiveOperationException {
        Binding active = binding();
        active.adjust().invoke(active.pool(), count);
    }

    private Binding binding() throws ReflectiveOperationException {
        Binding active = binding;
        if (active != null) {
            return active;
        }
        synchronized (this) {
            if (binding == null) {
                Object pool = Class.forName("ca.spottedleaf.moonrise.common.util.MoonriseCommon")
                        .getDeclaredField("WORKER_POOL").get(null);
                binding = new Binding(pool, pool.getClass().getDeclaredMethod("getCoreThreads"),
                        pool.getClass().getDeclaredMethod("adjustThreadCount", int.class));
            }
            return binding;
        }
    }

    private record Binding(Object pool, Method threads, Method adjust) {
    }
}

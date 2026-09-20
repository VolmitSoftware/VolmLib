package art.arcane.volmlib.nativelib.terrain;

import art.arcane.volmlib.nativelib.NativeBinding;

@NativeBinding("terrain.NativeWorldLifecycleFactoryImpl")
public interface NativeWorldLifecycleFactory {
    Controller create(NativeWorldLifecyclePolicy policy, String generatorClassName);

    interface Controller {
        boolean injectBukkit();

        void ensureServerLevelInjection();

        void uninjectBukkit();

        boolean isServerStopping();

        ServerShutdownBoundary createServerShutdownBoundary();

        void deferPluginClassLoaderClose();

        void releasePluginClassLoaderClose();
    }
}

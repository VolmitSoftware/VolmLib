package art.arcane.volmlib.nativelib.terrain;

import org.bukkit.generator.ChunkGenerator;

import java.lang.instrument.Instrumentation;

public interface NativeWorldLifecyclePolicy {
    String pluginName();

    String classLoaderLifecycleBridgeName();

    Instrumentation instrumentation();

    void requireClassLoaderCloseDeferral() throws ReflectiveOperationException;

    void retainClassLoader(ClassLoader loader);

    void releaseClassLoader(ClassLoader loader);

    void reportFailure(String message, Throwable failure);

    WorldDefinition stagedWorld(String levelId, ChunkGenerator constructorGenerator, boolean consume);

    record WorldDefinition(String identity, String dimensionTypeKey) {
    }
}

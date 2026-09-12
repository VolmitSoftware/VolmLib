package art.arcane.volmlib.util.noise;

import org.junit.Test;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NoiseRuntimeIsolationTest {
    @Test
    public void noiseAndStreamCompositionRunWithoutAPluginOrServer() throws Exception {
        try (RuntimeClassLoader loader = new RuntimeClassLoader()) {
            Class<?> noiseType = loader.loadClass("art.arcane.volmlib.util.noise.NoiseType");
            Class<?> generatorType = loader.loadClass("art.arcane.volmlib.util.noise.NoiseGenerator");
            Method create = noiseType.getMethod("create", long.class);
            Method sample2D = generatorType.getMethod("noise", double.class, double.class);
            Method sample3D = generatorType.getMethod("noise", double.class, double.class, double.class);
            for (Object type : noiseType.getEnumConstants()) {
                Object generator = create.invoke(type, 1337L);
                Object repeated = create.invoke(type, 1337L);
                double planar = (Double) sample2D.invoke(generator, -37.25D, 91.75D);
                double spatial = (Double) sample3D.invoke(generator, -37.25D, 13.5D, 91.75D);
                assertTrue(type.toString(), Double.isFinite(planar) && Double.isFinite(spatial));
                assertEquals(type.toString(), planar,
                        (Double) sample2D.invoke(repeated, -37.25D, 91.75D), 0D);
            }

            Class<?> randomType = loader.loadClass("art.arcane.volmlib.util.math.RNG");
            Class<?> compositeType = loader.loadClass("art.arcane.volmlib.util.noise.CNG");
            Object random = randomType.getConstructor(long.class).newInstance(7331L);
            Object composite = compositeType.getMethod("signature", randomType).invoke(null, random);
            Object source = compositeType.getMethod("stream").invoke(composite);
            Class<?> streamType = loader.loadClass("art.arcane.volmlib.util.stream.ProceduralStream");
            Object added = streamType.getMethod("add", double.class).invoke(source, 2D);
            Object scaled = streamType.getMethod("multiply", double.class).invoke(added, 0.5D);
            Object factory = streamType.getMethod("interpolate").invoke(scaled);
            Object interpolated = factory.getClass().getMethod("bilinear", int.class).invoke(factory, 4);
            double result = (Double) streamType.getMethod("getDouble", double.class, double.class)
                    .invoke(interpolated, 19.5D, -8.25D);
            assertTrue(Double.isFinite(result));
        }
    }

    private static final class RuntimeClassLoader extends URLClassLoader {
        private RuntimeClassLoader() {
            super(new URL[]{CNG.class.getProtectionDomain().getCodeSource().getLocation()}, CNG.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("org.bukkit.") || name.startsWith("io.papermc.")
                    || name.startsWith("art.arcane.iris.")) {
                throw new ClassNotFoundException(name);
            }
            if (!name.startsWith("art.arcane.volmlib.")) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    loaded = findClass(name);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }
}

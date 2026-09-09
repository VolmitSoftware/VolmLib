package art.arcane.volmlib.util.bukkit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BukkitEntitySpawnsTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void initializesBeforeAddingWithLegacyBukkitCallback() throws Exception {
        verifyCallback("org.bukkit.util.Consumer");
    }

    @Test
    public void initializesBeforeAddingWithJavaCallback() throws Exception {
        verifyCallback("java.util.function.Consumer");
    }

    private void verifyCallback(String callback) throws Exception {
        Path directory = temporary.newFolder().toPath();
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("org/bukkit/Location.java", "package org.bukkit; public class Location {}");
        sources.put("org/bukkit/entity/Entity.java", "package org.bukkit.entity; public interface Entity {}");
        sources.put("org/bukkit/RegionAccessor.java", """
                package org.bukkit;
                import org.bukkit.entity.Entity;
                public interface RegionAccessor {
                    <T extends Entity> T spawn(Location location, Class<T> type, %s<T> initializer);
                }
                """.formatted(callback));
        sources.put("Fixture.java", """
                import org.bukkit.Location;
                import org.bukkit.RegionAccessor;
                import org.bukkit.entity.Entity;
                public class Fixture implements RegionAccessor {
                    public static class Sample implements Entity {
                        public boolean initialized;
                        public boolean added;
                    }
                    public <T extends Entity> T spawn(Location location, Class<T> type, %s<T> initializer) {
                        T entity = type.cast(new Sample());
                        initializer.accept(entity);
                        if (!((Sample) entity).initialized) {
                            throw new IllegalStateException("Entity entered the world before initialization");
                        }
                        ((Sample) entity).added = true;
                        return entity;
                    }
                }
                """.formatted(callback));
        if (callback.startsWith("org.bukkit")) {
            sources.put("org/bukkit/util/Consumer.java",
                    "package org.bukkit.util; public interface Consumer<T> { void accept(T value); }");
        }
        ArrayList<String> arguments = new ArrayList<>();
        arguments.addAll(List.of("--release", "17", "-d", directory.toString()));
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path file = directory.resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
            arguments.add(file.toString());
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        assertEquals(0, compiler.run(null, null, null, arguments.toArray(String[]::new)));
        String helperPath = "art/arcane/volmlib/util/bukkit/BukkitEntitySpawns.class";
        Path helperFile = directory.resolve(helperPath);
        Files.createDirectories(helperFile.getParent());
        try (InputStream input = BukkitEntitySpawnsTest.class.getClassLoader().getResourceAsStream(helperPath)) {
            assertNotNull(input);
            Files.copy(input, helperFile);
        }
        try (URLClassLoader loader = new URLClassLoader(new URL[]{directory.toUri().toURL()}, null)) {
            Class<?> region = loader.loadClass("org.bukkit.RegionAccessor");
            Class<?> location = loader.loadClass("org.bukkit.Location");
            Class<?> entity = loader.loadClass("Fixture$Sample");
            Object world = loader.loadClass("Fixture").getConstructor().newInstance();
            Consumer<Object> initializer = value -> {
                try {
                    assertFalse(entity.getField("added").getBoolean(value));
                    entity.getField("initialized").setBoolean(value, true);
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException(exception);
                }
            };
            Object spawned = loader.loadClass("art.arcane.volmlib.util.bukkit.BukkitEntitySpawns")
                    .getMethod("spawn", region, location, Class.class, Consumer.class)
                    .invoke(null, world, location.getConstructor().newInstance(), entity, initializer);
            assertTrue(entity.getField("initialized").getBoolean(spawned));
            assertTrue(entity.getField("added").getBoolean(spawned));
        }
    }
}

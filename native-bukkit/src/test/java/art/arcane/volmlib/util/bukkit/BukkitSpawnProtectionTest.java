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
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BukkitSpawnProtectionTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void mappedBindingForwardsNativeDecisionAndExactObjectsAndCoordinates() throws Exception {
        try (Fixture fixture = fixture(new Shape(false, "boolean", false, false))) {
            assertTrue(fixture.supported());
            assertEquals("PROTECTED", fixture.check());
            assertEquals("custom-respawn:-12:311:48:owner", fixture.nativeValue("seen"));
            fixture.setNative("result", false);
            assertEquals("ALLOWED", fixture.check());
            assertEquals(2, fixture.nativeValue("calls"));
            assertTrue(fixture.logs.isEmpty());
        }
    }

    @Test
    public void versionedCraftBukkitAndObfuscatedMethodBindByExactSignature() throws Exception {
        try (Fixture fixture = fixture(new Shape(true, "boolean", false, false))) {
            assertTrue(fixture.supported());
            assertEquals("PROTECTED", fixture.check());
            assertEquals("custom-respawn:-12:311:48:owner", fixture.nativeValue("seen"));
        }
    }

    @Test
    public void wrongNativeReturnTypeRejectsCapability() throws Exception {
        try (Fixture fixture = fixture(new Shape(false, "int", false, false))) {
            assertFalse(fixture.supported());
            assertEquals("UNSUPPORTED", fixture.check());
            assertEquals("UNSUPPORTED", fixture.check());
            assertEquals(0, fixture.nativeValue("calls"));
            assertEquals(1, fixture.logs.size());
            assertNotNull(fixture.logs.get(0).getThrown());
        }
    }

    @Test
    public void wrongNativeParameterTypeRejectsCapability() throws Exception {
        try (Fixture fixture = fixture(new Shape(false, "boolean", true, false))) {
            assertFalse(fixture.supported());
            assertEquals("UNSUPPORTED", fixture.check());
            assertEquals(0, fixture.nativeValue("calls"));
        }
    }

    @Test
    public void missingExactBlockPositionConstructorRejectsCapability() throws Exception {
        try (Fixture fixture = fixture(new Shape(false, "boolean", false, true))) {
            assertFalse(fixture.supported());
            assertEquals("UNSUPPORTED", fixture.check());
        }
    }

    @Test
    public void nativeFailureDisablesFurtherInvocationAndReportsFullCauseOnce() throws Exception {
        try (Fixture fixture = fixture(new Shape(false, "boolean", false, false))) {
            fixture.setNative("explode", true);
            assertEquals("UNSUPPORTED", fixture.check());
            assertFalse(fixture.supported());
            assertEquals("UNSUPPORTED", fixture.check());
            assertEquals(1, fixture.nativeValue("calls"));
            assertEquals(1, fixture.logs.size());
            assertTrue(fixture.logs.get(0).getThrown() instanceof IllegalStateException);
            assertTrue(fixture.logs.get(0).getMessage().contains("[-12, 311, 48]"));
        }
    }

    @Test
    public void publicBypassesRemainAvailableWithoutNativeBinding() throws Exception {
        try (Fixture fixture = fixture(new Shape(false, "int", false, false))) {
            fixture.setServer("radius", 0);
            assertEquals("ALLOWED", fixture.check());
            fixture.setServer("radius", -1);
            assertEquals("ALLOWED", fixture.check());
            fixture.setServer("radius", 16);
            fixture.player.getClass().getField("operator").setBoolean(fixture.player, true);
            assertEquals("ALLOWED", fixture.check());
            fixture.player.getClass().getField("operator").setBoolean(fixture.player, false);
            fixture.setServer("hasOperators", false);
            assertEquals("ALLOWED", fixture.check());
            fixture.setServer("hasOperators", true);
            assertEquals("UNSUPPORTED", fixture.check());
            assertEquals(1, fixture.logs.size());
        }
    }

    private Fixture fixture(Shape shape) throws Exception {
        Path directory = temporary.newFolder().toPath();
        String craftPackage = "org.bukkit.craftbukkit" + (shape.obfuscated() ? ".v1_20_R1" : "");
        String worldType = shape.obfuscated() ? "WorldServer" : "ServerLevel";
        String positionType = shape.obfuscated() ? "BlockPosition" : "BlockPos";
        String playerType = shape.obfuscated() ? "EntityHuman" : "Player";
        String serverPlayerType = shape.obfuscated() ? "EntityPlayer" : "ServerPlayer";
        LinkedHashMap<String, String> sources = new LinkedHashMap<>();
        sources.put("org/bukkit/Server.java", """
                package org.bukkit;
                public interface Server {
                    java.util.logging.Logger getLogger();
                    int getSpawnRadius();
                    java.util.Set<Object> getOperators();
                }
                """);
        sources.put("org/bukkit/World.java", "package org.bukkit; public interface World { String getName(); }");
        sources.put("org/bukkit/entity/Player.java", """
                package org.bukkit.entity;
                public interface Player { boolean isOp(); java.util.UUID getUniqueId(); }
                """);
        sources.put("org/bukkit/block/Block.java", """
                package org.bukkit.block;
                public interface Block { org.bukkit.World getWorld(); int getX(); int getY(); int getZ(); }
                """);
        sources.put("net/minecraft/server/level/" + worldType + ".java", """
                package net.minecraft.server.level;
                public class %s { public String name = "custom-respawn"; }
                """.formatted(worldType));
        sources.put("net/minecraft/world/entity/player/" + playerType + ".java", """
                package net.minecraft.world.entity.player;
                public class %s { public String name = "owner"; }
                """.formatted(playerType));
        sources.put("net/minecraft/server/level/" + serverPlayerType + ".java", """
                package net.minecraft.server.level;
                public class %s extends net.minecraft.world.entity.player.%s {}
                """.formatted(serverPlayerType, playerType));
        sources.put("net/minecraft/core/" + positionType + ".java", """
                package net.minecraft.core;
                public class %s {
                    public int x, y, z;
                    public %s(%s x, int y, int z) { this.x = (int) x; this.y = y; this.z = z; }
                }
                """.formatted(positionType, positionType, shape.wrongConstructor() ? "long" : "int"));
        String predicatePlayer = shape.wrongParameter() ? "java.lang.Object" : "net.minecraft.world.entity.player." + playerType;
        sources.put("net/minecraft/server/dedicated/DedicatedServer.java", """
                package net.minecraft.server.dedicated;
                public class DedicatedServer {
                    public boolean result = true;
                    public boolean explode;
                    public int calls;
                    public String seen;
                    public %s %s(net.minecraft.server.level.%s world, net.minecraft.core.%s pos, %s player) {
                        calls++;
                        if (explode) { throw new IllegalStateException("Native query failed"); }
                        seen = world.name + ":" + pos.x + ":" + pos.y + ":" + pos.z + ":" + ((net.minecraft.world.entity.player.%s) player).name;
                        return %s;
                    }
                }
                """.formatted(shape.returnType(), shape.obfuscated() ? "a" : "isUnderSpawnProtection",
                worldType, positionType, predicatePlayer, playerType, shape.returnType().equals("boolean") ? "result" : "1"));
        sources.put(craftPackage.replace('.', '/') + "/CraftServer.java", """
                package %s;
                public class CraftServer implements org.bukkit.Server {
                    public int radius = 16;
                    public boolean hasOperators = true;
                    public java.util.logging.Logger logger = java.util.logging.Logger.getAnonymousLogger();
                    public net.minecraft.server.dedicated.DedicatedServer handle = new net.minecraft.server.dedicated.DedicatedServer();
                    public net.minecraft.server.dedicated.DedicatedServer getServer() { return handle; }
                    public java.util.logging.Logger getLogger() { return logger; }
                    public int getSpawnRadius() { return radius; }
                    public java.util.Set<Object> getOperators() { return hasOperators ? java.util.Set.of("operator") : java.util.Set.of(); }
                }
                """.formatted(craftPackage));
        sources.put(craftPackage.replace('.', '/') + "/CraftWorld.java", """
                package %s;
                public class CraftWorld implements org.bukkit.World {
                    public net.minecraft.server.level.%s handle = new net.minecraft.server.level.%s();
                    public net.minecraft.server.level.%s getHandle() { return handle; }
                    public String getName() { return "custom-world"; }
                }
                """.formatted(craftPackage, worldType, worldType, worldType));
        sources.put(craftPackage.replace('.', '/') + "/entity/CraftPlayer.java", """
                package %s.entity;
                public class CraftPlayer implements org.bukkit.entity.Player {
                    public boolean operator;
                    public net.minecraft.server.level.%s handle = new net.minecraft.server.level.%s();
                    public net.minecraft.server.level.%s getHandle() { return handle; }
                    public boolean isOp() { return operator; }
                    public java.util.UUID getUniqueId() { return new java.util.UUID(0L, 1L); }
                }
                """.formatted(craftPackage, serverPlayerType, serverPlayerType, serverPlayerType));
        sources.put("SampleBlock.java", """
                public class SampleBlock implements org.bukkit.block.Block {
                    public org.bukkit.World getWorld() { return new %s.CraftWorld(); }
                    public int getX() { return -12; }
                    public int getY() { return 311; }
                    public int getZ() { return 48; }
                }
                """.formatted(craftPackage));
        ArrayList<String> arguments = new ArrayList<>(List.of("--release", "17", "-d", directory.toString()));
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path file = directory.resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
            arguments.add(file.toString());
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        assertEquals(0, compiler.run(null, null, null, arguments.toArray(String[]::new)));
        for (String suffix : List.of("", "$Binding", "$NativeNames", "$Decision")) {
            String helper = "art/arcane/volmlib/util/bukkit/BukkitSpawnProtection" + suffix + ".class";
            Path file = directory.resolve(helper);
            Files.createDirectories(file.getParent());
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(helper)) {
                assertNotNull(input);
                Files.copy(input, file);
            }
        }
        return new Fixture(new URLClassLoader(new URL[]{directory.toUri().toURL()}, null), craftPackage);
    }

    private record Shape(boolean obfuscated, String returnType, boolean wrongParameter, boolean wrongConstructor) {
    }

    private static final class Fixture implements AutoCloseable {
        private final URLClassLoader loader;
        private final Object server;
        private final Object player;
        private final Object block;
        private final Object nativeServer;
        private final Object protection;
        private final List<LogRecord> logs = new ArrayList<>();

        private Fixture(URLClassLoader loader, String craftPackage) throws Exception {
            this.loader = loader;
            server = loader.loadClass(craftPackage + ".CraftServer").getConstructor().newInstance();
            player = loader.loadClass(craftPackage + ".entity.CraftPlayer").getConstructor().newInstance();
            block = loader.loadClass("SampleBlock").getConstructor().newInstance();
            nativeServer = server.getClass().getField("handle").get(server);
            Logger logger = (Logger) server.getClass().getMethod("getLogger").invoke(server);
            logger.setUseParentHandlers(false);
            logger.addHandler(new Handler() {
                @Override public void publish(LogRecord record) { logs.add(record); }
                @Override public void flush() {}
                @Override public void close() {}
            });
            protection = loader.loadClass("art.arcane.volmlib.util.bukkit.BukkitSpawnProtection")
                    .getMethod("create", loader.loadClass("org.bukkit.Server")).invoke(null, server);
        }

        private boolean supported() throws Exception {
            return (boolean) protection.getClass().getMethod("supported").invoke(protection);
        }

        private String check() throws Exception {
            return protection.getClass().getMethod("check", loader.loadClass("org.bukkit.entity.Player"),
                    loader.loadClass("org.bukkit.block.Block")).invoke(protection, player, block).toString();
        }

        private Object nativeValue(String name) throws Exception {
            return nativeServer.getClass().getField(name).get(nativeServer);
        }

        private void setNative(String name, boolean value) throws Exception {
            nativeServer.getClass().getField(name).setBoolean(nativeServer, value);
        }

        private void setServer(String name, Object value) throws Exception {
            server.getClass().getField(name).set(server, value);
        }

        @Override
        public void close() throws Exception {
            loader.close();
        }
    }
}

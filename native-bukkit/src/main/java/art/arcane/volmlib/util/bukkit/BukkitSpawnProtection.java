package art.arcane.volmlib.util.bukkit;

import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public final class BukkitSpawnProtection {
    private final Server server;
    private final Binding binding;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    private BukkitSpawnProtection(Server server, Binding binding) {
        this.server = server;
        this.binding = binding;
    }

    public static BukkitSpawnProtection create(Server server) {
        Server required = Objects.requireNonNull(server, "server");
        try {
            return new BukkitSpawnProtection(required, Binding.resolve(required));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            BukkitSpawnProtection protection = new BukkitSpawnProtection(required, null);
            protection.disable("Could not bind native spawn protection on " + required.getClass().getName(), exception);
            return protection;
        }
    }

    public boolean supported() {
        return binding != null && failure.get() == null;
    }

    public Decision check(Player player, Block block) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(block, "block");
        if (!supported()) {
            return publicBypass(player) ? Decision.ALLOWED : Decision.UNSUPPORTED;
        }
        World world = block.getWorld();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        try {
            Object nativeWorld = (Object) binding.world().invokeExact((Object) world);
            Object nativePlayer = (Object) binding.player().invokeExact((Object) player);
            Object position = (Object) binding.position().invokeExact(x, y, z);
            boolean protectedBlock = (boolean) binding.protection().invokeExact(nativeWorld, position, nativePlayer);
            return protectedBlock ? Decision.PROTECTED : Decision.ALLOWED;
        } catch (Throwable exception) {
            if (exception instanceof VirtualMachineError error) {
                throw error;
            }
            if (exception instanceof ThreadDeath death) {
                throw death;
            }
            disable("Native spawn protection failed at " + world.getName() + " [" + x + ", " + y + ", " + z
                    + "] for player " + player.getUniqueId(), exception);
            return publicBypass(player) ? Decision.ALLOWED : Decision.UNSUPPORTED;
        }
    }

    private boolean publicBypass(Player player) {
        return server.getSpawnRadius() <= 0 || player.isOp() || server.getOperators().isEmpty();
    }

    private void disable(String context, Throwable exception) {
        if (failure.compareAndSet(null, exception)) {
            server.getLogger().log(Level.SEVERE, context
                    + "; protected block changes require an available native decision or a verified protection bypass", exception);
        }
    }

    public enum Decision {
        ALLOWED, PROTECTED, UNSUPPORTED
    }

    private record Binding(MethodHandle world, MethodHandle player, MethodHandle position, MethodHandle protection) {
        private static Binding resolve(Server server) throws ReflectiveOperationException {
            Method serverAccessor = server.getClass().getMethod("getServer");
            Class<?> craftServer = serverAccessor.getDeclaringClass();
            String craftPackage = craftServer.getPackageName();
            if (!craftServer.getSimpleName().equals("CraftServer")
                    || !craftPackage.matches("org\\.bukkit\\.craftbukkit(?:\\.v[0-9]+_[0-9]+_R[0-9]+)?")
                    || Modifier.isStatic(serverAccessor.getModifiers())) {
                throw new NoSuchMethodException("Unsupported CraftBukkit server accessor: " + serverAccessor);
            }
            ClassLoader loader = craftServer.getClassLoader();
            Class<?> craftWorld = Class.forName(craftPackage + ".CraftWorld", false, loader);
            Class<?> craftPlayer = Class.forName(craftPackage + ".entity.CraftPlayer", false, loader);
            Method worldAccessor = craftWorld.getMethod("getHandle");
            Method playerAccessor = craftPlayer.getMethod("getHandle");
            if (!World.class.isAssignableFrom(craftWorld) || !Player.class.isAssignableFrom(craftPlayer)
                    || Modifier.isStatic(worldAccessor.getModifiers()) || Modifier.isStatic(playerAccessor.getModifiers())) {
                throw new NoSuchMethodException("Unsupported CraftBukkit world or player accessor");
            }
            Class<?> nativeWorld = worldAccessor.getReturnType();
            NativeNames names = switch (nativeWorld.getName()) {
                case "net.minecraft.server.level.ServerLevel" -> new NativeNames("net.minecraft.core.BlockPos",
                        "net.minecraft.world.entity.player.Player");
                case "net.minecraft.server.level.WorldServer" -> new NativeNames("net.minecraft.core.BlockPosition",
                        "net.minecraft.world.entity.player.EntityHuman");
                default -> throw new NoSuchMethodException("Unsupported native world type: " + nativeWorld.getName());
            };
            Class<?> position = Class.forName(names.position(), false, loader);
            Class<?> nativePlayer = Class.forName(names.player(), false, loader);
            if (!nativePlayer.isAssignableFrom(playerAccessor.getReturnType())) {
                throw new NoSuchMethodException("Unsupported native player accessor: " + playerAccessor);
            }
            Object nativeServer = Objects.requireNonNull(serverAccessor.invoke(server), "native server");
            Method protection;
            try {
                protection = nativeServer.getClass().getMethod("isUnderSpawnProtection", nativeWorld, position, nativePlayer);
            } catch (NoSuchMethodException exception) {
                protection = nativeServer.getClass().getMethod("a", nativeWorld, position, nativePlayer);
            }
            if (protection.getReturnType() != boolean.class || Modifier.isStatic(protection.getModifiers())) {
                throw new NoSuchMethodException("Unsupported native spawn-protection signature: " + protection);
            }
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodType accessor = MethodType.methodType(Object.class, Object.class);
            return new Binding(lookup.unreflect(worldAccessor).asType(accessor),
                    lookup.unreflect(playerAccessor).asType(accessor),
                    lookup.unreflectConstructor(position.getConstructor(int.class, int.class, int.class))
                            .asType(MethodType.methodType(Object.class, int.class, int.class, int.class)),
                    lookup.unreflect(protection).bindTo(nativeServer)
                            .asType(MethodType.methodType(boolean.class, Object.class, Object.class, Object.class)));
        }
    }

    private record NativeNames(String position, String player) {
    }
}

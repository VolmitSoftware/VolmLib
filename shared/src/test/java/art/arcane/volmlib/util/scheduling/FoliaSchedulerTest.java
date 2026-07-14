package art.arcane.volmlib.util.scheduling;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FoliaSchedulerTest {
    private SchedulerHandler schedulerHandler;
    private Server server;
    private Plugin plugin;

    @Before
    public void setUp() throws Exception {
        resetStaticState();
        schedulerHandler = new SchedulerHandler();
        BukkitScheduler scheduler = proxy(BukkitScheduler.class, schedulerHandler);
        server = proxy(Server.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "getLogger" -> Logger.getLogger("FoliaSchedulerTest");
            case "getName" -> "Spigot";
            case "getVersion", "getBukkitVersion", "getMinecraftVersion" -> "26.2";
            case "getScheduler" -> scheduler;
            case "isPrimaryThread" -> false;
            default -> defaultValue(method.getReturnType());
        });
        setStaticField(Bukkit.class, "server", server);
        plugin = plugin(true);
    }

    @After
    public void tearDown() throws Exception {
        resetStaticState();
    }

    @Test
    public void nonFoliaImmediateTasksUseBukkitSchedulers() {
        World world = proxy(World.class, FoliaSchedulerTest::defaultInvocation);
        Entity entity = proxy(Entity.class, FoliaSchedulerTest::defaultInvocation);

        assertTrue(FoliaScheduler.runGlobal(plugin, () -> {
        }));
        assertTrue(FoliaScheduler.runRegion(plugin, world, 2, 3, () -> {
        }));
        assertTrue(FoliaScheduler.runEntity(plugin, entity, () -> {
        }));
        assertTrue(FoliaScheduler.runAsync(plugin, () -> {
        }));

        assertEquals(List.of("runTask", "runTask", "runTask", "runTaskAsynchronously"), schedulerHandler.methodNames());
    }

    @Test
    public void nonFoliaDelayedTasksPreserveTickDelays() {
        World world = proxy(World.class, FoliaSchedulerTest::defaultInvocation);
        Entity entity = proxy(Entity.class, FoliaSchedulerTest::defaultInvocation);

        assertTrue(FoliaScheduler.runGlobal(plugin, () -> {
        }, 7L));
        assertTrue(FoliaScheduler.runRegion(plugin, world, 2, 3, () -> {
        }, 9L));
        assertTrue(FoliaScheduler.runEntity(plugin, entity, () -> {
        }, 11L));
        assertTrue(FoliaScheduler.runAsync(plugin, () -> {
        }, 13L));

        assertEquals(List.of(7L, 9L, 11L, 13L), schedulerHandler.delays());
    }

    @Test
    public void fallbackRejectsInactivePluginsAndSchedulerFailures() {
        assertFalse(FoliaScheduler.runGlobal(plugin(false), () -> {
        }));
        assertTrue(schedulerHandler.methodNames().isEmpty());

        schedulerHandler.rejectScheduling = true;
        assertFalse(FoliaScheduler.runGlobal(plugin, () -> {
        }));
        assertFalse(FoliaScheduler.runAsync(plugin, () -> {
        }));
    }

    @Test
    public void nonFoliaCancellationUsesBukkitScheduler() {
        FoliaScheduler.cancelTasks(plugin);
        assertEquals(List.of("cancelTasks"), schedulerHandler.methodNames());
    }

    @Test
    public void classifyScheduleOutcomeReportsSchedulingAccurately() {
        assertEquals(FoliaScheduler.ScheduleResult.SCHEDULED, FoliaScheduler.classifyScheduleOutcome(true, null));
        assertEquals(FoliaScheduler.ScheduleResult.SCHEDULED, FoliaScheduler.classifyScheduleOutcome(false, Boolean.TRUE));
        assertEquals(FoliaScheduler.ScheduleResult.REJECTED, FoliaScheduler.classifyScheduleOutcome(false, Boolean.FALSE));
        assertEquals(FoliaScheduler.ScheduleResult.REJECTED, FoliaScheduler.classifyScheduleOutcome(false, null));
        assertEquals(FoliaScheduler.ScheduleResult.SCHEDULED, FoliaScheduler.classifyScheduleOutcome(false, new Object()));
    }

    @Test
    public void retiredEntitySchedulingInvokesRetiredAndReportsFailure() {
        List<String> invoked = new ArrayList<>();
        Entity entity = entityWithScheduler(retiredEntityScheduler());

        assertFalse(FoliaScheduler.runEntity(plugin, entity, () -> invoked.add("task"), 0L, () -> invoked.add("retired")));
        assertEquals(List.of("retired"), invoked);

        invoked.clear();
        assertFalse(FoliaScheduler.runEntity(plugin, entity, () -> invoked.add("task"), 5L, () -> invoked.add("retired")));
        assertEquals(List.of("retired"), invoked);
        assertTrue(schedulerHandler.methodNames().isEmpty());
    }

    @Test
    public void retiredEntitySchedulingWithoutRetiredCallbackReturnsFalse() {
        Entity entity = entityWithScheduler(retiredEntityScheduler());

        assertFalse(FoliaScheduler.runEntity(plugin, entity, () -> {
        }, 0L));
        assertFalse(FoliaScheduler.runEntity(plugin, entity, () -> {
        }, 5L));
        assertTrue(schedulerHandler.methodNames().isEmpty());
    }

    @Test
    public void activeEntitySchedulingReportsSuccessWithoutInvokingCallbacks() {
        List<String> invoked = new ArrayList<>();
        EntityScheduler entityScheduler = proxy(EntityScheduler.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "execute" -> true;
            default -> defaultValue(method.getReturnType());
        });
        Entity entity = entityWithScheduler(entityScheduler);

        assertTrue(FoliaScheduler.runEntity(plugin, entity, () -> invoked.add("task"), 0L, () -> invoked.add("retired")));
        assertTrue(invoked.isEmpty());
        assertTrue(schedulerHandler.methodNames().isEmpty());
    }

    private static EntityScheduler retiredEntityScheduler() {
        return proxy(EntityScheduler.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "execute" -> false;
            case "run", "runDelayed", "runAtFixedRate" -> null;
            default -> defaultValue(method.getReturnType());
        });
    }

    private static Entity entityWithScheduler(EntityScheduler entityScheduler) {
        return proxy(Entity.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "getScheduler" -> entityScheduler;
            case "isValid" -> false;
            case "equals" -> proxy == arguments[0];
            case "hashCode" -> System.identityHashCode(proxy);
            default -> defaultValue(method.getReturnType());
        });
    }

    private Plugin plugin(boolean enabled) {
        return proxy(Plugin.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "isEnabled" -> enabled;
            case "getServer" -> server;
            case "getName" -> "TestPlugin";
            case "equals" -> proxy == arguments[0];
            case "hashCode" -> System.identityHashCode(proxy);
            default -> defaultValue(method.getReturnType());
        });
    }

    private static Object defaultInvocation(Object proxy, Method method, Object[] arguments) {
        return switch (method.getName()) {
            case "equals" -> proxy == arguments[0];
            case "hashCode" -> System.identityHashCode(proxy);
            default -> defaultValue(method.getReturnType());
        };
    }

    private static void resetStaticState() throws Exception {
        setStaticField(Bukkit.class, "server", null);
        setStaticField(FoliaScheduler.class, "forcedFoliaThreading", false);
        setStaticField(FoliaScheduler.class, "globalRegionSchedulerHandle", null);
        setStaticField(FoliaScheduler.class, "regionSchedulerHandle", null);
        setStaticField(FoliaScheduler.class, "asyncSchedulerHandle", null);
    }

    private static void setStaticField(Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static final class SchedulerHandler implements InvocationHandler {
        private final List<ScheduledCall> calls = new ArrayList<>();
        private boolean rejectScheduling;

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            String name = method.getName();
            if (name.startsWith("runTask")) {
                if (rejectScheduling) {
                    throw new UnsupportedOperationException("rejected");
                }
                long delay = arguments != null && arguments.length > 0 && arguments[arguments.length - 1] instanceof Long value
                        ? value
                        : 0L;
                calls.add(new ScheduledCall(name, delay));
                return proxy(BukkitTask.class, FoliaSchedulerTest::defaultInvocation);
            }
            if (name.equals("cancelTasks")) {
                calls.add(new ScheduledCall(name, 0L));
                return null;
            }
            return defaultValue(method.getReturnType());
        }

        private List<String> methodNames() {
            return calls.stream().map(ScheduledCall::methodName).toList();
        }

        private List<Long> delays() {
            return calls.stream().map(ScheduledCall::delay).toList();
        }
    }

    private record ScheduledCall(String methodName, long delay) {
    }
}

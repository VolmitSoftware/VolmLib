package art.arcane.volmit.packaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ReflectiveLookupShrinkTest {
    private static final String HOLDER = """
            package fixture;

            import java.lang.invoke.MethodHandle;
            import java.lang.invoke.MethodHandles;
            import java.lang.invoke.MethodType;
            import java.lang.invoke.VarHandle;

            public final class Holder {
                private static final VarHandle MONITOR;
                private static final VarHandle COUNTER;
                private static final MethodHandle HOOK;

                static {
                    try {
                        MethodHandles.Lookup lookup = MethodHandles.lookup();
                        MONITOR = lookup.findVarHandle(Holder.class, "monitor", Object.class);
                        COUNTER = lookup.findStaticVarHandle(Holder.class, "counter", int.class);
                        HOOK = lookup.findVirtual(Holder.class, "hook", MethodType.methodType(void.class, String.class));
                    } catch (ReflectiveOperationException e) {
                        throw new ExceptionInInitializerError(e);
                    }
                }

                private volatile Object monitor;
                private static volatile int counter;

                public void unused() {
                    monitor = null;
                    counter = 0;
                    hook("");
                }

                private void hook(String value) {
                }

                public static boolean ready() {
                    return MONITOR != null && COUNTER != null && HOOK != null;
                }
            }
            """;
    private static final String ENTRY = """
            package fixture;

            public final class Entry {
                public static boolean run() {
                    return Holder.ready();
                }
            }
            """;

    @TempDir
    Path directory;

    @Test
    void reflectivelyResolvedMembersSurviveShrinkingWhenTheirOnlyDirectUsersAreRemoved() throws Exception {
        Path artifact = ShrinkFixtures.jar(directory, "fixture.Entry", Map.of("fixture.Holder", HOLDER, "fixture.Entry", ENTRY));
        ShrinkFixtures.shrink(directory, artifact);
        try (URLClassLoader loader = ShrinkFixtures.loader(artifact)) {
            Method run = loader.loadClass("fixture.Entry").getMethod("run");
            assertEquals(Boolean.TRUE, run.invoke(null));
        }
    }
}

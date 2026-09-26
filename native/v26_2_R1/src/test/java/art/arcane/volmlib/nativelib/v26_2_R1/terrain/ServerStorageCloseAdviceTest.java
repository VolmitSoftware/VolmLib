package art.arcane.volmlib.nativelib.v26_2_R1.terrain;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.modifier.Ownership;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.matcher.ElementMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerStorageCloseAdviceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void signalsOnlyAfterStorageClosesSuccessfully() throws Exception {
        Instrumentation instrumentation = ByteBuddyAgent.install();
        String bridgeName = getClass().getPackageName() + ".StorageCloseBridge";
        DynamicType.Unloaded<?> bridge = new ByteBuddy()
                .subclass(Object.class)
                .name(bridgeName)
                .defineField("closedStorage", Object.class, Visibility.PUBLIC, Ownership.STATIC)
                .defineMethod("serverStorageClosed", void.class, Visibility.PUBLIC, Ownership.STATIC)
                .withParameters(Object.class)
                .intercept(FieldAccessor.ofField("closedStorage").setsArgumentAt(0))
                .make();
        ClassInjector.UsingInstrumentation.of(temporaryDirectory.toFile(),
                        ClassInjector.UsingInstrumentation.Target.SYSTEM, instrumentation)
                .injectRaw(Map.of(bridgeName, bridge.getBytes()));
        Field closedStorage = Class.forName(bridgeName, true, ClassLoader.getSystemClassLoader())
                .getField("closedStorage");
        ClassReloadingStrategy strategy = ClassReloadingStrategy.of(instrumentation);
        new ByteBuddy().redefine(Storage.class)
                .visit(Advice.withCustomMapping()
                        .bind(NativeWorldLifecycle.BridgeClassName.class, bridgeName)
                        .to(NativeWorldLifecycle.ServerStorageCloseAdvice.class)
                        .on(ElementMatchers.named("close")))
                .make()
                .load(Storage.class.getClassLoader(), strategy);
        try {
            Storage failed = new Storage(true);
            assertThrows(IOException.class, failed::close);
            assertNull(closedStorage.get(null));
            Storage successful = new Storage(false);
            successful.close();
            assertSame(successful, closedStorage.get(null));
        } finally {
            strategy.reset(Storage.class);
        }
    }

    public static class Storage {
        private final boolean fail;

        public Storage(boolean fail) {
            this.fail = fail;
        }

        public void close() throws IOException {
            if (fail) {
                throw new IOException("Storage close failed");
            }
        }
    }
}

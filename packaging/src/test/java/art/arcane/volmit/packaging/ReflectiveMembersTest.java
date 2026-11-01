package art.arcane.volmit.packaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ReflectiveMembersTest {
    private static final String TARGET = """
            package fixture;

            public final class Target {
                private int value;
                private void hook() {
                }
            }
            """;
    private static final String CALLER = """
            package fixture;

            import java.lang.invoke.MethodHandles;

            public final class Caller {
                private volatile Object monitor;

                static void direct() throws ReflectiveOperationException {
                    MethodHandles.lookup().findVarHandle(Caller.class, "monitor", Object.class);
                }

                static void forName() throws ReflectiveOperationException {
                    ClassLoader loader = Caller.class.getClassLoader();
                    Class.forName("fixture.Target", false, loader).getDeclaredMethod("hook");
                }

                static void local() throws ReflectiveOperationException {
                    Class<?> type = Target.class;
                    type.getDeclaredField("value");
                }

                static void dynamic(Object instance) throws ReflectiveOperationException {
                    Class<?> unrelated = Target.class;
                    instance.getClass().getDeclaredField("monitor");
                }
            }
            """;

    @TempDir
    Path directory;

    @Test
    void lookupsResolveTheirOwnerFromTheReceiverOrClassArgumentNotTheLastClassConstant() throws IOException {
        Set<ReflectiveMembers.Member> members = new LinkedHashSet<>();
        ReflectiveMembers.collect(new ClassReader(compile()), members);
        assertEquals(Set.of(
                new ReflectiveMembers.Member("fixture.Caller", "monitor", true),
                new ReflectiveMembers.Member("fixture.Target", "hook", false),
                new ReflectiveMembers.Member("fixture.Target", "value", true)), members);
    }

    private byte[] compile() throws IOException {
        Path sources = directory.resolve("src/fixture");
        Path classes = directory.resolve("classes");
        Files.createDirectories(sources);
        Files.createDirectories(classes);
        Files.writeString(sources.resolve("Target.java"), TARGET, StandardCharsets.UTF_8);
        Files.writeString(sources.resolve("Caller.java"), CALLER, StandardCharsets.UTF_8);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int status = compiler.run(null, null, null, "--release", "17", "-d", classes.toString(),
                sources.resolve("Target.java").toString(), sources.resolve("Caller.java").toString());
        assertEquals(0, status, "fixture compilation failed");
        return Files.readAllBytes(classes.resolve("fixture/Caller.class"));
    }
}

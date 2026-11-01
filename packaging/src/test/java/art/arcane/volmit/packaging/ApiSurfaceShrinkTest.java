package art.arcane.volmit.packaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ApiSurfaceShrinkTest {
    private static final Map<String, String> SOURCES = Map.of(
            "art.arcane.fixture.api.FixtureApi", """
                    package art.arcane.fixture.api;

                    import art.arcane.fixture.internal.Helper;
                    import art.arcane.fixture.internal.Widget;
                    import java.util.List;

                    public class FixtureApi {
                        public static Helper helper() {
                            return new Helper();
                        }

                        public static List<Widget> widgets() {
                            return List.of(new Widget());
                        }
                    }
                    """,
            "art.arcane.fixture.api.FixtureSampler", """
                    package art.arcane.fixture.api;

                    public class FixtureSampler extends art.arcane.fixture.internal.TickedBase {
                    }
                    """,
            "art.arcane.fixture.internal.Helper", """
                    package art.arcane.fixture.internal;

                    public class Helper {
                        public Chain unusedPublic() {
                            return new Chain();
                        }

                        void packagePrivate() {
                        }
                    }
                    """,
            "art.arcane.fixture.internal.Chain", """
                    package art.arcane.fixture.internal;

                    public class Chain {
                        public void chained() {
                        }
                    }
                    """,
            "art.arcane.fixture.internal.Widget", """
                    package art.arcane.fixture.internal;

                    public class Widget {
                        public String describe() {
                            return "widget";
                        }
                    }
                    """,
            "art.arcane.fixture.internal.Ticked", """
                    package art.arcane.fixture.internal;

                    public interface Ticked {
                        boolean isBursting();
                    }
                    """,
            "art.arcane.fixture.internal.TickedBase", """
                    package art.arcane.fixture.internal;

                    public class TickedBase implements Ticked {
                        public boolean isBursting() {
                            return false;
                        }
                    }
                    """,
            "art.arcane.fixture.internal.Hidden", """
                    package art.arcane.fixture.internal;

                    public class Hidden {
                        public static int used() {
                            return 1;
                        }

                        public static int extra() {
                            return 2;
                        }
                    }
                    """,
            "art.arcane.fixture.Entry", """
                    package art.arcane.fixture;

                    public final class Entry {
                        public static int run() {
                            return art.arcane.fixture.internal.Hidden.used();
                        }
                    }
                    """);

    @TempDir
    Path directory;

    @Test
    void publicSurfaceOfTypesReachableFromApiSignaturesSurvivesShrinking() throws Exception {
        Path artifact = ShrinkFixtures.jar(directory, "art.arcane.fixture.Entry", SOURCES);
        ShrinkFixtures.shrink(directory, artifact);
        try (URLClassLoader loader = ShrinkFixtures.loader(artifact)) {
            assertTrue(has(loader, "art.arcane.fixture.internal.Helper", "unusedPublic"));
            assertTrue(has(loader, "art.arcane.fixture.internal.Chain", "chained"));
            assertTrue(has(loader, "art.arcane.fixture.internal.Widget", "describe"));
            assertTrue(has(loader, "art.arcane.fixture.internal.Ticked", "isBursting"));
            assertFalse(has(loader, "art.arcane.fixture.internal.Helper", "packagePrivate"));
            assertFalse(has(loader, "art.arcane.fixture.internal.Hidden", "extra"));
        }
    }

    private static boolean has(URLClassLoader loader, String owner, String method) throws ClassNotFoundException {
        return Arrays.stream(loader.loadClass(owner).getDeclaredMethods()).map(Method::getName).anyMatch(method::equals);
    }
}

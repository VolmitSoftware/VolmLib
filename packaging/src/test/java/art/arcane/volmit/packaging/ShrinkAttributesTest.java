package art.arcane.volmit.packaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShrinkAttributesTest {
    private static final String ENTRY = """
            package fixture;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;
            import java.util.List;

            public final class Entry {
                @Retention(RetentionPolicy.CLASS)
                @Target(ElementType.METHOD)
                public @interface BuildOnly {
                }

                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.METHOD)
                public @interface Visible {
                    String value();
                }

                @BuildOnly
                @Visible("kept")
                public static List<String> echo(List<String> requested) {
                    List<String> debuggerOnly = new Helper().remember(requested);
                    if (debuggerOnly == null) {
                        throw new IllegalArgumentException("missing");
                    }
                    return debuggerOnly;
                }

                public static String local() {
                    record Local(int value) {
                    }
                    return new Local(1) + " " + Local.class.getSimpleName();
                }
            }
            """;
    private static final String HELPER = """
            package fixture;

            import java.util.List;

            final class Helper {
                private List<String> cache;

                List<String> remember(List<String> values) {
                    cache = values;
                    return cache;
                }
            }
            """;
    private static final int THROW_LINE = (int) ENTRY.substring(0, ENTRY.indexOf("throw ")).lines().count();

    @TempDir
    Path directory;

    @Test
    void shrinkDropsLocalTablesAndBuildOnlyAnnotationsButKeepsNamesAndRuntimeMetadata() throws Exception {
        Path artifact = fixture();
        Map<String, List<String>> before = members(artifact);

        ShrinkFixtures.shrink(directory, artifact);

        Map<String, List<String>> after = members(artifact);
        assertEquals(before.keySet().stream().filter(name -> !name.contains("Padding")).toList(),
                after.keySet().stream().toList());
        assertEquals(before.get("fixture/Helper.class"), after.get("fixture/Helper.class"));
        assertEquals(before.get("fixture/Entry.class"), after.get("fixture/Entry.class"));
        assertTrue(Files.isRegularFile(directory.resolve("reports/fixture-shrink-mapping.txt")));
        ClassFacts entry = facts(artifact, "fixture/Entry.class");
        assertEquals(0, entry.localVariables, "local variable tables must not ship");
        assertEquals(List.of(), entry.invisibleAnnotations, "class-retention annotations must not ship");
        assertEquals(List.of("requested"), entry.parameters);
        assertEquals("Entry.java", entry.source);
        assertTrue(entry.lines.contains(THROW_LINE), "line numbers must ship: " + entry.lines);
        try (URLClassLoader loader = ShrinkFixtures.loader(artifact)) {
            Method echo = echo(loader);
            assertEquals("requested", echo.getParameters()[0].getName());
            assertEquals("java.util.List<java.lang.String>", echo.getGenericReturnType().getTypeName());
            @SuppressWarnings("unchecked")
            Class<? extends Annotation> visible = (Class<? extends Annotation>) loader.loadClass("fixture.Entry$Visible");
            assertTrue(echo.isAnnotationPresent(visible));
            assertEquals("Local[value=1] Local", loader.loadClass("fixture.Entry").getMethod("local").invoke(null));
            InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                    () -> echo.invoke(null, new Object[]{null}));
            StackTraceElement top = failure.getCause().getStackTrace()[0];
            assertEquals("Entry.java", top.getFileName());
            assertEquals(THROW_LINE, top.getLineNumber());
        }
    }

    @Test
    void parameterNamesAreDroppedOnlyWhenTheArtifactOptsIn() throws Exception {
        Path artifact = fixture();

        ShrinkFixtures.shrink(directory, artifact, policy -> policy.setStripParameterNames(true));

        assertEquals(List.of(), facts(artifact, "fixture/Entry.class").parameters);
        try (URLClassLoader loader = ShrinkFixtures.loader(artifact)) {
            Method echo = echo(loader);
            assertFalse(echo.getParameters()[0].isNamePresent());
            assertEquals(List.of("value"), echo.invoke(null, List.of("value")));
        }
    }

    @Test
    void renamedMappingEntriesFailTheShrink() throws Exception {
        Path identity = directory.resolve("identity.txt");
        Files.writeString(identity, """
                # compiler: R8
                fixture.Entry -> fixture.Entry:
                # {"fileName":"Entry.java","id":"sourceFile"}
                    java.util.List cache -> cache
                    24:30:java.util.List echo(java.util.List) -> echo
                    void <init>() -> <init>
                fixture.Entry$Visible -> fixture.Entry$Visible:
                """, StandardCharsets.UTF_8);
        JarShrinker.verifyNoRenames(identity.toFile());

        for (String renamed : List.of("fixture.Entry -> a.a:", "fixture.Entry -> fixture.Entry:\n    void run() -> a",
                "fixture.Entry -> fixture.Entry:\n    int count -> b")) {
            Path mapping = directory.resolve("renamed.txt");
            Files.writeString(mapping, renamed + "\n", StandardCharsets.UTF_8);
            IOException failure = assertThrows(IOException.class, () -> JarShrinker.verifyNoRenames(mapping.toFile()));
            assertTrue(failure.getMessage().contains(renamed.substring(renamed.lastIndexOf('\n') + 1).trim()),
                    failure.getMessage());
        }
    }

    private Path fixture() throws IOException {
        return ShrinkFixtures.jar(directory, "fixture.Entry", Map.of("fixture.Entry", ENTRY, "fixture.Helper", HELPER),
                List.of("-g", "-parameters"));
    }

    private static Method echo(URLClassLoader loader) throws ReflectiveOperationException {
        return loader.loadClass("fixture.Entry").getMethod("echo", List.class);
    }

    private static Map<String, List<String>> members(Path artifact) throws IOException {
        Map<String, List<String>> members = new TreeMap<>();
        try (ZipFile zip = new ZipFile(artifact.toFile())) {
            for (ZipEntry entry : zip.stream().filter(entry -> entry.getName().endsWith(".class")).toList()) {
                List<String> names = new ArrayList<>();
                try (InputStream input = zip.getInputStream(entry)) {
                    new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override
                        public FieldVisitor visitField(int access, String name, String descriptor, String signature,
                                                       Object value) {
                            names.add("field " + name + " " + descriptor);
                            return null;
                        }

                        @Override
                        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                                         String[] exceptions) {
                            names.add("method " + name + descriptor);
                            return null;
                        }
                    }, ClassReader.SKIP_CODE);
                }
                members.put(entry.getName(), new ArrayList<>(new TreeSet<>(names)));
            }
        }
        return members;
    }

    private static ClassFacts facts(Path artifact, String name) throws IOException {
        ClassFacts facts = new ClassFacts();
        try (ZipFile zip = new ZipFile(artifact.toFile()); InputStream input = zip.getInputStream(zip.getEntry(name))) {
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public void visitSource(String source, String debug) {
                    facts.source = source;
                }

                @Override
                public MethodVisitor visitMethod(int access, String methodName, String descriptor, String signature,
                                                 String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitParameter(String parameter, int parameterAccess) {
                            facts.parameters.add(parameter);
                        }

                        @Override
                        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                            if (!visible) {
                                facts.invisibleAnnotations.add(descriptor);
                            }
                            return null;
                        }

                        @Override
                        public void visitLineNumber(int line, Label start) {
                            facts.lines.add(line);
                        }

                        @Override
                        public void visitLocalVariable(String local, String localDescriptor, String localSignature,
                                                       Label start, Label end, int index) {
                            facts.localVariables++;
                        }

                        @Override
                        public void visitAttribute(Attribute attribute) {
                            throw new AssertionError("unexpected attribute " + attribute.type);
                        }
                    };
                }
            }, 0);
        }
        return facts;
    }

    private static final class ClassFacts {
        private final List<String> parameters = new ArrayList<>();
        private final List<String> invisibleAnnotations = new ArrayList<>();
        private final List<Integer> lines = new ArrayList<>();
        private String source;
        private int localVariables;
    }
}

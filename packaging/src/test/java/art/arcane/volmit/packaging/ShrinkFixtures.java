package art.arcane.volmit.packaging;

import org.gradle.testfixtures.ProjectBuilder;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShrinkFixtures {
    private static final String PADDING = """
            package fixture.padding;

            public final class Padding {
                public static String a() { return "%s"; }
                public static String b() { return "%s"; }
                public static String c() { return "%s"; }
            }
            """.formatted("a".repeat(4096), "b".repeat(4096), "c".repeat(4096));

    private ShrinkFixtures() {
    }

    static Path jar(Path directory, String main, Map<String, String> sources) throws IOException {
        Path sourceRoot = directory.resolve("src");
        Path classes = directory.resolve("classes");
        Files.createDirectories(classes);
        List<String> arguments = new ArrayList<>(List.of("--release", "17", "-d", classes.toString()));
        Map<String, String> all = new LinkedHashMap<>(sources);
        all.put("fixture.padding.Padding", PADDING);
        for (Map.Entry<String, String> source : all.entrySet()) {
            Path file = sourceRoot.resolve(source.getKey().replace('.', '/') + ".java");
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue(), StandardCharsets.UTF_8);
            arguments.add(file.toString());
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertEquals(0, compiler.run(null, null, null, arguments.toArray(String[]::new)), "fixture compilation failed");
        Path artifact = directory.resolve("fixture.jar");
        try (OutputStream file = Files.newOutputStream(artifact);
             ZipOutputStream jar = new ZipOutputStream(file);
             Stream<Path> paths = Files.walk(classes)) {
            jar.putNextEntry(new ZipEntry("plugin.yml"));
            jar.write(("name: Fixture\nmain: " + main + "\n").getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
            for (Path path : (Iterable<Path>) paths.filter(Files::isRegularFile)::iterator) {
                jar.putNextEntry(new ZipEntry(classes.relativize(path).toString().replace(File.separatorChar, '/')));
                jar.write(Files.readAllBytes(path));
                jar.closeEntry();
            }
        }
        return artifact;
    }

    static void shrink(Path directory, Path artifact) throws IOException {
        PackagingArtifact policy = ProjectBuilder.builder().withProjectDir(directory.resolve("project").toFile()).build()
                .getObjects().newInstance(PackagingArtifact.class, "fixture");
        File library = JdkClassLibrary.export(directory.resolve("jdk").toFile());
        ShrinkRules.ShrinkRequest request = new ShrinkRules.ShrinkRequest(artifact.toFile(),
                directory.resolve("fixture.shrink.jar").toFile(), policy, List.of(library),
                directory.resolve("reports").toFile(), "fixture");
        JarShrinker.ShrinkResult result = JarShrinker.shrink(request, ShrinkRules.SHARED_DONTWARN);
        assertTrue(result.applied(), result.reason());
        assertTrue(JarShrinker.classEntries(artifact.toFile()).stream().noneMatch(name -> name.contains("Padding")),
                "fixture must actually be shrunk");
    }

    static URLClassLoader loader(Path artifact) throws IOException {
        return new URLClassLoader(new URL[]{artifact.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }
}

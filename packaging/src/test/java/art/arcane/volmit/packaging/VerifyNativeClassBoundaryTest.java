package art.arcane.volmit.packaging;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerifyNativeClassBoundaryTest {
    @TempDir
    Path directory;

    @Test
    void rejectsNativeTypeInferredByMethodReference() throws IOException {
        fixture("bind");
        BuildResult result = runner().buildAndFail();
        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyNativeBoundary").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileJava").getOutcome());
        assertEquals(TaskOutcome.FAILED, result.task(":verifyNativeClassBoundary").getOutcome());
        assertTrue(result.getOutput().contains("plugin.Bootstrap -> net.minecraft.server.packs.repository.RepositorySource"));
    }

    @Test
    void typedLibraryHandleDoesNotExposeNativeDescriptor() throws IOException {
        fixture("bindTyped");
        source("src/test/java/plugin/NativeFixture.java", "package plugin; class NativeFixture extends net.minecraft.server.packs.repository.RepositorySource {}");
        assertEquals(TaskOutcome.SUCCESS, runner().build().task(":verifyNativeClassBoundary").getOutcome());
        assertEquals(TaskOutcome.UP_TO_DATE, runner().build().task(":verifyNativeClassBoundary").getOutcome());
    }

    @Test
    void ordinaryStringDataIsNotAnExecutableClassReference() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "plugin/Logger", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "name", "()Ljava/lang/String;", null, null);
        method.visitCode();
        method.visitLdcInsn("net.minecraft.server.MinecraftServer");
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();
        assertTrue(VerifyNativeClassBoundary.nativeReferences(new ClassReader(writer.toByteArray())).isEmpty());
    }

    @Test
    void nativeImplementationClassesOwnTheirServerReferences() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC,
                "art/arcane/volmlib/nativelib/v26_2_R1/Adapter", null,
                "net/minecraft/server/level/ServerLevel", null);
        writer.visitEnd();
        assertTrue(VerifyNativeClassBoundary.nativeReferences(new ClassReader(writer.toByteArray())).isEmpty());
    }

    private void fixture(String binding) throws IOException {
        Files.writeString(directory.resolve("settings.gradle"), "rootProject.name = 'fixture'\n");
        Files.writeString(directory.resolve("build.gradle"), """
                plugins { id 'java'; id 'art.arcane.volmit-packaging' }
                dependencies { implementation files('native-api.jar') }
                """);
        Path nativeSource = source("library-src/net/minecraft/server/packs/repository/RepositorySource.java", """
                package net.minecraft.server.packs.repository;
                public interface RepositorySource {}
                """);
        Path librarySource = source("library-src/art/arcane/volmlib/nativelib/Access.java", """
                package art.arcane.volmlib.nativelib;
                import java.util.function.Supplier;
                import net.minecraft.server.packs.repository.RepositorySource;
                public final class Access {
                    public static final class Source implements RepositorySource {}
                    public static Source source() { return new Source(); }
                    public static void bind(Supplier<RepositorySource> source) {}
                    public static void bindTyped(Supplier<Source> source) {}
                }
                """);
        Path classes = directory.resolve("library-classes");
        Files.createDirectories(classes);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", classes.toString(),
                nativeSource.toString(), librarySource.toString()));
        try (JarOutputStream archive = new JarOutputStream(Files.newOutputStream(directory.resolve("native-api.jar")));
             Stream<Path> files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                archive.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, archive);
                archive.closeEntry();
            }
        }
        source("src/main/java/plugin/Bootstrap.java", """
                package plugin;
                import art.arcane.volmlib.nativelib.Access;
                public final class Bootstrap {
                    public void start() { Access.%s(Access::source); }
                }
                """.formatted(binding));
    }

    private Path source(String name, String text) throws IOException {
        Path file = directory.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
        return file;
    }

    private GradleRunner runner() {
        return GradleRunner.create().withProjectDir(directory.toFile()).withPluginClasspath()
                .withArguments("verifyNativeClassBoundary", "--stacktrace", "--max-workers=1");
    }
}

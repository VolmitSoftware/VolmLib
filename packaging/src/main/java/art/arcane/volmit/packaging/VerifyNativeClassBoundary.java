package art.arcane.volmit.packaging;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@CacheableTask
public abstract class VerifyNativeClassBoundary extends DefaultTask {
    private static final String IMPLEMENTATION_PACKAGE = "art/arcane/volmlib/nativelib/";
    private static final List<String> NATIVE_PACKAGES = List.of(
            "net/minecraft/", "org/bukkit/craftbukkit/", "io/papermc/paper/configuration/",
            "ca/spottedleaf/moonrise/");

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getClasses();

    @OutputFile
    public abstract RegularFileProperty getReport();

    @TaskAction
    public void verify() throws IOException {
        List<File> classes = new ArrayList<>(getClasses().getAsFileTree()
                .matching(pattern -> pattern.include("**/*.class")).getFiles());
        classes.sort(Comparator.comparing(File::getAbsolutePath));
        List<String> violations = new ArrayList<>();
        for (File file : classes) {
            ClassReader reader = new ClassReader(Files.readAllBytes(file.toPath()));
            for (String reference : nativeReferences(reader)) {
                violations.add(reader.getClassName().replace('/', '.') + " -> " + reference.replace('/', '.'));
            }
        }
        if (!violations.isEmpty()) {
            throw new GradleException("Compiled native server references belong in VolmLib implementations:\n"
                    + String.join("\n", violations));
        }
        File report = getReport().get().getAsFile();
        Files.createDirectories(report.toPath().getParent());
        Files.writeString(report.toPath(), "Verified " + classes.size() + " main classes.\n", StandardCharsets.UTF_8);
    }

    static Set<String> nativeReferences(ClassReader reader) {
        Set<String> references = new TreeSet<>();
        if (!reader.getClassName().startsWith(IMPLEMENTATION_PACKAGE)) {
            reader.accept(new ClassRemapper(new ClassWriter(0), new NativeReferences(references)), ClassReader.SKIP_DEBUG);
        }
        return references;
    }

    private static final class NativeReferences extends Remapper {
        private final Set<String> references;

        private NativeReferences(Set<String> references) {
            super(Opcodes.ASM9);
            this.references = references;
        }

        @Override
        public String map(String internalName) {
            if (internalName.equals("org/spigotmc/SpigotWorldConfig")) {
                references.add(internalName);
            } else {
                for (String prefix : NATIVE_PACKAGES) {
                    if (internalName.startsWith(prefix)) {
                        references.add(internalName);
                        break;
                    }
                }
            }
            return internalName;
        }
    }
}

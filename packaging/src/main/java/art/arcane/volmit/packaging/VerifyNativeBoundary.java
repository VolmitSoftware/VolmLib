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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

@CacheableTask
public abstract class VerifyNativeBoundary extends DefaultTask {
    private static final Pattern NATIVE_PACKAGE = Pattern.compile(
            "(?m)^\\s*package\\s+art\\.arcane\\.volmlib\\.nativelib(?:\\.[\\w.]+)?\\s*;");
    private static final List<String> NATIVE_NAMES = List.of(
            "net.minecraft.", "org.bukkit.craftbukkit.", "io.papermc.paper.configuration.",
            "ca.spottedleaf.moonrise.", "org.spigotmc.SpigotWorldConfig");

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSources();

    @OutputFile
    public abstract RegularFileProperty getReport();

    @TaskAction
    public void verify() throws IOException {
        List<File> sources = new ArrayList<>(getSources().getFiles());
        sources.sort(Comparator.comparing(File::getAbsolutePath));
        List<String> violations = new ArrayList<>();
        for (File source : sources) {
            String text = Files.readString(source.toPath(), StandardCharsets.UTF_8);
            if (NATIVE_PACKAGE.matcher(text).find()) {
                continue;
            }
            String[] lines = text.split("\\R", -1);
            for (int index = 0; index < lines.length; index++) {
                for (String name : NATIVE_NAMES) {
                    if (lines[index].contains(name)) {
                        violations.add(source.getName() + ":" + (index + 1) + " " + lines[index].strip());
                    }
                }
            }
        }
        if (!violations.isEmpty()) {
            throw new GradleException("Native server access belongs in VolmLib native implementations:\n"
                    + String.join("\n", violations));
        }
        File report = getReport().get().getAsFile();
        Files.createDirectories(report.toPath().getParent());
        Files.writeString(report.toPath(), "Verified " + sources.size() + " main Java sources.\n",
                StandardCharsets.UTF_8);
    }
}

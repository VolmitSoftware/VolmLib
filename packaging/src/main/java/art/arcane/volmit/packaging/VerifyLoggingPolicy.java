package art.arcane.volmit.packaging;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@CacheableTask
public abstract class VerifyLoggingPolicy extends DefaultTask {
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSources();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getAllowlist();

    @Input
    public abstract ListProperty<String> getForbiddenPatterns();

    @Internal
    public abstract ListProperty<String> getSourceRoots();

    @Internal
    public abstract Property<String> getAllowlistLocation();

    @OutputFile
    public abstract RegularFileProperty getReport();

    @TaskAction
    public void verify() throws IOException {
        List<String> patterns = List.copyOf(getForbiddenPatterns().get());
        List<LoggingPolicyExemption> exemptions = readExemptions();
        List<String> violations = new ArrayList<>();
        List<File> sources = new ArrayList<>(getSources().getFiles());
        sources.sort(Comparator.comparing(File::getAbsolutePath));
        int scanned = 0;
        for (File source : sources) {
            if (!source.getName().endsWith(".java")) {
                continue;
            }
            scanned++;
            String relative = relativize(source.toPath());
            String[] lines = new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8).split("\\R", -1);
            for (int index = 0; index < lines.length; index++) {
                for (String pattern : patterns) {
                    if (!lines[index].contains(pattern)) {
                        continue;
                    }
                    if (!exempt(exemptions, relative, pattern)) {
                        violations.add(relative + ":" + (index + 1) + " [" + pattern + "] " + lines[index].trim());
                    }
                }
            }
        }
        writeReport(scanned, patterns, exemptions);
        if (!violations.isEmpty()) {
            throw new GradleException("Operator logging policy violated - route runtime output through the plugin logger:\n  "
                    + String.join("\n  ", violations)
                    + "\nFix these or (only with strong justification) add them to " + getAllowlistLocation().get());
        }
        List<String> stale = new ArrayList<>();
        for (LoggingPolicyExemption exemption : exemptions) {
            if (exemption.getMatches() == 0) {
                stale.add(exemption.describe());
            }
        }
        if (!stale.isEmpty()) {
            throw new GradleException("Logging policy exemptions only shrink - these entries in "
                    + getAllowlistLocation().get() + " no longer match anything:\n  " + String.join("\n  ", stale));
        }
    }

    private boolean exempt(List<LoggingPolicyExemption> exemptions, String relative, String pattern) {
        boolean matched = false;
        for (LoggingPolicyExemption exemption : exemptions) {
            if (exemption.covers(relative, pattern)) {
                exemption.recordMatch();
                matched = true;
            }
        }
        return matched;
    }

    private List<LoggingPolicyExemption> readExemptions() throws IOException {
        List<LoggingPolicyExemption> exemptions = new ArrayList<>();
        for (File allowlist : getAllowlist().getFiles()) {
            if (!allowlist.isFile()) {
                continue;
            }
            for (String line : Files.readAllLines(allowlist.toPath(), StandardCharsets.UTF_8)) {
                String entry = line.trim();
                if (entry.isEmpty() || entry.startsWith("#")) {
                    continue;
                }
                int separator = indexOfWhitespace(entry);
                if (separator < 0) {
                    exemptions.add(new LoggingPolicyExemption(entry, ""));
                    continue;
                }
                exemptions.add(new LoggingPolicyExemption(entry.substring(0, separator),
                        entry.substring(separator).trim()));
            }
        }
        return exemptions;
    }

    private int indexOfWhitespace(String entry) {
        for (int index = 0; index < entry.length(); index++) {
            if (Character.isWhitespace(entry.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private String relativize(Path source) {
        String normalized = source.toAbsolutePath().normalize().toString().replace('\\', '/');
        for (String root : getSourceRoots().get()) {
            String prefix = root.replace('\\', '/');
            if (!prefix.endsWith("/")) {
                prefix = prefix + "/";
            }
            if (normalized.startsWith(prefix)) {
                return normalized.substring(prefix.length());
            }
        }
        return normalized;
    }

    private void writeReport(int scanned, List<String> patterns, List<LoggingPolicyExemption> exemptions) throws IOException {
        StringBuilder report = new StringBuilder();
        report.append("sources: ").append(scanned).append('\n');
        for (String pattern : patterns) {
            report.append("forbidden: ").append(pattern).append('\n');
        }
        for (LoggingPolicyExemption exemption : exemptions) {
            report.append("exempt: ").append(exemption.describe()).append(" matches=")
                    .append(exemption.getMatches()).append('\n');
        }
        File destination = getReport().get().getAsFile();
        Files.createDirectories(destination.toPath().getParent());
        Files.write(destination.toPath(), report.toString().getBytes(StandardCharsets.UTF_8));
    }
}

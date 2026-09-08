package art.arcane.volmit.packaging;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import proguard.Configuration;
import proguard.ConfigurationParser;
import proguard.ParseException;
import proguard.ProGuard;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class JarShrinker {
    private static final String WARNING_PREFIX = "Warning: ";
    private static final Pattern TRAILING_CLASS = Pattern.compile("(?:class|interface) ([\\w.$]+)$");
    private static final Pattern DEPENDENCY = Pattern.compile("^library class ([\\w.$]+) depends on program class ([\\w.$]+)$");
    private static final Pattern MISPLACED = Pattern.compile("^class \\[.*] unexpectedly contains class \\[([\\w.$]+)]$");

    private JarShrinker() {
    }

    public static ShrinkResult shrink(ShrinkRules.ShrinkRequest request, List<String> dontwarn) throws IOException {
        File artifact = request.artifact();
        long before = artifact.length();
        Path output = request.output().toPath();
        Files.deleteIfExists(output);
        File rules = request.report("rules");
        Files.createDirectories(rules.toPath().getParent());
        Set<String> advice = AdviceClasses.scan(artifact);
        Files.writeString(rules.toPath(), ShrinkRules.generate(request, advice), StandardCharsets.UTF_8);
        List<String> messages = execute(rules);
        List<Warning> warnings = classify(messages, dontwarn);
        writeWarnings(request.report("warnings"), warnings, advice);
        List<String> rejected = new ArrayList<>();
        List<String> tolerated = new ArrayList<>();
        for (Warning warning : warnings) {
            if (warning.pattern() == null) {
                rejected.add(warning.message());
            } else {
                tolerated.add(warning.message() + " (tolerated by " + warning.pattern() + ")");
            }
        }
        if (!rejected.isEmpty()) {
            Files.deleteIfExists(output);
            throw new IOException("ProGuard reported " + rejected.size() + " unresolved references for "
                    + artifact.getName() + "; extend shrink_dontwarn or the library classpath:\n"
                    + String.join("\n", rejected));
        }
        if (!Files.isRegularFile(output)) {
            throw new IOException("ProGuard produced no output for " + artifact.getName());
        }
        AdviceClasses.restore(artifact, output.toFile(), advice);
        appendRestored(request.report("usage"), advice);
        int removed = classCount(artifact) - classCount(output.toFile());
        long after = Files.size(output);
        if (after >= before) {
            Files.deleteIfExists(output);
            return new ShrinkResult(false, "shrunk archive was not smaller", before, before, 0, tolerated, List.of());
        }
        Files.move(output, artifact.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        return new ShrinkResult(true, "shrunk", before, after, removed, tolerated, new ArrayList<>(advice));
    }

    private static List<String> execute(File rules) throws IOException {
        Configuration configuration = new Configuration();
        ConfigurationParser parser = new ConfigurationParser(rules, System.getProperties());
        try {
            parser.parse(configuration);
        } catch (ParseException failure) {
            throw new IOException("Invalid generated shrink rules in " + rules + ": " + failure.getMessage(), failure);
        } finally {
            parser.close();
        }
        LoggerContext context = (LoggerContext) LogManager.getContext(ProGuard.class.getClassLoader(), false);
        LoggerConfig root = context.getConfiguration().getRootLogger();
        Level previous = root.getLevel();
        Capture capture = new Capture();
        capture.start();
        root.addAppender(capture, Level.WARN, null);
        if (previous.isMoreSpecificThan(Level.WARN) && previous != Level.WARN) {
            root.setLevel(Level.WARN);
        }
        context.updateLoggers();
        try {
            new ProGuard(configuration).execute();
        } catch (IOException | RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IOException("ProGuard failed: " + failure.getMessage(), failure);
        } finally {
            root.removeAppender(capture.getName());
            root.setLevel(previous);
            context.updateLoggers();
            capture.stop();
        }
        return capture.lines();
    }

    private static List<Warning> classify(List<String> messages, List<String> dontwarn) {
        List<Pattern> filters = new ArrayList<>(dontwarn.size());
        for (String pattern : dontwarn) {
            filters.add(classPattern(pattern));
        }
        List<Warning> warnings = new ArrayList<>();
        for (String message : messages) {
            if (!message.startsWith(WARNING_PREFIX) || message.startsWith(WARNING_PREFIX + "there were ")) {
                continue;
            }
            String tolerated = null;
            for (String name : classNames(message.substring(WARNING_PREFIX.length()))) {
                for (int index = 0; index < filters.size() && tolerated == null; index++) {
                    if (filters.get(index).matcher(name).matches()) {
                        tolerated = dontwarn.get(index);
                    }
                }
            }
            warnings.add(new Warning(message, tolerated));
        }
        return warnings;
    }

    private static List<String> classNames(String body) {
        Matcher dependency = DEPENDENCY.matcher(body);
        if (dependency.matches()) {
            return List.of(dependency.group(1), dependency.group(2));
        }
        Matcher misplaced = MISPLACED.matcher(body);
        if (misplaced.matches()) {
            return List.of(misplaced.group(1));
        }
        List<String> names = new ArrayList<>(2);
        int separator = body.indexOf(": ");
        if (separator > 0 && body.substring(0, separator).matches("[\\w.$]+")) {
            names.add(body.substring(0, separator));
        }
        Matcher trailing = TRAILING_CLASS.matcher(body);
        if (trailing.find()) {
            names.add(trailing.group(1));
        }
        return names;
    }

    private static Pattern classPattern(String filter) {
        StringBuilder regex = new StringBuilder();
        for (int index = 0; index < filter.length(); index++) {
            char character = filter.charAt(index);
            if (character == '*') {
                boolean doubled = index + 1 < filter.length() && filter.charAt(index + 1) == '*';
                regex.append(doubled ? ".*" : "[^.]*");
                if (doubled) {
                    index++;
                }
            } else if (character == '?') {
                regex.append("[^.]");
            } else {
                regex.append(Pattern.quote(String.valueOf(character)));
            }
        }
        return Pattern.compile(regex.toString());
    }

    private static void writeWarnings(File report, List<Warning> warnings, Set<String> restored) throws IOException {
        List<String> lines = new ArrayList<>(warnings.size() + restored.size());
        for (Warning warning : warnings) {
            lines.add((warning.pattern() == null ? "rejected  " : "tolerated ") + warning.message()
                    + (warning.pattern() == null ? "" : " [" + warning.pattern() + "]"));
        }
        for (String name : restored) {
            lines.add("restored  " + name + " (original class file kept for advice inlining)");
        }
        Files.writeString(report.toPath(), String.join("\n", lines) + (lines.isEmpty() ? "" : "\n"),
                StandardCharsets.UTF_8);
    }

    private static void appendRestored(File usage, Set<String> restored) throws IOException {
        if (restored.isEmpty() || !usage.isFile()) {
            return;
        }
        StringBuilder text = new StringBuilder("\nRestored original class files (advice inlining):\n");
        for (String name : restored) {
            text.append(name).append('\n');
        }
        Files.writeString(usage.toPath(), text, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    public static Set<String> classEntries(File archive) throws IOException {
        Set<String> names = new HashSet<>();
        try (ZipFile zip = new ZipFile(archive)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.endsWith(".class")) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    public static boolean overlaps(File library, Set<String> bundled) throws IOException {
        if (library.isDirectory()) {
            Path root = library.toPath();
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : (Iterable<Path>) files::iterator) {
                    if (bundled.contains(root.relativize(file).toString().replace(File.separatorChar, '/'))) {
                        return true;
                    }
                }
            }
            return false;
        }
        try (ZipFile zip = new ZipFile(library)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.endsWith(".class") && !name.startsWith("META-INF/") && bundled.contains(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int classCount(File archive) throws IOException {
        return classEntries(archive).size();
    }

    public record ShrinkResult(boolean applied, String reason, long beforeBytes, long afterBytes,
                               int removedClasses, List<String> toleratedWarnings, List<String> restoredClasses) {
        public ShrinkResult {
            toleratedWarnings = List.copyOf(toleratedWarnings);
            restoredClasses = List.copyOf(restoredClasses);
        }

        public static ShrinkResult skipped(String reason, long bytes) {
            return new ShrinkResult(false, reason, bytes, bytes, 0, List.of(), List.of());
        }
    }

    private record Warning(String message, String pattern) {
    }

    private static final class Capture extends AbstractAppender {
        private final List<String> lines = Collections.synchronizedList(new ArrayList<>());

        private Capture() {
            super("volmit-packaging-shrink", null, PatternLayout.createDefaultLayout(), false, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            lines.add(event.getMessage().getFormattedMessage());
        }

        private List<String> lines() {
            return List.copyOf(lines);
        }
    }
}

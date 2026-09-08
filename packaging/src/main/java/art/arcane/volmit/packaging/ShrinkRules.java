package art.arcane.volmit.packaging;

import org.objectweb.asm.ClassReader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShrinkRules {
    private static final String SERVICES_PREFIX = "META-INF/services/";
    private static final Set<String> DESCRIPTORS = Set.of("plugin.yml", "paper-plugin.yml");
    private static final int STRING_TAG = 8;
    private static final long TEXT_RESOURCE_LIMIT = 1_048_576L;
    private static final Pattern CLASS_NAME = Pattern.compile("[A-Za-z_$][\\w$]*(?:[./][A-Za-z_$][\\w$]*)+");
    private static final Pattern CLASS_NAME_TOKEN = Pattern.compile("[A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)+");
    private static final Pattern DESCRIPTOR_KEY = Pattern.compile("^(main|bootstrapper|loader):\\s*['\"]?([\\w.$]+)['\"]?\\s*$");
    private static final List<String> OPTIONS = List.of(
            "-dontobfuscate",
            "-dontoptimize",
            "-keepparameternames",
            "-keepdirectories",
            "-dontskipnonpubliclibraryclassmembers",
            "-ignorewarnings",
            "-dontnote **",
            "-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,"
                    + "RuntimeVisibleParameterAnnotations,RuntimeInvisibleParameterAnnotations,"
                    + "RuntimeVisibleTypeAnnotations,RuntimeInvisibleTypeAnnotations,AnnotationDefault,"
                    + "Signature,InnerClasses,EnclosingMethod,MethodParameters,Exceptions,Record,"
                    + "PermittedSubclasses,NestHost,NestMembers,SourceFile,LineNumberTable");
    public static final List<String> SHARED_DONTWARN = List.of(
            "javax.annotation.**",
            "org.jetbrains.annotations.**",
            "org.checkerframework.**",
            "edu.umd.cs.findbugs.annotations.**",
            "com.google.errorprone.annotations.**",
            "kotlin.**",
            "android.**",
            "dalvik.**",
            "org.apache.logging.log4j.**",
            "java.lang.invoke.**",
            "me.clip.placeholderapi.**",
            "lombok.**");
    private static final List<String> SHARED_KEEPS = List.of(
            "-keep @interface * { *; }",
            "-keepclassmembers class * { <init>(...); }",
            "-keep class **.slimjar.** { *; }",
            "-keep class **.bstats.** { *; }",
            "-keep class **.packetevents.** { *; }",
            "-keep class **.bytebuddy.** { *; }",
            "-keep class **.caffeine.** { *; }",
            "-keep class **.director.** { *; }",
            "-keep class **.matter.slices.** { *; }",
            "-keep class **.papi.** { *; }",
            "-keep class **.*API { *; }",
            "-keep class * extends **.VolmitPlaceholderExpansion { *; }",
            "-keep @**.director.annotations.Director class * { *; }",
            "-keepclasseswithmembers class * { @**.director.annotations.Director <methods>; }",
            "-keepclassmembers class * { @**.director.annotations.Director *; @**.director.annotations.Param *; }",
            "-keepclassmembers class * { @**.EventHandler <methods>; @**.Subscribe <methods>; }",
            "-keepclassmembers class * { @**.gson.annotations.SerializedName <fields>; @**.gson.annotations.Expose <fields>; }",
            "-keepclassmembers class * { @**.ConfigDoc *; @**.ConfigDescription *; }",
            "-keepclassmembers class **Config* { *; }",
            "-keepclassmembers class **Settings* { *; }",
            "-keepclassmembers class * extends java.lang.Record { *; }",
            "-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }",
            "-keepclassmembers class * implements java.io.Serializable { static final long serialVersionUID;"
                    + " private static final java.io.ObjectStreamField[] serialPersistentFields;"
                    + " private void writeObject(java.io.ObjectOutputStream);"
                    + " private void readObject(java.io.ObjectInputStream);"
                    + " java.lang.Object writeReplace(); java.lang.Object readResolve(); }",
            "-keepclassmembers class * implements org.bukkit.configuration.serialization.ConfigurationSerializable {"
                    + " public static ** deserialize(java.util.Map); public static ** valueOf(java.util.Map);"
                    + " <init>(java.util.Map); }",
            "-keepclassmembers class * extends org.bukkit.event.Event {"
                    + " public static org.bukkit.event.HandlerList getHandlerList();"
                    + " public org.bukkit.event.HandlerList getHandlers(); }",
            "-keep class **.integration.IntegrationServiceContract { *; }",
            "-keep class * implements **.integration.IntegrationServiceContract { *; }",
            "-keep class **.volmlib.integration.** { *; }",
            "-keep public class art.arcane.**.api.** { public protected *; }",
            "-keep public class com.volmit.**.api.** { public protected *; }");

    private ShrinkRules() {
    }

    public static String generate(ShrinkRequest request) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("-injars " + quote(request.artifact()));
        lines.add("-outjars " + quote(request.output()));
        for (File library : request.libraries()) {
            lines.add("-libraryjars " + quote(library) + "(!META-INF/**)");
        }
        lines.addAll(OPTIONS);
        lines.add("-printseeds " + quote(request.report("seeds")));
        lines.add("-printusage " + quote(request.report("usage")));
        lines.add("-printconfiguration " + quote(request.report("configuration")));
        lines.addAll(SHARED_KEEPS);
        lines.addAll(archiveKeeps(request.artifact()));
        for (String prefix : request.policy().getKeepPrefixes()) {
            lines.add(keepAll(prefix.replace('/', '.') + "**"));
        }
        for (String required : request.policy().getRequiredEntries()) {
            if (required.endsWith(".class")) {
                lines.add(keepAll(className(required)));
            }
        }
        lines.addAll(request.policy().getShrinkKeep());
        for (File include : request.policy().getShrinkRules()) {
            lines.add("-include " + quote(include));
        }
        return String.join("\n", lines) + "\n";
    }

    private static List<String> archiveKeeps(File artifact) throws IOException {
        Set<String> classes = new LinkedHashSet<>();
        Set<String> bundled = new HashSet<>();
        List<String> mentions = new ArrayList<>();
        try (JarFile jar = new JarFile(artifact)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                if (entry.getName().endsWith(".class")) {
                    if (!entry.getName().startsWith("META-INF/")) {
                        bundled.add(className(entry.getName()));
                    }
                    collectStringConstants(jar, entry, mentions);
                } else if (!entry.getName().startsWith("META-INF/MANIFEST") && entry.getSize() <= TEXT_RESOURCE_LIMIT) {
                    collectTokens(read(jar, entry), mentions);
                }
                if (DESCRIPTORS.contains(entry.getName())) {
                    for (String line : read(jar, entry).split("\\R")) {
                        Matcher matcher = DESCRIPTOR_KEY.matcher(line);
                        if (matcher.matches()) {
                            classes.add(matcher.group(2));
                        }
                    }
                } else if (entry.getName().startsWith(SERVICES_PREFIX)) {
                    classes.add(entry.getName().substring(SERVICES_PREFIX.length()));
                    for (String line : read(jar, entry).split("\\R")) {
                        int comment = line.indexOf('#');
                        String provider = (comment < 0 ? line : line.substring(0, comment)).trim();
                        if (!provider.isEmpty()) {
                            classes.add(provider);
                        }
                    }
                }
            }
        }
        for (String mention : mentions) {
            String dotted = mention.replace('/', '.');
            if (bundled.contains(dotted)) {
                classes.add(dotted);
            }
        }
        List<String> rules = new ArrayList<>(classes.size());
        for (String name : classes) {
            rules.add(keepAll(name));
        }
        return rules;
    }

    private static void collectStringConstants(JarFile jar, JarEntry entry, List<String> mentions) throws IOException {
        ClassReader reader;
        try (InputStream input = jar.getInputStream(entry)) {
            reader = new ClassReader(input);
        }
        char[] buffer = new char[reader.getMaxStringLength()];
        for (int index = 1; index < reader.getItemCount(); index++) {
            int offset = reader.getItem(index);
            if (offset != 0 && reader.readByte(offset - 1) == STRING_TAG) {
                Object constant = reader.readConst(index, buffer);
                if (constant instanceof String value && CLASS_NAME.matcher(value).matches()) {
                    mentions.add(value);
                }
            }
        }
    }

    private static void collectTokens(String text, List<String> mentions) {
        Matcher matcher = CLASS_NAME_TOKEN.matcher(text);
        while (matcher.find()) {
            mentions.add(matcher.group());
        }
    }

    private static String read(JarFile jar, JarEntry entry) throws IOException {
        try (InputStream input = jar.getInputStream(entry)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String className(String entry) {
        return entry.substring(0, entry.length() - ".class".length()).replace('/', '.');
    }

    private static String keepAll(String specification) {
        return "-keep class " + specification + " { *; }";
    }

    private static String quote(File file) {
        return "'" + file.getAbsolutePath().replace("'", "\\'") + "'";
    }

    public record ShrinkRequest(File artifact, File output, PackagingArtifact policy, List<File> libraries,
                                File reportDirectory, String artifactName) {
        public File report(String kind) {
            return new File(reportDirectory, artifactName + "-shrink-" + kind + ".txt");
        }
    }
}

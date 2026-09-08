package art.arcane.volmit.packaging;

import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class JarArtifactAudit {
    private JarArtifactAudit() {
    }

    public static void write(File artifact, PackagingArtifact policy, PackagingMode mode, File report, long before,
                             Set<String> removed, JarShrinker.ShrinkResult shrink) throws IOException {
        Audit audit = inspect(artifact, policy, mode);
        Map<String, Object> result = audit.report();
        result.put("beforeBytes", before);
        result.put("savedBytes", before - artifact.length());
        result.put("removedClasses", removed.stream().sorted().toList());
        Map<String, Object> shrinkReport = new LinkedHashMap<>();
        shrinkReport.put("applied", shrink.applied());
        shrinkReport.put("reason", shrink.reason());
        shrinkReport.put("beforeBytes", shrink.beforeBytes());
        shrinkReport.put("afterBytes", shrink.afterBytes());
        shrinkReport.put("savedBytes", shrink.beforeBytes() - shrink.afterBytes());
        shrinkReport.put("removedClasses", shrink.removedClasses());
        shrinkReport.put("restoredClasses", shrink.restoredClasses());
        shrinkReport.put("toleratedWarnings", shrink.toleratedWarnings());
        result.put("shrink", shrinkReport);
        Files.createDirectories(report.toPath().getParent());
        Files.writeString(report.toPath(), new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(result) + "\n",
                StandardCharsets.UTF_8);
        rejectErrors(audit);
    }

    public static List<String> verify(File artifact, PackagingArtifact policy, PackagingMode mode) throws IOException {
        Audit audit = inspect(artifact, policy, mode);
        rejectErrors(audit);
        return audit.warnings();
    }

    private static Audit inspect(File artifact, PackagingArtifact policy, PackagingMode mode) throws IOException {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        long budget = policy.getEffectiveMaximumBytes();
        if (artifact.length() > budget) {
            String overage = "Jar exceeds " + budget + " byte budget: " + artifact.length()
                    + (budget == PackagingArtifact.SPIGOT_CAP_BYTES ? " (Spigot cap)" : "");
            if (mode.development()) {
                warnings.add(overage);
            } else {
                errors.add(overage);
            }
        }
        Set<String> names = new HashSet<>();
        Map<String, Long> packages = new HashMap<>();
        long classes = 0;
        long resources = 0;
        byte[] buffer = new byte[65536];
        try (ZipFile archive = new ZipFile(artifact)) {
            Enumeration<? extends ZipEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!names.add(name)) {
                    errors.add("Duplicate entry: " + name);
                }
                verifyContent(archive, entry, buffer);
                if (entry.isDirectory()) {
                    resources += entry.getCompressedSize();
                    continue;
                }
                String logicalName = name.replaceFirst("^META-INF/versions/[0-9]+/", "");
                for (String prefix : policy.getForbiddenPrefixes()) {
                    if (logicalName.startsWith(prefix)) {
                        errors.add("Forbidden bundled entry: " + name);
                        break;
                    }
                }
                if (name.endsWith(".class")) {
                    classes += entry.getCompressedSize();
                    int separator = logicalName.lastIndexOf('/');
                    String packageName = separator < 0 ? "<default>" : logicalName.substring(0, separator);
                    packages.merge(packageName, entry.getCompressedSize(), Long::sum);
                } else {
                    resources += entry.getCompressedSize();
                }
            }
            for (String required : policy.getRequiredEntries()) {
                ZipEntry entry = archive.getEntry(required);
                if (entry == null || entry.isDirectory()) {
                    errors.add("Missing required entry: " + required);
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("artifact", artifact.getName());
        result.put("mode", mode.label());
        result.put("bytes", artifact.length());
        result.put("maximumBytes", policy.getMaximumBytes());
        result.put("effectiveMaximumBytes", budget);
        result.put("modded", policy.isModded());
        result.put("stripLocalVariables", policy.isStripLocalVariables());
        result.put("releaseCompression", policy.isReleaseCompression());
        result.put("classCompressedBytes", classes);
        result.put("resourceCompressedBytes", resources);
        result.put("zipOverheadBytes", artifact.length() - classes - resources);
        result.put("packages", packages.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> Map.<String, Object>of("package", entry.getKey(), "compressedBytes", entry.getValue())).toList());
        result.put("warnings", warnings);
        result.put("errors", errors);
        return new Audit(result, errors, warnings);
    }

    private static void verifyContent(ZipFile archive, ZipEntry entry, byte[] buffer) throws IOException {
        CRC32 crc = new CRC32();
        long bytes = 0;
        try (InputStream input = archive.getInputStream(entry)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                crc.update(buffer, 0, count);
                bytes += count;
            }
        }
        if (bytes != entry.getSize() || crc.getValue() != entry.getCrc()) {
            throw new IOException("Invalid CRC or size: " + entry.getName());
        }
    }

    private static void rejectErrors(Audit audit) throws IOException {
        if (!audit.errors().isEmpty()) {
            throw new IOException(audit.errors().toString());
        }
    }

    private record Audit(Map<String, Object> report, List<String> errors, List<String> warnings) {
    }
}

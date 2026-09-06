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

    public static void write(File artifact, PackagingArtifact policy, File report, long before, Set<String> removed)
            throws IOException {
        Map<String, Object> result = inspect(artifact, policy);
        result.put("beforeBytes", before);
        result.put("savedBytes", before - artifact.length());
        result.put("removedClasses", removed.stream().sorted().toList());
        Files.createDirectories(report.toPath().getParent());
        Files.writeString(report.toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n",
                StandardCharsets.UTF_8);
        rejectErrors(result);
    }

    public static void verify(File artifact, PackagingArtifact policy) throws IOException {
        rejectErrors(inspect(artifact, policy));
    }

    private static Map<String, Object> inspect(File artifact, PackagingArtifact policy) throws IOException {
        List<String> errors = new ArrayList<>();
        if (artifact.length() > policy.getMaximumBytes()) {
            errors.add("Jar exceeds " + policy.getMaximumBytes() + " byte budget: " + artifact.length());
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
        result.put("artifact", artifact.getAbsolutePath());
        result.put("bytes", artifact.length());
        result.put("maximumBytes", policy.getMaximumBytes());
        result.put("stripLocalVariables", policy.isStripLocalVariables());
        result.put("releaseCompression", policy.isReleaseCompression());
        result.put("classCompressedBytes", classes);
        result.put("resourceCompressedBytes", resources);
        result.put("zipOverheadBytes", artifact.length() - classes - resources);
        result.put("packages", packages.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> Map.<String, Object>of("package", entry.getKey(), "compressedBytes", entry.getValue())).toList());
        result.put("errors", errors);
        return result;
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

    private static void rejectErrors(Map<String, Object> report) throws IOException {
        if (report.get("errors") instanceof List<?> errors && !errors.isEmpty()) {
            throw new IOException(errors.toString());
        }
    }
}

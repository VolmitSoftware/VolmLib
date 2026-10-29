package art.arcane.volmit.packaging;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JarArtifactAuditTest {
    @TempDir
    Path directory;

    @Test
    void everyNonModdedCatalogProfileUsesTheSharedUploadCap() throws IOException {
        assertEquals(7_500_000L, PackagingArtifact.SPIGOT_CAP_BYTES);
        int pluginProfiles = 0;
        try (InputStream input = PackagingArtifact.class.getResourceAsStream("artifact-policies.json")) {
            assertNotNull(input);
            JsonObject catalog = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement element : catalog.getAsJsonArray("profiles")) {
                JsonObject profile = element.getAsJsonObject();
                if (profile.has("modded") && profile.get("modded").getAsBoolean()) {
                    continue;
                }
                String profileName = profile.get("name").getAsString();
                PackagingArtifact policy = policy();
                policy.setPolicyName(profileName);
                assertFalse(policy.isModded(), profileName);
                assertEquals(7_500_000L, profile.get("max_bytes").getAsLong(), profileName);
                assertEquals(Long.valueOf(7_500_000L), policy.getMaximumBytes(), profileName);
                assertEquals(Long.valueOf(7_500_000L), policy.getEffectiveMaximumBytes(), profileName);
                pluginProfiles++;
            }
        }
        assertTrue(pluginProfiles > 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"BileTools", "HiddenOre"})
    void pluginProfilesAcceptLargerDistributionsAndEnforceTheSharedCap(String profileName) throws IOException {
        PackagingArtifact policy = policy();
        policy.setPolicyName(profileName);
        String[] requiredEntries = policy.getRequiredEntries().toArray(String[]::new);
        Path artifact = jar(2_325_000, requiredEntries);
        assertTrue(Files.size(artifact) > 1_000_000L);
        assertTrue(Files.size(artifact) < 7_500_000L);
        assertTrue(JarArtifactAudit.verify(artifact.toFile(), policy, mode(false)).isEmpty());

        Path oversized = jar(7_500_000, requiredEntries);
        IOException failure = assertThrows(IOException.class,
                () -> JarArtifactAudit.verify(oversized.toFile(), policy, mode(false)));
        assertTrue(failure.getMessage().contains("Jar exceeds 7500000 byte budget"), failure.getMessage());

        policy.setIntermediate(true);
        List<String> warnings = JarArtifactAudit.verify(oversized.toFile(), policy, mode(false));
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("the budget applies to the final distribution"), warnings.toString());
    }

    @Test
    void pruningReportSeparatesRemovedClassesAndResources() throws IOException {
        PackagingArtifact policy = policy();
        Path artifact = jar("fabric.mod.json");
        Path reportFile = directory.resolve("reports/distribution.json");
        String removedClass = "art/arcane/volmlib/noise/NativeNoise.class";
        String removedResource = "art/arcane/volmlib/native/linux-x86_64/libvolmlib_noise.so";
        JarArtifactAudit.write(artifact.toFile(), policy, mode(false), reportFile.toFile(), Files.size(artifact),
                Set.of(removedClass), Set.of(removedResource), JarShrinker.ShrinkResult.skipped("modded profile", Files.size(artifact)));
        JsonObject report = JsonParser.parseString(Files.readString(reportFile)).getAsJsonObject();
        assertEquals(List.of(removedClass), report.getAsJsonArray("removedClasses").asList().stream()
                .map(JsonElement::getAsString).toList());
        assertEquals(List.of(removedResource), report.getAsJsonArray("removedResources").asList().stream()
                .map(JsonElement::getAsString).toList());
    }

    @ParameterizedTest
    @CsvSource({
            "Iris Fabric, fabric.mod.json",
            "Iris Forge, META-INF/mods.toml",
            "Iris NeoForge, META-INF/neoforge.mods.toml"
    })
    void irisModdedCatalogProfilesLoadWithoutMaximumBytes(String profileName, String descriptor) throws IOException {
        PackagingArtifact policy = policy();
        policy.setPolicyName(profileName);
        assertTrue(policy.isModded());
        assertNull(policy.getMaximumBytes());
        assertNull(policy.getEffectiveMaximumBytes());
        assertTrue(policy.getRequiredEntries().contains(descriptor));
        assertTrue(policy.getForbiddenPrefixes().contains("org/bukkit/"));
        try (InputStream input = PackagingArtifact.class.getResourceAsStream("artifact-policies.json")) {
            assertNotNull(input);
            JsonObject catalog = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject selected = null;
            for (JsonElement element : catalog.getAsJsonArray("profiles")) {
                JsonObject profile = element.getAsJsonObject();
                if (profile.get("name").getAsString().equals(profileName)) {
                    selected = profile;
                    break;
                }
            }
            assertNotNull(selected);
            assertFalse(selected.has("max_bytes"), selected.toString());
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false,false", "true,false,false", "false,true,false", "false,false,true"})
    void moddedAuditReportsNoBudgetEvenForIntermediateAndPackedArtifacts(boolean development,
                                                                       boolean intermediate,
                                                                       boolean packed) throws IOException {
        PackagingArtifact policy = policy();
        policy.setIntermediate(intermediate);
        policy.setPacked(packed);
        PackagingMode mode = mode(development);
        Path artifact = jar("fabric.mod.json");
        assertTrue(Files.size(artifact) > 1);
        assertTrue(JarArtifactAudit.verify(artifact.toFile(), policy, mode).isEmpty());
        Path reportFile = directory.resolve("reports/distribution.json");
        JarArtifactAudit.write(artifact.toFile(), policy, mode, reportFile.toFile(), Files.size(artifact),
                Set.of(), Set.of(), JarShrinker.ShrinkResult.skipped("modded profile", Files.size(artifact)));
        JsonObject report = JsonParser.parseString(Files.readString(reportFile)).getAsJsonObject();
        assertTrue(report.get("maximumBytes").isJsonNull(), report.toString());
        assertTrue(report.get("effectiveMaximumBytes").isJsonNull(), report.toString());
        assertEquals("none", report.get("budgetAppliesTo").getAsString(), report.toString());
        assertTrue(report.getAsJsonArray("warnings").isEmpty(), report.toString());
        assertTrue(report.getAsJsonArray("errors").isEmpty(), report.toString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void moddedJarStillRequiresItsDescriptor(boolean development) throws IOException {
        Path artifact = jar("assets/example.txt");
        IOException failure = assertThrows(IOException.class,
                () -> JarArtifactAudit.verify(artifact.toFile(), policy(), mode(development)));
        assertTrue(failure.getMessage().contains("Missing required entry: fabric.mod.json"), failure.getMessage());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void moddedJarStillRejectsForbiddenMultiReleaseEntries(boolean development) throws IOException {
        Path artifact = jar("fabric.mod.json", "META-INF/versions/17/org/bukkit/Bad.class");
        IOException failure = assertThrows(IOException.class,
                () -> JarArtifactAudit.verify(artifact.toFile(), policy(), mode(development)));
        assertTrue(failure.getMessage().contains("Forbidden bundled entry: META-INF/versions/17/org/bukkit/Bad.class"),
                failure.getMessage());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void moddedJarStillRejectsDuplicateEntries(boolean development) throws IOException {
        Path artifact = jar("fabric.mod.json", "fabric.mod.json");
        IOException failure = assertThrows(IOException.class,
                () -> JarArtifactAudit.verify(artifact.toFile(), policy(), mode(development)));
        assertTrue(failure.getMessage().contains("Duplicate entry: fabric.mod.json"), failure.getMessage());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void moddedJarStillRejectsCorruptContent(boolean development) throws IOException {
        Path artifact = jar("fabric.mod.json");
        byte[] bytes = Files.readAllBytes(artifact);
        int payload = new String(bytes, StandardCharsets.ISO_8859_1).indexOf("payload:fabric.mod.json");
        assertTrue(payload >= 0);
        bytes[payload] ^= 1;
        Files.write(artifact, bytes);
        IOException failure = assertThrows(IOException.class,
                () -> JarArtifactAudit.verify(artifact.toFile(), policy(), mode(development)));
        assertTrue(failure.getMessage().contains("Invalid CRC or size: fabric.mod.json"), failure.getMessage());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void moddedJarStillRejectsInvalidArchives(boolean development) throws IOException {
        Path artifact = directory.resolve("invalid.jar");
        Files.writeString(artifact, "invalid archive");
        assertThrows(IOException.class,
                () -> JarArtifactAudit.verify(artifact.toFile(), policy(), mode(development)));
    }

    private PackagingArtifact policy() {
        PackagingArtifact policy = ProjectBuilder.builder().withProjectDir(directory.toFile()).build()
                .getObjects().newInstance(PackagingArtifact.class, "distribution");
        policy.setMaximumBytes(1);
        policy.setModded(true);
        policy.setRequiredEntries(List.of("fabric.mod.json"));
        policy.setForbiddenPrefixes(List.of("org/bukkit/"));
        return policy;
    }

    private PackagingMode mode(boolean development) {
        return new PackagingMode(development, development ? "volmitPackaging=dev" : "release");
    }

    private Path jar(String... names) throws IOException {
        return jar(0, names);
    }

    private Path jar(int paddingBytes, String... names) throws IOException {
        Path artifact = directory.resolve("example.jar");
        try (ZipArchiveOutputStream archive = new ZipArchiveOutputStream(artifact)) {
            for (String name : names) {
                writeEntry(archive, name, ("payload:" + name).getBytes(StandardCharsets.UTF_8));
            }
            if (paddingBytes > 0) {
                writeEntry(archive, "assets/padding.bin", new byte[paddingBytes]);
            }
        }
        return artifact;
    }

    private void writeEntry(ZipArchiveOutputStream archive, String name, byte[] payload) throws IOException {
        CRC32 crc = new CRC32();
        crc.update(payload);
        ZipArchiveEntry entry = new ZipArchiveEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(payload.length);
        entry.setCrc(crc.getValue());
        archive.putArchiveEntry(entry);
        archive.write(payload);
        archive.closeArchiveEntry();
    }
}

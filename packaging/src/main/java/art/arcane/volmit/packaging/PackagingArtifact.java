package art.arcane.volmit.packaging;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.gradle.api.GradleException;
import org.gradle.api.Named;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PackagingArtifact implements Named {
    public static final long SPIGOT_CAP_BYTES = 7_600_000L;

    private final String name;
    private final ConfigurableFileCollection shrinkLibraries;
    private String taskName = "shadowJar";
    private long maximumBytes;
    private boolean modded;
    private boolean shrink = true;
    private boolean stripDirectories;
    private boolean stripLocalVariables;
    private boolean releaseCompression;
    private List<String> prunePrefixes = new ArrayList<>();
    private List<String> keepPrefixes = new ArrayList<>();
    private List<String> requiredEntries = new ArrayList<>();
    private List<String> forbiddenPrefixes = new ArrayList<>();
    private List<String> shrinkKeep = new ArrayList<>();
    private List<String> shrinkDontwarn = new ArrayList<>();
    private List<File> shrinkRules = new ArrayList<>();
    private Map<String, String> shrinkRelocations = new LinkedHashMap<>();

    @Inject
    public PackagingArtifact(String name, ObjectFactory objects) {
        this.name = name;
        this.shrinkLibraries = objects.fileCollection();
    }

    @Override
    public String getName() {
        return name;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public void setPolicyName(String policyName) {
        try (InputStream input = PackagingArtifact.class.getResourceAsStream("artifact-policies.json")) {
            if (input == null) {
                throw new GradleException("Packaging policies are missing");
            }
            JsonObject policies = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement candidate : policies.getAsJsonArray("profiles")) {
                JsonObject profile = candidate.getAsJsonObject();
                if (profile.get("name").getAsString().equals(policyName)) {
                    maximumBytes = profile.get("max_bytes").getAsLong();
                    modded = profile.has("modded") && profile.get("modded").getAsBoolean();
                    requiredEntries = readList(profile, "required_entries");
                    forbiddenPrefixes = readList(profile, "forbidden_prefixes");
                    shrinkKeep = readList(profile, "shrink_keep");
                    shrinkDontwarn = readList(profile, "shrink_dontwarn");
                    return;
                }
            }
        } catch (IOException exception) {
            throw new GradleException("Cannot read packaging policy " + policyName, exception);
        }
        throw new GradleException("Unknown packaging policy " + policyName);
    }

    public boolean isModded() {
        return modded;
    }

    public void setModded(boolean modded) {
        this.modded = modded;
    }

    public boolean isShrink() {
        return shrink;
    }

    public void setShrink(boolean shrink) {
        this.shrink = shrink;
    }

    public boolean isStripDirectories() {
        return stripDirectories;
    }

    public void setStripDirectories(boolean stripDirectories) {
        this.stripDirectories = stripDirectories;
    }

    public boolean isStripLocalVariables() {
        return stripLocalVariables;
    }

    public void setStripLocalVariables(boolean stripLocalVariables) {
        this.stripLocalVariables = stripLocalVariables;
    }

    public boolean isReleaseCompression() {
        return releaseCompression;
    }

    public void setReleaseCompression(boolean releaseCompression) {
        this.releaseCompression = releaseCompression;
    }

    public long getMaximumBytes() {
        return maximumBytes;
    }

    public void setMaximumBytes(long maximumBytes) {
        this.maximumBytes = maximumBytes;
    }

    public long getEffectiveMaximumBytes() {
        return modded ? maximumBytes : Math.min(maximumBytes, SPIGOT_CAP_BYTES);
    }

    public List<String> getPrunePrefixes() {
        return prunePrefixes;
    }

    public void setPrunePrefixes(List<String> prunePrefixes) {
        this.prunePrefixes = List.copyOf(prunePrefixes);
    }

    public List<String> getKeepPrefixes() {
        return keepPrefixes;
    }

    public void setKeepPrefixes(List<String> keepPrefixes) {
        this.keepPrefixes = List.copyOf(keepPrefixes);
    }

    public List<String> getRequiredEntries() {
        return requiredEntries;
    }

    public void setRequiredEntries(List<String> requiredEntries) {
        this.requiredEntries = List.copyOf(requiredEntries);
    }

    public List<String> getForbiddenPrefixes() {
        return forbiddenPrefixes;
    }

    public void setForbiddenPrefixes(List<String> forbiddenPrefixes) {
        this.forbiddenPrefixes = List.copyOf(forbiddenPrefixes);
    }

    public List<String> getShrinkKeep() {
        return shrinkKeep;
    }

    public void setShrinkKeep(List<String> shrinkKeep) {
        this.shrinkKeep = List.copyOf(shrinkKeep);
    }

    public List<String> getShrinkDontwarn() {
        return shrinkDontwarn;
    }

    public void setShrinkDontwarn(List<String> shrinkDontwarn) {
        this.shrinkDontwarn = List.copyOf(shrinkDontwarn);
    }

    public List<File> getShrinkRules() {
        return shrinkRules;
    }

    public void setShrinkRules(List<File> shrinkRules) {
        this.shrinkRules = List.copyOf(shrinkRules);
    }

    public Map<String, String> getShrinkRelocations() {
        return shrinkRelocations;
    }

    public void setShrinkRelocations(Map<String, ?> shrinkRelocations) {
        Map<String, String> relocations = new LinkedHashMap<>(shrinkRelocations.size());
        for (Map.Entry<String, ?> relocation : shrinkRelocations.entrySet()) {
            relocations.put(relocation.getKey(), String.valueOf(relocation.getValue()));
        }
        this.shrinkRelocations = relocations;
    }

    public ConfigurableFileCollection getShrinkLibraries() {
        return shrinkLibraries;
    }

    private List<String> readList(JsonObject profile, String key) {
        List<String> entries = new ArrayList<>();
        if (!profile.has(key)) {
            return entries;
        }
        for (JsonElement entry : profile.getAsJsonArray(key)) {
            entries.add(entry.getAsString());
        }
        return List.copyOf(entries);
    }
}

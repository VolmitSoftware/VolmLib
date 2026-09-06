package art.arcane.volmit.packaging;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.gradle.api.GradleException;
import org.gradle.api.Named;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class PackagingArtifact implements Named {
    private final String name;
    private String taskName = "shadowJar";
    private long maximumBytes;
    private boolean stripDirectories;
    private boolean stripLocalVariables;
    private boolean releaseCompression;
    private List<String> prunePrefixes = new ArrayList<>();
    private List<String> keepPrefixes = new ArrayList<>();
    private List<String> requiredEntries = new ArrayList<>();
    private List<String> forbiddenPrefixes = new ArrayList<>();

    public PackagingArtifact(String name) {
        this.name = name;
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
                    requiredEntries = readList(profile, "required_entries");
                    forbiddenPrefixes = readList(profile, "forbidden_prefixes");
                    return;
                }
            }
        } catch (IOException exception) {
            throw new GradleException("Cannot read packaging policy " + policyName, exception);
        }
        throw new GradleException("Unknown packaging policy " + policyName);
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

    private List<String> readList(JsonObject profile, String key) {
        List<String> entries = new ArrayList<>();
        for (JsonElement entry : profile.getAsJsonArray(key)) {
            entries.add(entry.getAsString());
        }
        return List.copyOf(entries);
    }
}

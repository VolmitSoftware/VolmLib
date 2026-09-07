package art.arcane.volmit.packaging;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LoggingPolicySpec {
    public static final List<String> FLEET_FORBIDDEN_PATTERNS = List.of(
            "System.out",
            "System.err",
            "printStackTrace(",
            "Throwable::printStackTrace",
            "Bukkit.getLogger(",
            "getServer().getLogger(",
            "getConsoleSender().sendMessage(");

    private List<String> forbiddenPatterns = FLEET_FORBIDDEN_PATTERNS;
    private List<File> sourceDirectories = Collections.emptyList();
    private File allowlistFile;

    public LoggingPolicySpec(File allowlistFile) {
        this.allowlistFile = allowlistFile;
    }

    public List<String> getForbiddenPatterns() {
        return forbiddenPatterns;
    }

    public void setForbiddenPatterns(List<String> forbiddenPatterns) {
        this.forbiddenPatterns = List.copyOf(forbiddenPatterns);
    }

    public void forbid(String... patterns) {
        List<String> merged = new ArrayList<>(forbiddenPatterns);
        Collections.addAll(merged, patterns);
        this.forbiddenPatterns = List.copyOf(merged);
    }

    public List<File> getSourceDirectories() {
        return sourceDirectories;
    }

    public void setSourceDirectories(List<File> sourceDirectories) {
        this.sourceDirectories = List.copyOf(sourceDirectories);
    }

    public File getAllowlistFile() {
        return allowlistFile;
    }

    public void setAllowlistFile(File allowlistFile) {
        this.allowlistFile = allowlistFile;
    }
}

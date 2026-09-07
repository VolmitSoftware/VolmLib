package art.arcane.volmit.packaging;

public class LoggingPolicyExemption {
    private final String path;
    private final String pattern;
    private int matches;

    public LoggingPolicyExemption(String path, String pattern) {
        this.path = path;
        this.pattern = pattern;
    }

    public boolean covers(String relativePath, String candidate) {
        if (!pattern.isEmpty() && !pattern.equals(candidate)) {
            return false;
        }
        if (path.endsWith("/")) {
            return relativePath.startsWith(path);
        }
        return relativePath.equals(path);
    }

    public void recordMatch() {
        matches++;
    }

    public int getMatches() {
        return matches;
    }

    public String describe() {
        return pattern.isEmpty() ? path : path + " " + pattern;
    }
}

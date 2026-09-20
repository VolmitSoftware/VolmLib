package art.arcane.volmlib.nativelib;

import java.util.Optional;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum NativeVersion {
    V1_21_R7("v1_21_R7", List.of("1.21.11")),
    V26_2_R1("v26_2_R1", List.of("26.1.2", "26.2")),
    V26_3_R1("v26_3_R1", List.of("26.3"));

    private static final Pattern RELEASE = Pattern.compile(
            "([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)(?:\\.build\\.[0-9]+)?(?:-[0-9A-Za-z.+-]+)?");
    private final String packageName;
    private final List<String> minecraftVersions;

    NativeVersion(String packageName, List<String> minecraftVersions) {
        this.packageName = packageName;
        this.minecraftVersions = minecraftVersions;
    }

    public String packageName() {
        return packageName;
    }

    public List<String> minecraftVersions() {
        return minecraftVersions;
    }

    public static Optional<NativeVersion> resolve(String minecraftVersion) {
        if (minecraftVersion == null) {
            return Optional.empty();
        }
        Matcher release = RELEASE.matcher(minecraftVersion);
        if (!release.matches()) {
            return Optional.empty();
        }
        String versionName = release.group(1);
        String withoutZeroPatch = versionName.endsWith(".0")
                ? versionName.substring(0, versionName.length() - 2) : versionName;
        for (NativeVersion version : values()) {
            if (version.minecraftVersions.contains(versionName)
                    || version.minecraftVersions.contains(withoutZeroPatch)) {
                return Optional.of(version);
            }
        }
        return Optional.empty();
    }
}

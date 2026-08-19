package art.arcane.volmlib.util.plugin;

import org.bukkit.Bukkit;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class SplashScreenSupport {
    private SplashScreenSupport() {
    }

    public static int javaMajorVersion() {
        String version = System.getProperty("java.version", "0");
        if (version.startsWith("1.")) {
            version = version.substring(2, 3);
        } else {
            int dot = version.indexOf('.');
            if (dot != -1) {
                version = version.substring(0, dot);
            }
        }
        int end = 0;
        while (end < version.length() && Character.isDigit(version.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return 0;
        }
        return Integer.parseInt(version.substring(0, end));
    }

    public static String serverVersionWithoutMcSuffix() {
        String version = Bukkit.getVersion();
        int mcMarkerIndex = version.indexOf(" (MC:");
        if (mcMarkerIndex != -1) {
            version = version.substring(0, mcMarkerIndex);
        }
        return version;
    }

    public static String startupDate() {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    public static String releaseTrain(String version) {
        String value = version;
        int suffixIndex = value.indexOf('-');
        if (suffixIndex >= 0) {
            value = value.substring(0, suffixIndex);
        }
        String[] split = value.split("\\.");
        if (split.length >= 2) {
            return split[0] + "." + split[1];
        }
        return value;
    }
}

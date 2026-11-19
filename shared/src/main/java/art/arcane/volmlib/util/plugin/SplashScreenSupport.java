package art.arcane.volmlib.util.plugin;

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

    public static String serverVersionWithoutMcSuffix(String version) {
        int mcMarkerIndex = version.indexOf(" (MC:");
        if (mcMarkerIndex != -1) {
            version = version.substring(0, mcMarkerIndex);
        }
        return version;
    }

    public static String startupDate() {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /**
     * Paints one row of block-letter art: {@code fill} before each run of {@code █}, {@code edge} before each run of
     * other glyphs. Spaces keep the current colour. The colours are inserted as given, so any colour syntax works.
     */
    public static String colorizeArt(String row, String fill, String edge) {
        StringBuilder out = new StringBuilder(row.length() * 2);
        boolean inFill = false;
        boolean inEdge = false;
        for (int index = 0; index < row.length(); index++) {
            char glyph = row.charAt(index);
            if (glyph == '█') {
                if (!inFill) {
                    out.append(fill);
                    inFill = true;
                    inEdge = false;
                }
            } else if (glyph != ' ' && !inEdge) {
                out.append(edge);
                inEdge = true;
                inFill = false;
            }
            out.append(glyph);
        }
        return out.toString();
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

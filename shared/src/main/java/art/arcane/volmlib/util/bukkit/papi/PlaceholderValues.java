package art.arcane.volmlib.util.bukkit.papi;

public final class PlaceholderValues {
    public static final String UNAVAILABLE = "---";
    public static final String TRUE = "true";
    public static final String FALSE = "false";

    private static final int SMALL_COUNT_LIMIT = 1024;
    private static final String[] SMALL_COUNTS = smallCounts();
    private static final double LARGE_MAGNITUDE = 9.0E16D;
    private static final char SECTION = '\u00A7';

    private PlaceholderValues() {
    }

    public static String bool(boolean value) {
        return value ? TRUE : FALSE;
    }

    public static String count(long value) {
        if (value >= 0L && value < SMALL_COUNT_LIMIT) {
            return SMALL_COUNTS[(int) value];
        }

        return Long.toString(value);
    }

    public static String num(double value) {
        if (!Double.isFinite(value)) {
            return UNAVAILABLE;
        }

        boolean negative = value < 0.0D;
        double magnitude = negative ? -value : value;

        if (magnitude >= LARGE_MAGNITUDE) {
            return negative ? "-" + (long) magnitude + ".00" : (long) magnitude + ".00";
        }

        long scaled = Math.round(magnitude * 100.0D);
        long whole = scaled / 100L;
        int fraction = (int) (scaled % 100L);
        StringBuilder builder = new StringBuilder(24);

        if (negative && scaled != 0L) {
            builder.append('-');
        }

        builder.append(whole).append('.');

        if (fraction < 10) {
            builder.append('0');
        }

        return builder.append(fraction).toString();
    }

    public static String percent(double fraction) {
        return num(fraction * 100.0D);
    }

    public static String text(String value) {
        if (value == null || value.isEmpty()) {
            return UNAVAILABLE;
        }

        int length = value.length();
        StringBuilder builder = null;

        for (int index = 0; index < length; index++) {
            char character = value.charAt(index);
            boolean legacy = character == SECTION;

            if (character != '%' && !legacy) {
                if (builder != null) {
                    builder.append(character);
                }

                continue;
            }

            if (builder == null) {
                builder = new StringBuilder(length).append(value, 0, index);
            }

            if (legacy && index + 1 < length) {
                index++;
            }
        }

        if (builder == null) {
            return value;
        }

        return builder.isEmpty() ? UNAVAILABLE : builder.toString();
    }

    private static String[] smallCounts() {
        String[] counts = new String[SMALL_COUNT_LIMIT];

        for (int index = 0; index < SMALL_COUNT_LIMIT; index++) {
            counts[index] = Integer.toString(index);
        }

        return counts;
    }
}

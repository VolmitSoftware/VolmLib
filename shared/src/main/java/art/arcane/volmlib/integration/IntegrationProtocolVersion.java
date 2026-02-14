package art.arcane.volmlib.integration;

import java.util.Locale;

public record IntegrationProtocolVersion(int major, int minor) implements Comparable<IntegrationProtocolVersion> {
    public IntegrationProtocolVersion {
        if (major < 0 || minor < 0) {
            throw new IllegalArgumentException("Protocol version values must be >= 0");
        }
    }

    public static IntegrationProtocolVersion parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Protocol version cannot be blank");
        }

        String[] parts = value.trim().split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid protocol version: " + value);
        }

        return new IntegrationProtocolVersion(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1])
        );
    }

    public String asText() {
        return major + "." + minor;
    }

    @Override
    public int compareTo(IntegrationProtocolVersion other) {
        if (other == null) {
            return 1;
        }

        int majorCmp = Integer.compare(major, other.major);
        if (majorCmp != 0) {
            return majorCmp;
        }

        return Integer.compare(minor, other.minor);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%d.%d", major, minor);
    }
}

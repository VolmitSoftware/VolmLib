package art.arcane.volmlib.util.network;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Objects;

public final class IpRange {
    private final byte[] network;
    private final int prefixLength;

    private IpRange(byte[] network, int prefixLength) {
        this.network = network;
        this.prefixLength = prefixLength;
    }

    public static IpRange parse(String value) {
        String text = Objects.requireNonNull(value, "value").trim();
        int slash = text.indexOf('/');
        InetAddress address = parseLiteral(slash < 0 ? text : text.substring(0, slash));
        int maximum = address.getAddress().length * 8;
        int prefix = maximum;
        if (slash >= 0) {
            try {
                prefix = Integer.parseInt(text.substring(slash + 1).trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid prefix length in " + text);
            }
            if (prefix < 0 || prefix > maximum) {
                throw new IllegalArgumentException("Prefix length must be between 0 and " + maximum + " in " + text);
            }
        }
        return new IpRange(mask(address.getAddress(), prefix), prefix);
    }

    public static InetAddress parseLiteral(String value) {
        String text = Objects.requireNonNull(value, "value").trim();
        if (text.startsWith("[") && text.endsWith("]")) {
            text = text.substring(1, text.length() - 1);
        }
        if (text.indexOf(':') >= 0) {
            return parseIpv6(text);
        }
        return parseIpv4(text);
    }

    public boolean contains(InetAddress address) {
        if (address == null) {
            return false;
        }
        byte[] candidate = address.getAddress();
        return candidate.length == network.length && Arrays.equals(network, mask(candidate, prefixLength));
    }

    public int prefixLength() {
        return prefixLength;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof IpRange range && prefixLength == range.prefixLength && Arrays.equals(network, range.network);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(network) + prefixLength;
    }

    @Override
    public String toString() {
        try {
            return InetAddress.getByAddress(network).getHostAddress() + "/" + prefixLength;
        } catch (UnknownHostException exception) {
            throw new IllegalStateException("Stored network address has an invalid length", exception);
        }
    }

    private static InetAddress parseIpv4(String text) {
        String[] parts = text.split("\\.", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException("Not an IP address: " + text);
        }
        byte[] bytes = new byte[4];
        for (int index = 0; index < 4; index++) {
            String part = parts[index];
            if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException("Not an IP address: " + text);
            }
            int octet = Integer.parseInt(part);
            if (octet > 255) {
                throw new IllegalArgumentException("Not an IP address: " + text);
            }
            bytes[index] = (byte) octet;
        }
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("Not an IP address: " + text, exception);
        }
    }

    private static InetAddress parseIpv6(String text) {
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (Character.digit(character, 16) < 0 && character != ':' && character != '.') {
                throw new IllegalArgumentException("Not an IP address: " + text);
            }
        }
        try {
            return InetAddress.getByName(text);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("Not an IP address: " + text, exception);
        }
    }

    private static byte[] mask(byte[] address, int prefix) {
        byte[] masked = address.clone();
        for (int index = 0; index < masked.length; index++) {
            int remaining = prefix - index * 8;
            if (remaining >= 8) {
                continue;
            }
            masked[index] = remaining <= 0 ? 0 : (byte) (masked[index] & (0xFF << (8 - remaining)));
        }
        return masked;
    }
}

package art.arcane.volmlib.util.editor;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.regex.Pattern;

/** Client addresses as the link counts them: IP literals parsed without DNS, and IPv6 grouped by its {@code /64}. */
public final class EditorAddresses {
    private static final Pattern IPV4 = Pattern.compile("(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}");
    private static final Pattern IPV6 = Pattern.compile("[0-9A-Fa-f:][0-9A-Fa-f:.]*(%[0-9A-Za-z_.-]+)?");

    private EditorAddresses() {}

    /** The address an IP literal names, never looked up in DNS; brackets around IPv6 are allowed. */
    public static Optional<InetAddress> literal(String text) {
        if (text == null) return Optional.empty();
        String bare = text.strip();
        if (bare.startsWith("[") && bare.endsWith("]")) bare = bare.substring(1, bare.length() - 1);
        if (!IPV4.matcher(bare).matches() && !(bare.contains(":") && IPV6.matcher(bare).matches())) return Optional.empty();
        // Only literals reach getByName, which then parses them instead of asking DNS.
        try { return Optional.of(InetAddress.getByName(bare)); }
        catch (UnknownHostException | SecurityException invalid) { return Optional.empty(); }
    }

    /** An IPv4 address itself, an IPv6 address by its {@code /64}, since one host can usually use any address inside it. */
    public static String key(InetAddress address) {
        if (!(address instanceof Inet6Address)) return address.getHostAddress();
        byte[] bytes = address.getAddress();
        StringBuilder prefix = new StringBuilder();
        for (int index = 0; index < 8; index += 2) prefix.append(Integer.toHexString(((bytes[index] & 0xff) << 8) | (bytes[index + 1] & 0xff))).append(':');
        return prefix.append(":/64").toString();
    }

    /** {@link #key(InetAddress)} of an IP literal; anything else, such as {@code relay} or {@code unknown}, stays as it is. */
    public static String key(String address) { return literal(address).map(EditorAddresses::key).orElse(address); }
}

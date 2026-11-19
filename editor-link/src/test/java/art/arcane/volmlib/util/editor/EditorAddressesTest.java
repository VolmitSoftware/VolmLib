package art.arcane.volmlib.util.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EditorAddressesTest {
    private static InetAddress address(String literal) { return EditorAddresses.literal(literal).orElseThrow(); }

    @Test
    void literal_ipv4_parses() {
        InetAddress parsed = address("203.0.113.9");
        assertTrue(parsed instanceof Inet4Address);
        assertEquals("203.0.113.9", parsed.getHostAddress());
        assertEquals("203.0.113.9", address(" 203.0.113.9 ").getHostAddress());
        assertTrue(address("127.0.0.1").isLoopbackAddress());
    }

    @Test
    void literal_ipv6WithAndWithoutBrackets_parses() {
        assertTrue(address("2001:db8::1") instanceof Inet6Address);
        assertTrue(address("[::1]").isLoopbackAddress());
        assertTrue(address("::1").isLoopbackAddress());
        assertEquals(address("2001:db8:0:0:0:0:0:1"), address("[2001:db8::1]"));
    }

    @Test
    void literal_hostNamesAndJunk_refused() {
        for (String text : Arrays.asList("localhost", "example.com", "[localhost]", "relay", "unknown", "", " ", "1.2.3", "256.1.1.1", "1.2.3.4.5",
                "2001:db8::g", ":::", "1.2.3.4:80", null))
            assertEquals(Optional.empty(), EditorAddresses.literal(text), String.valueOf(text));
    }

    @Test
    void literal_anyText_neverReachesTheResolver() throws Exception {
        String canary = "canary-" + UUID.randomUUID() + ".invalid";
        assertThrows(UnknownHostException.class, () -> InetAddress.getByName(canary));
        assertTrue(RecordingResolver.LOOKUPS.contains(canary), "the recording resolver is installed");
        int before = RecordingResolver.LOOKUPS.size();
        for (String text : List.of("localhost", "example.com", "203.0.113.9", "[::1]", "2001:db8::1", ".1:2", "..:", "[.:1]")) EditorAddresses.literal(text);
        assertEquals(List.of(), List.copyOf(RecordingResolver.LOOKUPS.subList(before, RecordingResolver.LOOKUPS.size())), "looked up by name");
    }

    @Test
    void key_ipv6_groupedBySlash64() {
        String key = EditorAddresses.key(address("2001:db8:1:2::1"));
        assertEquals("2001:db8:1:2::/64", key);
        assertEquals(key, EditorAddresses.key(address("2001:db8:1:2:ffff:ffff:ffff:9")));
        assertEquals(key, EditorAddresses.key("[2001:db8:1:2::abcd]"));
        assertNotEquals(key, EditorAddresses.key(address("2001:db8:1:3::1")));
    }

    @Test
    void key_ipv4AndNonLiterals_keptAsTheyAre() {
        assertEquals("203.0.113.9", EditorAddresses.key(address("203.0.113.9")));
        assertEquals("203.0.113.9", EditorAddresses.key("203.0.113.9"));
        assertEquals("relay", EditorAddresses.key("relay"));
        assertEquals("unknown", EditorAddresses.key("unknown"));
        assertEquals("example.com", EditorAddresses.key("example.com"));
    }
}

package art.arcane.volmlib.util.network;

import org.junit.Test;

import java.net.InetAddress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IpRangeTest {
    @Test
    public void singleAddressMatchesOnlyItself() {
        IpRange range = IpRange.parse(" 192.168.1.5 ");

        assertTrue(range.contains(IpRange.parseLiteral("192.168.1.5")));
        assertFalse(range.contains(IpRange.parseLiteral("192.168.1.6")));
        assertEquals("192.168.1.5/32", range.toString());
    }

    @Test
    public void cidrMatchesItsNetworkAndNormalizesHostBits() {
        IpRange range = IpRange.parse("10.20.30.40/12");

        assertEquals("10.16.0.0/12", range.toString());
        assertTrue(range.contains(IpRange.parseLiteral("10.31.255.255")));
        assertFalse(range.contains(IpRange.parseLiteral("10.32.0.0")));
        assertTrue(IpRange.parse("0.0.0.0/0").contains(IpRange.parseLiteral("203.0.113.9")));
    }

    @Test
    public void ipv6RangesDoNotMatchIpv4Addresses() throws Exception {
        IpRange range = IpRange.parse("[fd00::]/8");

        assertTrue(range.contains(IpRange.parseLiteral("fd12:3456::1")));
        assertFalse(range.contains(IpRange.parseLiteral("fe80::1")));
        assertFalse(range.contains(IpRange.parseLiteral("10.0.0.1")));
        assertTrue(IpRange.parse("::1").contains(IpRange.parseLiteral("[::1]")));
        assertTrue(IpRange.parse("127.0.0.0/8").contains(InetAddress.getByAddress(new byte[]{127, 0, 0, 1})));
    }

    @Test
    public void rejectsHostnamesAndMalformedRanges() {
        assertThrows(IllegalArgumentException.class, () -> IpRange.parse("example.org"));
        assertThrows(IllegalArgumentException.class, () -> IpRange.parse("10.0.0"));
        assertThrows(IllegalArgumentException.class, () -> IpRange.parse("10.0.0.256"));
        assertThrows(IllegalArgumentException.class, () -> IpRange.parse("10.0.0.0/33"));
        assertThrows(IllegalArgumentException.class, () -> IpRange.parse("10.0.0.0/x"));
        assertThrows(IllegalArgumentException.class, () -> IpRange.parse("fd00::zz"));
    }
}

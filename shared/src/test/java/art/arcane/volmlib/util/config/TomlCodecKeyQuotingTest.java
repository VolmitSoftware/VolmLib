package art.arcane.volmlib.util.config;

import org.junit.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TomlCodecKeyQuotingTest {
    @Test
    public void quotedKeysParseWithoutTheirQuotes() throws IOException {
        Holder holder = TomlCodec.fromToml("[values]\n\"adapt.xpmultiplier.vip\" = 1.5\n\"group.vip\" = 2.0\nplain = 3.0\n", Holder.class);

        assertEquals(Double.valueOf(1.5D), holder.values.get("adapt.xpmultiplier.vip"));
        assertEquals(Double.valueOf(2.0D), holder.values.get("group.vip"));
        assertEquals(Double.valueOf(3.0D), holder.values.get("plain"));
    }

    @Test
    public void quotedKeysStayStableAcrossRepeatedRewrites() throws IOException {
        Holder holder = new Holder();
        holder.values.put("adapt.xpmultiplier.vip", 1.5D);

        String first = TomlCodec.toToml(holder, "test");
        String second = TomlCodec.toToml(TomlCodec.fromToml(first, Holder.class), "test");
        String third = TomlCodec.toToml(TomlCodec.fromToml(second, Holder.class), "test");

        assertTrue(first.contains("\"adapt.xpmultiplier.vip\" = 1.5"));
        assertEquals(first, second);
        assertEquals(first, third);
    }

    public static class Holder {
        private Map<String, Double> values = new LinkedHashMap<>();
    }
}

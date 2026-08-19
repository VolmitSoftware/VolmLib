package art.arcane.volmlib.util.config;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TomlConfigRoundTripTest {
    @Test
    public void pojoSurvivesTomlRoundTrip() throws IOException {
        RootConfig original = new RootConfig();

        String toml = TomlCodec.toToml(original, "round-trip", ConfigExposePolicy.ALL);
        RootConfig loaded = TomlCodec.fromToml(toml, RootConfig.class);

        assertEquals(original, loaded);
    }

    @Test
    public void allPolicyEmitsEveryFieldWithComments() {
        String toml = TomlCodec.toToml(new RootConfig(), "round-trip", ConfigExposePolicy.ALL);

        assertTrue(toml.contains("# Configuration - round-trip"));
        assertTrue(toml.contains("# Chat prefix prepended to rendered lines."));
        assertTrue(toml.contains("# Effect: Color codes like &7 pass through unchanged."));
        assertTrue(toml.contains("prefix = \"&7[Gloss]\""));
        assertTrue(toml.contains("# Enables or disables this feature."));
        assertTrue(toml.contains("enabled = true"));
        assertTrue(toml.contains("statIntervalMs = 250"));
        assertTrue(toml.contains("advancedKnob = 3"));
        assertTrue(toml.contains("aliases = [\"&7one\", \"two\"]"));
        assertTrue(toml.contains("mode = \"FANCY\""));
        assertTrue(toml.contains("# Settings for Rendering."));
        assertTrue(toml.contains("[rendering]"));
        assertTrue(toml.contains("# Maximum render distance in blocks."));
        assertTrue(toml.contains("maxDistance = 32.5"));
        assertTrue(toml.contains("ticks = [1, 2, 3]"));
        assertTrue(toml.contains("unicode = \"★ ünïcode ✓\""));
        assertTrue(toml.contains("[multipliers]"));
        assertTrue(toml.contains("\"gloss.vip\" = 1.5"));
        assertTrue(toml.contains("plain = 2.0"));
    }

    @Test
    public void curatedPolicyHidesTuningFieldsThatAllEmits() {
        String curated = TomlCodec.toToml(new RootConfig(), "round-trip", new CuratedConfigExposePolicy());

        assertFalse(curated.contains("statIntervalMs"));
        assertFalse(curated.contains("advancedKnob"));
        assertTrue(curated.contains("enabled = true"));
        assertTrue(curated.contains("prefix = \"&7[Gloss]\""));
    }

    public enum Mode {
        SIMPLE,
        FANCY
    }

    public static class RootConfig {
        @ConfigDoc(value = "Chat prefix prepended to rendered lines.", impact = "Color codes like &7 pass through unchanged.")
        private String prefix = "&7[Gloss]";
        private boolean enabled = true;
        private int statIntervalMs = 250;
        @ConfigAdvanced
        private int advancedKnob = 3;
        private List<String> aliases = new ArrayList<>(List.of("&7one", "two"));
        private Mode mode = Mode.FANCY;
        private Section rendering = new Section();
        private Map<String, Double> multipliers = defaultMultipliers();

        private static Map<String, Double> defaultMultipliers() {
            Map<String, Double> out = new LinkedHashMap<>();
            out.put("gloss.vip", 1.5D);
            out.put("plain", 2.0D);
            return out;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof RootConfig config)) {
                return false;
            }
            return enabled == config.enabled
                    && statIntervalMs == config.statIntervalMs
                    && advancedKnob == config.advancedKnob
                    && Objects.equals(prefix, config.prefix)
                    && Objects.equals(aliases, config.aliases)
                    && mode == config.mode
                    && Objects.equals(rendering, config.rendering)
                    && Objects.equals(multipliers, config.multipliers);
        }

        @Override
        public int hashCode() {
            return Objects.hash(prefix, enabled, statIntervalMs, advancedKnob, aliases, mode, rendering, multipliers);
        }
    }

    public static class Section {
        @ConfigDoc("Maximum render distance in blocks.")
        private double maxDistance = 32.5D;
        private List<Integer> ticks = new ArrayList<>(List.of(1, 2, 3));
        private String unicode = "★ ünïcode ✓";

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Section section)) {
                return false;
            }
            return Double.compare(maxDistance, section.maxDistance) == 0
                    && Objects.equals(ticks, section.ticks)
                    && Objects.equals(unicode, section.unicode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(maxDistance, ticks, unicode);
        }
    }
}

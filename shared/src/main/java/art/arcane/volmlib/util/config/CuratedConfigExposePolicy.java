package art.arcane.volmlib.util.config;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Set;

public final class CuratedConfigExposePolicy implements ConfigExposePolicy {
    private static final Set<String> ALWAYS_VISIBLE_KEYS = Set.of(
            "enabled",
            "permanent",
            "baseCost",
            "initialCost",
            "costFactor",
            "maxLevel",
            "minXp",
            "showParticles",
            "showSounds",
            "immunitySoundVolume",
            "levelMilestoneSoundVolume"
    );

    @Override
    public boolean expose(String sourceTag, String path, Field field, Object value) {
        if (field == null) {
            return false;
        }
        if (field.getAnnotation(ConfigAdvanced.class) != null) {
            return false;
        }

        String key = field.getName();
        if (ALWAYS_VISIBLE_KEYS.contains(key)) {
            return true;
        }

        String lowered = key.toLowerCase(Locale.ROOT);
        Class<?> type = field.getType();
        boolean isBoolean = type == boolean.class || type == Boolean.class;

        if (lowered.startsWith("challenge") && lowered.contains("reward")) {
            return false;
        }

        if (lowered.equals("setinterval") || lowered.equals("statintervalms")) {
            return false;
        }

        if (lowered.contains("pitch") || lowered.contains("volume")) {
            return false;
        }
        if (lowered.contains("sound") && !isBoolean) {
            return false;
        }
        if (lowered.contains("particlesize") || lowered.contains("particlecount") || lowered.contains("particleevery")) {
            return false;
        }
        if (lowered.contains("xoffset") || lowered.contains("yoffset") || lowered.contains("zoffset")) {
            return false;
        }

        if (lowered.contains("fallback") || lowered.contains("variance") || lowered.contains("curveexponent")) {
            return false;
        }

        return true;
    }
}

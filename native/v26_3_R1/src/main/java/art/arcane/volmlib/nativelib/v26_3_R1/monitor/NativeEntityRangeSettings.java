package art.arcane.volmlib.nativelib.v26_3_R1.monitor;

import art.arcane.volmlib.nativelib.monitor.EntityRangeSettings;
import org.spigotmc.SpigotWorldConfig;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

final class NativeEntityRangeSettings implements EntityRangeSettings {
    private static final Set<Range> SUPPORTED = Collections.unmodifiableSet(EnumSet.complementOf(EnumSet.of(Range.TRACKING_ITEM)));
    private final SpigotWorldConfig config;

    NativeEntityRangeSettings(SpigotWorldConfig config) {
        this.config = config;
    }

    @Override
    public Set<Range> supportedRanges() {
        return SUPPORTED;
    }

    @Override
    public int range(Range type) {
        return switch (type) {
            case TRACKING_PLAYER -> config.playerTrackingRange;
            case TRACKING_MISC -> config.miscTrackingRange;
            case TRACKING_DISPLAY -> config.displayTrackingRange;
            case TRACKING_ANIMAL -> config.animalTrackingRange;
            case TRACKING_MONSTER -> config.monsterTrackingRange;
            case TRACKING_OTHER -> config.otherTrackingRange;
            case ACTIVATION_ANIMAL -> config.animalActivationRange;
            case ACTIVATION_MONSTER -> config.monsterActivationRange;
            case ACTIVATION_RAIDER -> config.raiderActivationRange;
            case ACTIVATION_MISC -> config.miscActivationRange;
            case ACTIVATION_WATER -> config.waterActivationRange;
            case ACTIVATION_VILLAGER -> config.villagerActivationRange;
            case ACTIVATION_FLYING_MONSTER -> config.flyingMonsterActivationRange;
            case TRACKING_ITEM -> throw new UnsupportedOperationException("Separate item tracking ranges are unavailable");
        };
    }

    @Override
    public void range(Range type, int blocks) {
        switch (type) {
            case TRACKING_PLAYER -> config.playerTrackingRange = blocks;
            case TRACKING_MISC -> config.miscTrackingRange = blocks;
            case TRACKING_DISPLAY -> config.displayTrackingRange = blocks;
            case TRACKING_ANIMAL -> config.animalTrackingRange = blocks;
            case TRACKING_MONSTER -> config.monsterTrackingRange = blocks;
            case TRACKING_OTHER -> config.otherTrackingRange = blocks;
            case ACTIVATION_ANIMAL -> config.animalActivationRange = blocks;
            case ACTIVATION_MONSTER -> config.monsterActivationRange = blocks;
            case ACTIVATION_RAIDER -> config.raiderActivationRange = blocks;
            case ACTIVATION_MISC -> config.miscActivationRange = blocks;
            case ACTIVATION_WATER -> config.waterActivationRange = blocks;
            case ACTIVATION_VILLAGER -> config.villagerActivationRange = blocks;
            case ACTIVATION_FLYING_MONSTER -> config.flyingMonsterActivationRange = blocks;
            case TRACKING_ITEM -> throw new UnsupportedOperationException("Separate item tracking ranges are unavailable");
        }
    }

    @Override
    public boolean tickInactiveVillagers() {
        return config.tickInactiveVillagers;
    }

    @Override
    public void tickInactiveVillagers(boolean enabled) {
        config.tickInactiveVillagers = enabled;
    }
}

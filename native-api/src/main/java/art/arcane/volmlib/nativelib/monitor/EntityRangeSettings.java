package art.arcane.volmlib.nativelib.monitor;

import java.util.Set;

public interface EntityRangeSettings {
    Set<Range> supportedRanges();

    int range(Range type);

    void range(Range type, int blocks);

    boolean tickInactiveVillagers();

    void tickInactiveVillagers(boolean enabled);

    enum Range {
        TRACKING_PLAYER,
        TRACKING_ITEM,
        TRACKING_MISC,
        TRACKING_DISPLAY,
        TRACKING_ANIMAL,
        TRACKING_MONSTER,
        TRACKING_OTHER,
        ACTIVATION_ANIMAL,
        ACTIVATION_MONSTER,
        ACTIVATION_RAIDER,
        ACTIVATION_MISC,
        ACTIVATION_WATER,
        ACTIVATION_VILLAGER,
        ACTIVATION_FLYING_MONSTER
    }
}

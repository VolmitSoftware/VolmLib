package art.arcane.volmlib.util.mantle.flag;

public enum ReservedFlag implements MantleFlag {
    OBJECT,
    UPDATE,
    JIGSAW,
    FEATURE,
    INITIAL_SPAWNED,
    REAL,
    CARVED,
    FLUID_BODIES,
    INITIAL_SPAWNED_MARKER,
    CLEANED,
    PLANNED,
    ETCHED,
    TILE,
    CUSTOM,
    DISCOVERED,
    CUSTOM_ACTIVE,
    SCRIPT,
    FLOATING_OBJECT;

    @Override
    public boolean isCustom() {
        return false;
    }
}

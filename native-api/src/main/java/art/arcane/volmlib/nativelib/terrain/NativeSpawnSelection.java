package art.arcane.volmlib.nativelib.terrain;

public interface NativeSpawnSelection {
    Mode mode();
    String derivativeKey();

    enum Mode { CURRENT, RETAINED, NONE, LOADING }
}

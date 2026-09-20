package art.arcane.volmlib.nativelib.terrain.feature;

public interface NativeFeatureTable {
    long generation();
    int biomeCount();
    int stepCount();
    int derivativeCount();
}

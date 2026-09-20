package art.arcane.volmlib.nativelib.terrain.feature;

public interface NativeImportedFeatureControl {
    boolean shouldGenerateFeatures();
    boolean shouldGenerateStepOrdinal(int step);
    boolean shouldGenerate(String featureKey);
    boolean hasFeatureFilter();
}

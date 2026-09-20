package art.arcane.volmlib.nativelib.terrain.feature;

import art.arcane.volmlib.nativelib.terrain.NativeBlockPositionPredicate;
import java.util.List;
import java.util.Set;
import java.util.function.IntBinaryOperator;

public interface NativeImportedFeaturePolicy<D> {
    D dimension();
    int runtimeId();
    String dimensionKey();
    NativeImportedFeatureControl control();
    Set<String> visibleBiomeKeys();
    List<DerivativeGroup> customBiomeDerivatives();
    Placement placement();
    void info(String message);
    void warn(String message);
    void error(String message, Throwable cause);

    record DerivativeGroup(String source, String derivativeKey, List<String> customKeys) {
    }

    record Placement(boolean guarded, boolean stacked, IntBinaryOperator surfaceFirstFreeY,
                     IntBinaryOperator floorFirstFreeY, NativeBlockPositionPredicate protectedPosition,
                     NativeBlockPositionPredicate acceptedBiomePosition) {
    }

}

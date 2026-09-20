package art.arcane.volmlib.nativelib.terrain;

import java.io.IOException;

public interface NativeGenerationRoute extends NativeGenerationScope {
    NativeGenerationScope openRuntimeScope();
    void detachThread();
    boolean claimGeneratedSemantics(NativeBlockPositionPredicate caveSpace) throws IOException;
}

package art.arcane.volmlib.integration;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public interface ReloadAware {
    default CompletionStage<ReloadPreparation> prepareReload(PreUnloadReason reason) {
        return CompletableFuture.completedFuture(ReloadPreparation.readyToUnload());
    }

    default CompletionStage<Void> cancelReload() {
        return CompletableFuture.completedFuture(null);
    }

    CompletionStage<Void> commitReload(PreUnloadReason reason);

    enum PreUnloadReason {
        HOT_RELOAD,
        HOT_UNLOAD
    }
}

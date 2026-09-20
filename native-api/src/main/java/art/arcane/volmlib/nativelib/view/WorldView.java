package art.arcane.volmlib.nativelib.view;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface WorldView {
    List<WorldMarker> players();
    void requestEntities(Consumer<List<WorldMarker>> sink);
    void execute(Runnable task);
    Optional<TeleportOperation> teleport(UUID playerId, Destination destination);

    record Destination(double x, double y, double z, long deadlineNanos) {
    }

    record TeleportOperation(UUID playerId, CompletableFuture<Boolean> result) {
    }
}

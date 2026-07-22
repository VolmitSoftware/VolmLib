package art.arcane.volmlib.util.localization;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class LocalizationManager {
    private final AtomicReference<LocalizationSnapshot> current;

    public LocalizationManager(LocalizationCandidate initialCandidate) {
        current = new AtomicReference<>(LocalizationSnapshot.create(initialCandidate));
    }

    public LocalizationSnapshot snapshot() {
        return current.get();
    }

    public LocalizationReloadResult reload(LocalizationCandidate candidate) {
        try {
            LocalizationSnapshot next = LocalizationSnapshot.create(
                    Objects.requireNonNull(candidate, "Localization candidate cannot be null")
            );
            LocalizationSnapshot previous = current.getAndSet(next);
            return LocalizationReloadResult.applied(previous, next);
        } catch (LocalizationValidationException exception) {
            LocalizationSnapshot retained = current.get();
            return LocalizationReloadResult.rejected(retained, exception.result(), exception);
        } catch (RuntimeException exception) {
            LocalizationSnapshot retained = current.get();
            return LocalizationReloadResult.rejected(
                    retained,
                    LocalizationValidationResult.empty(),
                    exception
            );
        }
    }

    public LocalizationReloadResult reload(LocalizationLoader loader) {
        Objects.requireNonNull(loader, "Localization loader cannot be null");
        try {
            return reload(loader.load());
        } catch (LocalizationValidationException exception) {
            LocalizationSnapshot retained = current.get();
            return LocalizationReloadResult.rejected(retained, exception.result(), exception);
        } catch (Exception exception) {
            LocalizationSnapshot retained = current.get();
            return LocalizationReloadResult.rejected(
                    retained,
                    LocalizationValidationResult.empty(),
                    exception
            );
        }
    }
}

package art.arcane.volmlib.util.localization;

import java.util.Objects;

public record LocalizationReloadResult(
        boolean applied,
        LocalizationSnapshot previous,
        LocalizationSnapshot current,
        LocalizationValidationResult validation,
        Exception failure
) {
    public LocalizationReloadResult {
        previous = Objects.requireNonNull(previous, "Previous localization snapshot cannot be null");
        current = Objects.requireNonNull(current, "Current localization snapshot cannot be null");
        validation = Objects.requireNonNull(validation, "Localization validation result cannot be null");
        if (applied && failure != null) {
            throw new IllegalArgumentException("Applied reload cannot contain a failure");
        }
        if (!applied && previous != current) {
            throw new IllegalArgumentException("Rejected reload must retain the previous snapshot");
        }
    }

    static LocalizationReloadResult applied(
            LocalizationSnapshot previous,
            LocalizationSnapshot current
    ) {
        return new LocalizationReloadResult(true, previous, current, current.validation(), null);
    }

    static LocalizationReloadResult rejected(
            LocalizationSnapshot current,
            LocalizationValidationResult validation,
            Exception failure
    ) {
        return new LocalizationReloadResult(false, current, current, validation, failure);
    }
}

package art.arcane.volmlib.util.localization;

import java.util.Objects;

public final class LocalizationValidationException extends IllegalArgumentException {
    private final LocalizationValidationResult result;

    public LocalizationValidationException(LocalizationValidationResult result) {
        super(message(Objects.requireNonNull(result, "Localization validation result cannot be null")));
        this.result = result;
    }

    public LocalizationValidationResult result() {
        return result;
    }

    private static String message(LocalizationValidationResult result) {
        if (result.errors().isEmpty()) {
            return "Localization validation failed";
        }
        LocalizationIssue first = result.errors().getFirst();
        return "Localization validation failed with "
                + result.errors().size()
                + " error(s): "
                + first.code()
                + " "
                + first.key()
                + " "
                + first.detail();
    }
}

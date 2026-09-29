package art.arcane.volmlib.util.localization;

import java.util.List;
import java.util.Objects;

public record LocalizationCandidate(
        MessageCatalog catalog,
        List<LocaleOverlay> overlays,
        PluralSelector pluralSelector
) {
    public LocalizationCandidate {
        catalog = Objects.requireNonNull(catalog, "Message catalog cannot be null");
        overlays = overlays == null ? List.of() : List.copyOf(overlays);
        for (LocaleOverlay overlay : overlays) {
            Objects.requireNonNull(overlay, "Locale overlay cannot be null");
        }
        pluralSelector = Objects.requireNonNull(pluralSelector, "Plural selector cannot be null");
    }

    public static LocalizationCandidate english(MessageCatalog catalog, PluralSelector pluralSelector) {
        return new LocalizationCandidate(catalog, List.of(), pluralSelector);
    }
}

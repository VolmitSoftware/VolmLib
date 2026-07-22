package art.arcane.volmlib.util.localization;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LocalizationValidator {
    private LocalizationValidator() {
    }

    public static LocalizationValidationResult validate(MessageCatalog catalog, List<LocaleOverlay> overlays) {
        List<LocalizationIssue> issues = new ArrayList<>();
        for (LocaleOverlay overlay : overlays) {
            validateOverlayValues(catalog, overlay, issues);
            findMissingValues(catalog, overlay, issues);
        }
        return new LocalizationValidationResult(issues);
    }

    private static void validateOverlayValues(
            MessageCatalog catalog,
            LocaleOverlay overlay,
            List<LocalizationIssue> issues
    ) {
        for (Map.Entry<String, MessageValue> entry : overlay.values().entrySet()) {
            String id = entry.getKey();
            MessageValue value = entry.getValue();
            MessageKey definition = catalog.key(id);
            if (definition == null) {
                issues.add(new LocalizationIssue(
                        LocalizationSeverity.ERROR,
                        LocalizationIssueCode.UNUSED_KEY,
                        overlay.source(),
                        id,
                        "Locale overlay key is not declared by the message catalog"
                ));
                continue;
            }

            if (definition.shape() != value.shape()) {
                issues.add(new LocalizationIssue(
                        LocalizationSeverity.ERROR,
                        LocalizationIssueCode.SHAPE_MISMATCH,
                        overlay.source(),
                        id,
                        "Expected " + definition.shape() + " but found " + value.shape()
                ));
                continue;
            }

            validateValueStructure(definition, value, overlay, issues);

            Set<String> actualPlaceholders = overlayPlaceholders(definition, value);
            if (!definition.placeholders().equals(actualPlaceholders)) {
                issues.add(new LocalizationIssue(
                        LocalizationSeverity.ERROR,
                        LocalizationIssueCode.PLACEHOLDER_MISMATCH,
                        overlay.source(),
                        id,
                        "Expected " + definition.placeholders() + " but found " + actualPlaceholders
                ));
            }
        }
    }

    private static void validateValueStructure(
            MessageKey definition,
            MessageValue value,
            LocaleOverlay overlay,
            List<LocalizationIssue> issues
    ) {
        if (definition instanceof LinesKey linesKey && value instanceof LinesValue linesValue) {
            validateLineStructure(linesKey, linesValue, overlay, issues);
            return;
        }
        if (definition instanceof PluralKey pluralKey && value instanceof PluralValue pluralValue) {
            validatePluralStructure(pluralKey, pluralValue, overlay, issues);
        }
    }

    private static void validateLineStructure(
            LinesKey definition,
            LinesValue value,
            LocaleOverlay overlay,
            List<LocalizationIssue> issues
    ) {
        if (definition.english().size() != value.lines().size()) {
            issues.add(new LocalizationIssue(
                    LocalizationSeverity.ERROR,
                    LocalizationIssueCode.SHAPE_MISMATCH,
                    overlay.source(),
                    definition.id(),
                    "Expected " + definition.english().size() + " lines but found " + value.lines().size()
            ));
            return;
        }

        for (int index = 0; index < definition.english().size(); index++) {
            Set<String> expected = LocalizationSupport.placeholders(definition.english().get(index));
            Set<String> actual = LocalizationSupport.placeholders(value.lines().get(index));
            if (expected.equals(actual)) {
                continue;
            }
            issues.add(new LocalizationIssue(
                    LocalizationSeverity.ERROR,
                    LocalizationIssueCode.PLACEHOLDER_MISMATCH,
                    overlay.source(),
                    definition.id() + "[" + index + "]",
                    "Expected " + expected + " but found " + actual
            ));
        }
    }

    private static void validatePluralStructure(
            PluralKey definition,
            PluralValue value,
            LocaleOverlay overlay,
            List<LocalizationIssue> issues
    ) {
        if (!definition.english().keySet().equals(value.forms().keySet())) {
            issues.add(new LocalizationIssue(
                    LocalizationSeverity.ERROR,
                    LocalizationIssueCode.SHAPE_MISMATCH,
                    overlay.source(),
                    definition.id(),
                    "Expected plural forms " + definition.english().keySet() + " but found " + value.forms().keySet()
            ));
        }

        for (Map.Entry<String, String> form : definition.english().entrySet()) {
            String translated = value.forms().get(form.getKey());
            if (translated == null) {
                continue;
            }
            Set<String> expected = new LinkedHashSet<>(LocalizationSupport.placeholders(form.getValue()));
            Set<String> actual = new LinkedHashSet<>(LocalizationSupport.placeholders(translated));
            expected.remove(definition.selectorArgument());
            actual.remove(definition.selectorArgument());
            if (expected.equals(actual)) {
                continue;
            }
            issues.add(new LocalizationIssue(
                    LocalizationSeverity.ERROR,
                    LocalizationIssueCode.PLACEHOLDER_MISMATCH,
                    overlay.source(),
                    definition.id() + "." + form.getKey(),
                    "Expected " + expected + " but found " + actual
            ));
        }
    }

    private static void findMissingValues(
            MessageCatalog catalog,
            LocaleOverlay overlay,
            List<LocalizationIssue> issues
    ) {
        for (String id : catalog.ids()) {
            if (overlay.values().containsKey(id)) {
                continue;
            }
            issues.add(new LocalizationIssue(
                    LocalizationSeverity.WARNING,
                    LocalizationIssueCode.MISSING_KEY,
                    overlay.source(),
                    id,
                    "Locale overlay falls through to a lower-priority value"
            ));
        }
    }

    private static Set<String> overlayPlaceholders(MessageKey definition, MessageValue value) {
        Set<String> placeholders = new LinkedHashSet<>(value.placeholders());
        if (definition instanceof PluralKey pluralKey) {
            placeholders.add(pluralKey.selectorArgument());
        }
        return Set.copyOf(placeholders);
    }
}

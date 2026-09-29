package art.arcane.volmlib.util.localization;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class LocalizationSnapshot {
    private final MessageCatalog catalog;
    private final List<LocaleOverlay> overlays;
    private final PluralSelector pluralSelector;
    private final LocalizationValidationResult validation;
    private final Map<String, ResolvedValue> resolvedValues;

    private LocalizationSnapshot(
            MessageCatalog catalog,
            List<LocaleOverlay> overlays,
            PluralSelector pluralSelector,
            LocalizationValidationResult validation,
            Map<String, ResolvedValue> resolvedValues
    ) {
        this.catalog = catalog;
        this.overlays = List.copyOf(overlays);
        this.pluralSelector = pluralSelector;
        this.validation = validation;
        this.resolvedValues = Collections.unmodifiableMap(new LinkedHashMap<>(resolvedValues));
    }

    public static LocalizationSnapshot create(LocalizationCandidate candidate) {
        LocalizationCandidate resolvedCandidate = Objects.requireNonNull(candidate, "Localization candidate cannot be null");
        LocalizationValidationResult validation = LocalizationValidator.validate(
                resolvedCandidate.catalog(),
                resolvedCandidate.overlays()
        );
        validation.throwIfInvalid();
        Map<String, ResolvedValue> resolvedValues = buildResolvedValues(
                resolvedCandidate.catalog(),
                resolvedCandidate.overlays()
        );
        return new LocalizationSnapshot(
                resolvedCandidate.catalog(),
                resolvedCandidate.overlays(),
                resolvedCandidate.pluralSelector(),
                validation,
                resolvedValues
        );
    }

    public MessageCatalog catalog() {
        return catalog;
    }

    public List<LocaleOverlay> overlays() {
        return overlays;
    }

    public LocalizationValidationResult validation() {
        return validation;
    }

    public ResolvedText resolve(TextKey key) {
        return resolve(key, MessageArgs.empty());
    }

    public ResolvedText resolve(TextKey key, MessageArgs arguments) {
        TextKey definition = requireDefinition(key, TextKey.class);
        MessageArgs resolvedArguments = requireArguments(definition, arguments);
        ResolvedValue resolved = resolvedValues.get(definition.id());
        TextValue value = (TextValue) resolved.value();
        return new ResolvedText(definition.id(), resolved.locale(), value.template(), resolvedArguments);
    }

    public ResolvedLines resolve(LinesKey key) {
        return resolve(key, MessageArgs.empty());
    }

    public ResolvedLines resolve(LinesKey key, MessageArgs arguments) {
        LinesKey definition = requireDefinition(key, LinesKey.class);
        MessageArgs resolvedArguments = requireArguments(definition, arguments);
        ResolvedValue resolved = resolvedValues.get(definition.id());
        LinesValue value = (LinesValue) resolved.value();
        return new ResolvedLines(definition.id(), resolved.locale(), value.lines(), resolvedArguments);
    }

    public ResolvedText resolve(PluralKey key, MessageArgs arguments) {
        PluralKey definition = requireDefinition(key, PluralKey.class);
        MessageArgs resolvedArguments = requireArguments(definition, arguments);
        MessageArgument selector = resolvedArguments.require(definition.selectorArgument());
        if (!(selector.value() instanceof Number quantity)) {
            throw new IllegalArgumentException("Plural selector argument must be numeric: " + definition.selectorArgument());
        }

        ResolvedValue resolved = resolvedValues.get(definition.id());
        PluralValue value = (PluralValue) resolved.value();
        String category = pluralSelector.select(resolved.locale(), quantity);
        String normalizedCategory = LocalizationSupport.requirePluralCategory(category);
        String template = value.forms().getOrDefault(normalizedCategory, value.forms().get("other"));
        return new ResolvedText(definition.id(), resolved.locale(), template, resolvedArguments);
    }

    public MessageValue value(MessageKey key) {
        MessageKey definition = catalog.requireDefinition(key);
        return resolvedValues.get(definition.id()).value();
    }

    public String sourceLocale(MessageKey key) {
        MessageKey definition = catalog.requireDefinition(key);
        return resolvedValues.get(definition.id()).locale();
    }

    private static Map<String, ResolvedValue> buildResolvedValues(
            MessageCatalog catalog,
            List<LocaleOverlay> overlays
    ) {
        Map<String, ResolvedValue> resolved = new LinkedHashMap<>();
        for (MessageKey key : catalog.keys()) {
            resolved.put(key.id(), new ResolvedValue(catalog.englishLocale(), key.englishValue()));
        }

        for (int index = overlays.size() - 1; index >= 0; index--) {
            LocaleOverlay overlay = overlays.get(index);
            for (Map.Entry<String, MessageValue> entry : overlay.values().entrySet()) {
                resolved.put(entry.getKey(), new ResolvedValue(overlay.locale(), entry.getValue()));
            }
        }
        return resolved;
    }

    private <T extends MessageKey> T requireDefinition(T key, Class<T> expectedType) {
        MessageKey definition = catalog.requireDefinition(key);
        if (!expectedType.isInstance(definition)) {
            throw new IllegalArgumentException("Unexpected message shape for key: " + key.id());
        }
        return expectedType.cast(definition);
    }

    private MessageArgs requireArguments(MessageKey key, MessageArgs arguments) {
        MessageArgs resolved = arguments == null ? MessageArgs.empty() : arguments;
        Set<String> expected = key.placeholders();
        if (expected.equals(resolved.names())) {
            return resolved;
        }

        Set<String> missing = new LinkedHashSet<>(expected);
        missing.removeAll(resolved.names());
        Set<String> unexpected = new LinkedHashSet<>(resolved.names());
        unexpected.removeAll(expected);
        throw new IllegalArgumentException(
                "Arguments do not match message " + key.id() + "; missing=" + missing + ", unexpected=" + unexpected
        );
    }

    private record ResolvedValue(String locale, MessageValue value) {
        private ResolvedValue {
            locale = LocalizationSupport.requireLocale(locale);
            value = Objects.requireNonNull(value, "Resolved message value cannot be null");
        }
    }
}

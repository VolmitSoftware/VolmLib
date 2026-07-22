package art.arcane.volmlib.util.localization;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record PluralValue(Map<String, String> forms) implements MessageValue {
    public PluralValue {
        Objects.requireNonNull(forms, "Plural forms cannot be null");
        if (forms.isEmpty()) {
            throw new IllegalArgumentException("Plural forms cannot be empty");
        }

        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : forms.entrySet()) {
            String category = LocalizationSupport.requirePluralCategory(entry.getKey());
            String template = Objects.requireNonNull(entry.getValue(), "Plural template cannot be null");
            LocalizationSupport.placeholders(template);
            if (copy.putIfAbsent(category, template) != null) {
                throw new IllegalArgumentException("Duplicate plural category: " + category);
            }
        }
        if (!copy.containsKey("other")) {
            throw new IllegalArgumentException("Plural forms must define other");
        }
        forms = Collections.unmodifiableMap(copy);
    }

    @Override
    public MessageShape shape() {
        return MessageShape.PLURAL;
    }

    @Override
    public Set<String> placeholders() {
        Set<String> placeholders = new LinkedHashSet<>();
        for (String template : forms.values()) {
            placeholders.addAll(LocalizationSupport.placeholders(template));
        }
        return Set.copyOf(placeholders);
    }
}

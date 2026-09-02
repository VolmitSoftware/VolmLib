package art.arcane.volmlib.util.localization;

import java.util.Objects;
import java.util.Set;

public record TextKey(String id, String english, Set<String> optionalPlaceholders) implements MessageKey {
    public TextKey {
        id = LocalizationSupport.requireMessageId(id);
        english = Objects.requireNonNull(english, "English text cannot be null");
        Set<String> placeholders = LocalizationSupport.placeholders(english);
        optionalPlaceholders = Set.copyOf(Objects.requireNonNull(
                optionalPlaceholders,
                "Optional placeholders cannot be null"
        ));
        if (!placeholders.containsAll(optionalPlaceholders)) {
            throw new IllegalArgumentException("Optional placeholders must be present in the English text");
        }
    }

    public TextKey(String id, String english) {
        this(id, english, Set.of());
    }

    public static TextKey of(String id, String english) {
        return new TextKey(id, english);
    }

    public static TextKey ofOptional(String id, String english, String... optionalPlaceholders) {
        return new TextKey(id, english, Set.of(optionalPlaceholders));
    }

    @Override
    public MessageShape shape() {
        return MessageShape.TEXT;
    }

    @Override
    public MessageValue englishValue() {
        return new TextValue(english);
    }

    @Override
    public Set<String> placeholders() {
        return LocalizationSupport.placeholders(english);
    }
}

package art.arcane.volmlib.util.localization;

import java.util.Objects;
import java.util.Set;

public record TextKey(String id, String english) implements MessageKey {
    public TextKey {
        id = LocalizationSupport.requireMessageId(id);
        english = Objects.requireNonNull(english, "English text cannot be null");
        LocalizationSupport.placeholders(english);
    }

    public static TextKey of(String id, String english) {
        return new TextKey(id, english);
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

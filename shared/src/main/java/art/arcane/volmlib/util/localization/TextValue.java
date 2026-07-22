package art.arcane.volmlib.util.localization;

import java.util.Objects;
import java.util.Set;

public record TextValue(String template) implements MessageValue {
    public TextValue {
        template = Objects.requireNonNull(template, "Text template cannot be null");
        LocalizationSupport.placeholders(template);
    }

    @Override
    public MessageShape shape() {
        return MessageShape.TEXT;
    }

    @Override
    public Set<String> placeholders() {
        return LocalizationSupport.placeholders(template);
    }
}

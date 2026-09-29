package art.arcane.volmlib.util.localization;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public record PluralKey(String id, String selectorArgument, Map<String, String> english) implements MessageKey {
    public PluralKey {
        id = LocalizationSupport.requireMessageId(id);
        selectorArgument = LocalizationSupport.requirePlaceholderName(selectorArgument);
        english = new PluralValue(english).forms();
    }

    public static PluralKey of(String id, String selectorArgument, Map<String, String> english) {
        return new PluralKey(id, selectorArgument, english);
    }

    @Override
    public MessageShape shape() {
        return MessageShape.PLURAL;
    }

    @Override
    public MessageValue englishValue() {
        return new PluralValue(english);
    }

    @Override
    public Set<String> placeholders() {
        Set<String> placeholders = new LinkedHashSet<>(englishValue().placeholders());
        placeholders.add(selectorArgument);
        return Set.copyOf(placeholders);
    }
}

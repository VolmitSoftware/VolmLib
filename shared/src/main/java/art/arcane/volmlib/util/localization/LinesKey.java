package art.arcane.volmlib.util.localization;

import java.util.List;
import java.util.Set;

public record LinesKey(String id, List<String> english) implements MessageKey {
    public LinesKey {
        id = LocalizationSupport.requireMessageId(id);
        english = new LinesValue(english).lines();
    }

    public static LinesKey of(String id, List<String> english) {
        return new LinesKey(id, english);
    }

    public static LinesKey of(String id, String... english) {
        return new LinesKey(id, List.of(english));
    }

    @Override
    public MessageShape shape() {
        return MessageShape.LINES;
    }

    @Override
    public MessageValue englishValue() {
        return new LinesValue(english);
    }

    @Override
    public Set<String> placeholders() {
        return englishValue().placeholders();
    }
}

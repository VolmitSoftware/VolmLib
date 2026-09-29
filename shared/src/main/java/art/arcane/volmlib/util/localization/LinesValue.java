package art.arcane.volmlib.util.localization;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record LinesValue(List<String> lines) implements MessageValue {
    public LinesValue {
        Objects.requireNonNull(lines, "Message lines cannot be null");
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Message lines cannot be empty");
        }

        List<String> copy = new ArrayList<>(lines.size());
        for (String line : lines) {
            String resolved = Objects.requireNonNull(line, "Message line cannot be null");
            LocalizationSupport.placeholders(resolved);
            copy.add(resolved);
        }
        lines = List.copyOf(copy);
    }

    @Override
    public MessageShape shape() {
        return MessageShape.LINES;
    }

    @Override
    public Set<String> placeholders() {
        Set<String> placeholders = new LinkedHashSet<>();
        for (String line : lines) {
            placeholders.addAll(LocalizationSupport.placeholders(line));
        }
        return Set.copyOf(placeholders);
    }
}

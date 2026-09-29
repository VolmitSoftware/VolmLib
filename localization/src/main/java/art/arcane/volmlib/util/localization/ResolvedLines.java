package art.arcane.volmlib.util.localization;

import java.util.List;
import java.util.Objects;

public record ResolvedLines(String key, String locale, List<String> lines, MessageArgs arguments) {
    public ResolvedLines {
        key = LocalizationSupport.requireMessageId(key);
        locale = LocalizationSupport.requireLocale(locale);
        lines = List.copyOf(lines);
        arguments = Objects.requireNonNull(arguments, "Resolved line arguments cannot be null");
    }
}

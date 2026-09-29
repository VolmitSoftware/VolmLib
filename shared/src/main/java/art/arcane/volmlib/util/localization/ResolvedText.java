package art.arcane.volmlib.util.localization;

import java.util.Objects;

public record ResolvedText(String key, String locale, String template, MessageArgs arguments) {
    public ResolvedText {
        key = LocalizationSupport.requireMessageId(key);
        locale = LocalizationSupport.requireLocale(locale);
        template = Objects.requireNonNull(template, "Resolved text template cannot be null");
        arguments = Objects.requireNonNull(arguments, "Resolved text arguments cannot be null");
    }
}

package art.arcane.volmlib.util.localization;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

final class LocalizationSupport {
    private static final Pattern MESSAGE_ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]*");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]*");
    private static final Pattern PLURAL_CATEGORY_PATTERN = Pattern.compile("[a-z][a-z0-9_-]*");

    private LocalizationSupport() {
    }

    static String requireMessageId(String id) {
        if (id == null || !MESSAGE_ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid message id: " + id);
        }
        return id;
    }

    static String requireLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            throw new IllegalArgumentException("Locale cannot be blank");
        }
        return locale.trim();
    }

    static String requireSource(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Overlay source cannot be blank");
        }
        return source.trim();
    }

    static String requirePlaceholderName(String name) {
        if (name == null || !PLACEHOLDER_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid placeholder name: " + name);
        }
        return name;
    }

    static String requirePluralCategory(String category) {
        if (category == null || !PLURAL_CATEGORY_PATTERN.matcher(category).matches()) {
            throw new IllegalArgumentException("Invalid plural category: " + category);
        }
        return category;
    }

    static Set<String> placeholders(String template) {
        if (template == null) {
            throw new IllegalArgumentException("Message template cannot be null");
        }

        Set<String> placeholders = new LinkedHashSet<>();
        int length = template.length();
        for (int index = 0; index < length; index++) {
            char current = template.charAt(index);
            if (current == '{') {
                if (index + 1 < length && template.charAt(index + 1) == '{') {
                    index++;
                    continue;
                }

                int end = template.indexOf('}', index + 1);
                if (end < 0) {
                    throw new IllegalArgumentException("Unclosed placeholder in template: " + template);
                }
                String name = template.substring(index + 1, end);
                placeholders.add(requirePlaceholderName(name));
                index = end;
                continue;
            }

            if (current == '}') {
                if (index + 1 < length && template.charAt(index + 1) == '}') {
                    index++;
                    continue;
                }
                throw new IllegalArgumentException("Unexpected closing brace in template: " + template);
            }
        }
        return Set.copyOf(placeholders);
    }
}

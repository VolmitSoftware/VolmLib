package art.arcane.volmlib.util.director;

import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@FunctionalInterface
public interface DirectorTextResolver {
    DirectorTextResolver ENGLISH = DirectorTextResolver::resolveEnglish;

    String resolve(TextKey key, MessageArgs arguments);

    default String resolve(TextKey key) {
        return resolve(key, MessageArgs.empty());
    }

    default String resolve(TextKey key, MessageArgument... arguments) {
        MessageArgs.Builder builder = MessageArgs.builder();
        if (arguments != null) {
            for (MessageArgument argument : arguments) {
                builder.add(argument);
            }
        }
        return resolve(key, builder.build());
    }

    private static String resolveEnglish(TextKey key, MessageArgs arguments) {
        TextKey resolvedKey = Objects.requireNonNull(key, "Director message key cannot be null");
        MessageArgs resolvedArguments = arguments == null ? MessageArgs.empty() : arguments;
        Set<String> expected = resolvedKey.placeholders();
        if (!expected.equals(resolvedArguments.names())) {
            Set<String> missing = new LinkedHashSet<>(expected);
            missing.removeAll(resolvedArguments.names());
            Set<String> unexpected = new LinkedHashSet<>(resolvedArguments.names());
            unexpected.removeAll(expected);
            throw new IllegalArgumentException(
                    "Arguments do not match message " + resolvedKey.id()
                            + "; missing=" + missing + ", unexpected=" + unexpected
            );
        }

        String template = resolvedKey.english();
        StringBuilder rendered = new StringBuilder(template.length());
        int index = 0;
        while (index < template.length()) {
            char current = template.charAt(index);
            if (current == '{') {
                if (index + 1 < template.length() && template.charAt(index + 1) == '{') {
                    rendered.append('{');
                    index += 2;
                    continue;
                }

                int end = template.indexOf('}', index + 1);
                String name = template.substring(index + 1, end);
                rendered.append(resolvedArguments.require(name).value());
                index = end + 1;
                continue;
            }

            if (current == '}' && index + 1 < template.length() && template.charAt(index + 1) == '}') {
                rendered.append('}');
                index += 2;
                continue;
            }

            rendered.append(current);
            index++;
        }
        return rendered.toString();
    }
}

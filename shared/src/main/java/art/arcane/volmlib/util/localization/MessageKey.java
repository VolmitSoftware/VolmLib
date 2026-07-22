package art.arcane.volmlib.util.localization;

import java.util.Set;

public sealed interface MessageKey permits TextKey, LinesKey, PluralKey {
    String id();

    MessageShape shape();

    MessageValue englishValue();

    Set<String> placeholders();
}

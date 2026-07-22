package art.arcane.volmlib.util.localization;

import java.util.Set;

public sealed interface MessageValue permits TextValue, LinesValue, PluralValue {
    MessageShape shape();

    Set<String> placeholders();
}

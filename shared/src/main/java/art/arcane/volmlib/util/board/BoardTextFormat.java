package art.arcane.volmlib.util.board;

import art.arcane.volmlib.util.plugin.ComponentText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public enum BoardTextFormat {
    LEGACY,
    MINI_MESSAGE;

    public String json(String text) {
        Component component = this == MINI_MESSAGE
                ? MiniMessage.miniMessage().deserialize(ComponentText.normalizeMarkup(text))
                : LegacyComponentSerializer.legacySection().deserialize(text == null ? "" : text);
        return GsonComponentSerializer.gson().serialize(component);
    }
}

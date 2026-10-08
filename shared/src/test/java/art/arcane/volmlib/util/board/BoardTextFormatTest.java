package art.arcane.volmlib.util.board;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BoardTextFormatTest {
    @Test
    public void richRowsPreserveBothFontsAndMixedLegacyColors() {
        Component actual = GsonComponentSerializer.gson().deserialize(BoardTextFormat.MINI_MESSAGE.json(
                "§aA<font:nativeqa:gold>\ue000</font><font:nativeqa:cyan>\ue001</font>B"));
        Component expected = Component.text("A", NamedTextColor.GREEN)
                .append(Component.text("\ue000").font(Key.key("nativeqa:gold")))
                .append(Component.text("\ue001").font(Key.key("nativeqa:cyan")))
                .append(Component.text("B"));
        assertEquals(expected.compact(), actual.compact());
    }

    @Test
    public void legacyModeKeepsAuthoredMarkupLiteral() {
        String raw = "<font:nativeqa:gold>\ue000</font>";
        Component actual = GsonComponentSerializer.gson().deserialize(BoardTextFormat.LEGACY.json(raw));
        assertEquals(raw, ((TextComponent) actual).content());
    }
}

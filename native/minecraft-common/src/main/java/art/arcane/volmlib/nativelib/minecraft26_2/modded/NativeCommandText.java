package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.function.UnaryOperator;

public final class NativeCommandText {
    private final MutableComponent value;

    private NativeCommandText(MutableComponent value) {
        this.value = value;
    }

    public static NativeCommandText literal(String text) {
        return new NativeCommandText(Component.literal(text));
    }

    public static NativeCommandText empty() {
        return new NativeCommandText(Component.empty());
    }

    public NativeCommandText append(NativeCommandText text) {
        value.append(text.value);
        return this;
    }

    public NativeCommandText append(String text) {
        value.append(text);
        return this;
    }

    public NativeCommandText withStyle(UnaryOperator<TextStyle> styles) {
        value.setStyle(styles.apply(new TextStyle(value.getStyle())).value());
        return this;
    }

    public NativeCommandText withStyle(Format... formats) {
        for (Format format : formats) {
            value.withStyle(ChatFormatting.valueOf(format.name()));
        }
        return this;
    }

    public String getString() {
        return value.getString();
    }

    Component component() {
        return value;
    }

    public enum Format {
        BLACK, DARK_BLUE, DARK_GREEN, DARK_AQUA, DARK_RED, DARK_PURPLE, GOLD, GRAY,
        DARK_GRAY, BLUE, GREEN, AQUA, RED, LIGHT_PURPLE, YELLOW, WHITE,
        OBFUSCATED, BOLD, STRIKETHROUGH, UNDERLINE, ITALIC, RESET
    }

    public static final class TextStyle {
        private final Style value;

        private TextStyle(Style value) {
            this.value = value;
        }

        public TextStyle withColor(int rgb) {
            return new TextStyle(value.withColor(TextColor.fromRgb(rgb)));
        }

        public TextStyle withBold(boolean bold) {
            return new TextStyle(value.withBold(bold));
        }

        public TextStyle withStrikethrough(boolean strikethrough) {
            return new TextStyle(value.withStrikethrough(strikethrough));
        }

        public TextStyle copyToClipboard(String text) {
            return new TextStyle(value.withClickEvent(new ClickEvent.CopyToClipboard(text)));
        }

        public TextStyle runCommand(String command) {
            return new TextStyle(value.withClickEvent(new ClickEvent.RunCommand(command)));
        }

        public TextStyle suggestCommand(String command) {
            return new TextStyle(value.withClickEvent(new ClickEvent.SuggestCommand(command)));
        }

        public TextStyle hover(NativeCommandText text) {
            return new TextStyle(value.withHoverEvent(new HoverEvent.ShowText(text.value)));
        }

        private Style value() {
            return value;
        }
    }
}

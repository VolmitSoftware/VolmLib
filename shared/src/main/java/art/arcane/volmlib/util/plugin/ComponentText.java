package art.arcane.volmlib.util.plugin;

import art.arcane.volmlib.util.format.ColorFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.net.URI;
import java.util.Objects;

public final class ComponentText {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('\u00a7')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final Component component;
    private final String miniMessage;
    private volatile String legacy;
    private volatile String plain;

    private ComponentText(Component component) {
        this.component = component;
        miniMessage = MINI_MESSAGE.serialize(component);
    }

    public static ComponentText empty() {
        return literal("");
    }

    public static ComponentText markup(String trustedMarkup) {
        String source = Objects.requireNonNullElse(trustedMarkup, "");
        try {
            return new ComponentText(MINI_MESSAGE.deserialize(normalizeMarkup(source)));
        } catch (RuntimeException ignored) {
            return legacy(source);
        }
    }

    public static String normalizeMarkup(String mixedText) {
        String input = Objects.requireNonNullElse(mixedText, "");
        return legacyToMiniMessage(input);
    }

    public static ComponentText legacy(String legacyText) {
        String source = Objects.requireNonNullElse(legacyText, "");
        return new ComponentText(LEGACY.deserialize(ColorFormatter.translateColors(source)));
    }

    public static ComponentText section(String sectionText) {
        return new ComponentText(LEGACY.deserialize(Objects.requireNonNullElse(sectionText, "")));
    }

    public static ComponentText legacyOnly(String mixedText) {
        String source = Objects.requireNonNullElse(mixedText, "");
        return legacy(MINI_MESSAGE.stripTags(source));
    }

    public static ComponentText literal(String plainText) {
        return new ComponentText(Component.text(Objects.requireNonNullElse(plainText, "")));
    }

    public static ComponentText component(Object adventureComponent) {
        if (!(adventureComponent instanceof Component component)) {
            throw new IllegalArgumentException("adventureComponent must be an Adventure Component");
        }
        return new ComponentText(component);
    }

    public ComponentText append(ComponentText suffix) {
        ComponentText requiredSuffix = Objects.requireNonNull(suffix, "suffix");
        return new ComponentText(Component.empty().append(component).append(requiredSuffix.component));
    }

    public ComponentText hover(ComponentText content) {
        ComponentText requiredContent = Objects.requireNonNull(content, "content");
        return new ComponentText(component.hoverEvent(requiredContent.component));
    }

    public ComponentText clickRunCommand(String command) {
        String requiredCommand = Objects.requireNonNull(command, "command");
        if (!requiredCommand.startsWith("/")) {
            throw new IllegalArgumentException("Run-command text must start with /");
        }
        return new ComponentText(component.clickEvent(ClickEvent.runCommand(requiredCommand)));
    }

    public ComponentText clickOpenUrl(URI url) {
        URI requiredUrl = Objects.requireNonNull(url, "url");
        String scheme = Objects.requireNonNullElse(requiredUrl.getScheme(), "");
        if ((!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http"))
                || requiredUrl.getHost() == null || requiredUrl.getHost().isBlank()) {
            throw new IllegalArgumentException("Open-URL text requires an HTTP or HTTPS URL with a host");
        }
        return new ComponentText(component.clickEvent(ClickEvent.openUrl(requiredUrl.toString())));
    }

    public String miniMessage() {
        return miniMessage;
    }

    public String legacy() {
        String current = legacy;
        if (current == null) {
            current = LEGACY.serialize(component);
            legacy = current;
        }
        return current;
    }

    public String plain() {
        String current = plain;
        if (current == null) {
            current = PLAIN.serialize(component);
            plain = current;
        }
        return current;
    }

    Object component() {
        return component;
    }

    static String legacyToMiniMessage(String input) {
        String source = ColorFormatter.translateColors(Objects.requireNonNullElse(input, ""));
        StringBuilder target = new StringBuilder(source.length() + 32);
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current != '\u00a7' || index + 1 >= source.length()) {
                target.append(current);
                continue;
            }

            char code = Character.toLowerCase(source.charAt(index + 1));
            if (code == 'x') {
                String hex = expandedHex(source, index);
                if (hex != null) {
                    target.append("<reset><#").append(hex).append('>');
                    index += 13;
                    continue;
                }
            }

            String tag = legacyTag(code);
            if (tag == null) {
                target.append(current).append(source.charAt(index + 1));
                index++;
                continue;
            }
            if (isColor(code)) {
                target.append("<reset>");
            }
            target.append('<').append(tag).append('>');
            index++;
        }
        return target.toString();
    }

    private static String expandedHex(String source, int offset) {
        if (offset + 13 >= source.length()) {
            return null;
        }
        StringBuilder hex = new StringBuilder(6);
        for (int index = 0; index < 6; index++) {
            int sectionIndex = offset + 2 + (index * 2);
            int valueIndex = sectionIndex + 1;
            if (source.charAt(sectionIndex) != '\u00a7') {
                return null;
            }
            char value = source.charAt(valueIndex);
            if (Character.digit(value, 16) < 0) {
                return null;
            }
            hex.append(Character.toLowerCase(value));
        }
        return hex.toString();
    }

    private static boolean isColor(char code) {
        return "0123456789abcdefx".indexOf(code) >= 0;
    }

    private static String legacyTag(char code) {
        return switch (code) {
            case '0' -> "black";
            case '1' -> "dark_blue";
            case '2' -> "dark_green";
            case '3' -> "dark_aqua";
            case '4' -> "dark_red";
            case '5' -> "dark_purple";
            case '6' -> "gold";
            case '7' -> "gray";
            case '8' -> "dark_gray";
            case '9' -> "blue";
            case 'a' -> "green";
            case 'b' -> "aqua";
            case 'c' -> "red";
            case 'd' -> "light_purple";
            case 'e' -> "yellow";
            case 'f' -> "white";
            case 'k' -> "obfuscated";
            case 'l' -> "bold";
            case 'm' -> "strikethrough";
            case 'n' -> "underlined";
            case 'o' -> "italic";
            case 'r' -> "reset";
            default -> null;
        };
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ComponentText that)) {
            return false;
        }
        return miniMessage.equals(that.miniMessage);
    }

    @Override
    public int hashCode() {
        return miniMessage.hashCode();
    }

    @Override
    public String toString() {
        return plain();
    }
}

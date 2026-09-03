package art.arcane.volmlib.util.plugin;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public final class ComponentMessenger {
    private static final ClassValue<Optional<Method>> RICH_MESSAGE_METHODS = new ClassValue<>() {
        @Override
        protected Optional<Method> computeValue(Class<?> type) {
            try {
                return Optional.of(type.getMethod("sendRichMessage", String.class));
            } catch (NoSuchMethodException | SecurityException ignored) {
                return Optional.empty();
            }
        }
    };

    private ComponentMessenger() {
    }

    public static void sendMarkup(CommandSender sender, String trustedMarkup) {
        send(sender, ComponentText.markup(trustedMarkup));
    }

    public static void sendLegacy(CommandSender sender, String legacyText) {
        send(sender, ComponentText.legacy(legacyText));
    }

    public static void sendSection(CommandSender sender, String sectionText) {
        send(sender, ComponentText.section(sectionText));
    }

    public static void sendLiteral(CommandSender sender, String plainText) {
        send(sender, ComponentText.literal(plainText));
    }

    public static void sendRunCommand(
            Player player,
            ComponentText message,
            String command,
            ComponentText hover
    ) {
        Player requiredPlayer = Objects.requireNonNull(player, "player");
        ComponentText requiredMessage = Objects.requireNonNull(message, "message");
        String requiredCommand = Objects.requireNonNull(command, "command");
        ComponentText requiredHover = Objects.requireNonNull(hover, "hover");
        if (!requiredCommand.startsWith("/")) {
            throw new IllegalArgumentException("Run-command text must start with /");
        }
        ComponentText interactive = requiredMessage.clickRunCommand(requiredCommand).hover(requiredHover);
        if (sendRichMessage(requiredPlayer, interactive.miniMessage())) {
            return;
        }
        BaseComponent[] components = TextComponent.fromLegacyText(requiredMessage.legacy());
        ClickEvent clickEvent = new ClickEvent(ClickEvent.Action.RUN_COMMAND, requiredCommand);
        HoverEvent hoverEvent = new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                TextComponent.fromLegacyText(requiredHover.legacy())
        );
        for (BaseComponent component : components) {
            component.setClickEvent(clickEvent);
            component.setHoverEvent(hoverEvent);
        }
        if (!sendSpigotComponents(requiredPlayer, components)) {
            requiredPlayer.sendMessage(requiredMessage.legacy());
        }
    }

    public static void sendOpenUrl(
            Player player,
            ComponentText message,
            URI url,
            ComponentText hover
    ) {
        Player requiredPlayer = Objects.requireNonNull(player, "player");
        ComponentText requiredMessage = Objects.requireNonNull(message, "message");
        URI requiredUrl = Objects.requireNonNull(url, "url");
        ComponentText requiredHover = Objects.requireNonNull(hover, "hover");
        ComponentText interactive = requiredMessage.clickOpenUrl(requiredUrl).hover(requiredHover);
        if (sendRichMessage(requiredPlayer, interactive.miniMessage())) {
            return;
        }
        BaseComponent[] components = TextComponent.fromLegacyText(requiredMessage.legacy());
        ClickEvent clickEvent = new ClickEvent(ClickEvent.Action.OPEN_URL, requiredUrl.toString());
        HoverEvent hoverEvent = new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                TextComponent.fromLegacyText(requiredHover.legacy())
        );
        for (BaseComponent component : components) {
            component.setClickEvent(clickEvent);
            component.setHoverEvent(hoverEvent);
        }
        if (!sendSpigotComponents(requiredPlayer, components)) {
            requiredPlayer.sendMessage(requiredMessage.legacy());
        }
    }

    public static void sendCopyToClipboard(
            Player player,
            ComponentText message,
            String text,
            ComponentText hover
    ) {
        Player requiredPlayer = Objects.requireNonNull(player, "player");
        ComponentText requiredMessage = Objects.requireNonNull(message, "message");
        String requiredText = Objects.requireNonNull(text, "text");
        ComponentText requiredHover = Objects.requireNonNull(hover, "hover");
        ComponentText interactive = requiredMessage.clickCopyToClipboard(requiredText).hover(requiredHover);
        if (sendRichMessage(requiredPlayer, interactive.miniMessage())) {
            return;
        }
        BaseComponent[] components = TextComponent.fromLegacyText(requiredMessage.legacy());
        ClickEvent clickEvent = new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, requiredText);
        HoverEvent hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                TextComponent.fromLegacyText(requiredHover.legacy()));
        for (BaseComponent component : components) {
            component.setClickEvent(clickEvent);
            component.setHoverEvent(hoverEvent);
        }
        if (!sendSpigotComponents(requiredPlayer, components)) {
            requiredPlayer.sendMessage(requiredMessage.legacy() + " " + requiredText);
        }
    }

    public static void send(CommandSender sender, ComponentText message) {
        CommandSender requiredSender = Objects.requireNonNull(sender, "sender");
        ComponentText requiredMessage = Objects.requireNonNull(message, "message");
        if (sendRichMessage(requiredSender, requiredMessage.miniMessage())) {
            return;
        }
        requiredSender.sendMessage(requiredSender instanceof Player
                ? requiredMessage.legacy()
                : requiredMessage.plain());
    }

    public static void sendActionBarMarkup(Player player, String trustedMarkup) {
        sendActionBar(player, ComponentText.markup(trustedMarkup));
    }

    public static void sendActionBarLegacy(Player player, String legacyText) {
        sendActionBar(player, ComponentText.legacy(legacyText));
    }

    public static void sendActionBarSection(Player player, String sectionText) {
        sendActionBar(player, ComponentText.section(sectionText));
    }

    public static void sendActionBar(Player player, ComponentText message) {
        Player requiredPlayer = Objects.requireNonNull(player, "player");
        ComponentText requiredMessage = Objects.requireNonNull(message, "message");
        requiredPlayer.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(requiredMessage.legacy()));
    }

    public static void showTitleMarkup(
            Player player,
            String trustedTitle,
            String trustedSubtitle,
            Duration fadeIn,
            Duration stay,
            Duration fadeOut) {
        showTitle(
                player,
                ComponentText.markup(trustedTitle),
                ComponentText.markup(trustedSubtitle),
                fadeIn,
                stay,
                fadeOut);
    }

    public static void showTitle(
            Player player,
            ComponentText title,
            ComponentText subtitle,
            Duration fadeIn,
            Duration stay,
            Duration fadeOut) {
        Player requiredPlayer = Objects.requireNonNull(player, "player");
        ComponentText requiredTitle = Objects.requireNonNull(title, "title");
        ComponentText requiredSubtitle = Objects.requireNonNull(subtitle, "subtitle");
        requiredPlayer.sendTitle(
                requiredTitle.legacy(),
                requiredSubtitle.legacy(),
                ticks(fadeIn),
                ticks(stay),
                ticks(fadeOut));
    }

    private static boolean sendRichMessage(CommandSender sender, String miniMessage) {
        try {
            Method method = RICH_MESSAGE_METHODS.get(sender.getClass()).orElse(null);
            if (method == null) {
                return false;
            }
            method.invoke(sender, miniMessage);
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean sendSpigotComponents(Player player, BaseComponent[] components) {
        try {
            Object spigot = player.spigot();
            Method method = spigot.getClass().getMethod("sendMessage", BaseComponent[].class);
            method.invoke(spigot, (Object) components);
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static int ticks(Duration duration) {
        Duration safeDuration = duration == null ? Duration.ZERO : duration;
        long ticks = Math.max(0L, safeDuration.toMillis() / 50L);
        return ticks > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) ticks;
    }
}

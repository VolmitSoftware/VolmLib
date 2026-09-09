package art.arcane.volmlib.util.plugin;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ComponentMessengerTest {
    @Test
    public void paperDeliveryUsesServerRichMessageMarkup() {
        CommandSender sender = mock(CommandSender.class);

        ComponentMessenger.sendMarkup(sender, "&aGreen <bold>Bold</bold>");

        ArgumentCaptor<String> markup = ArgumentCaptor.forClass(String.class);
        verify(sender).sendRichMessage(markup.capture());
        assertEquals("Green Bold", ComponentText.markup(markup.getValue()).plain());
    }

    @Test
    public void unsupportedRichDeliveryUsesLegacyFallback() {
        CommandSender sender = mock(CommandSender.class, withSettings().extraInterfaces(Player.class));
        doThrow(new UnsupportedOperationException("unsupported")).when(sender).sendRichMessage(anyString());

        ComponentMessenger.sendLegacy(sender, "&eWarning");

        verify(sender).sendMessage("\u00a7eWarning");
    }

    @Test
    public void unsupportedNonPlayerDeliveryUsesPlainFallback() {
        CommandSender sender = mock(CommandSender.class);
        doThrow(new UnsupportedOperationException("unsupported")).when(sender).sendRichMessage(anyString());

        ComponentMessenger.sendLegacy(sender, "&eWarning");

        verify(sender).sendMessage("Warning");
    }

    @Test
    public void spigotDeliveryPreservesRgbAndIndependentClickAndHoverActions() {
        CommandSender sender = mock(CommandSender.class, withSettings().extraInterfaces(Player.class));
        Player player = (Player) sender;
        Player.Spigot spigot = mock(Player.Spigot.class);
        when(player.spigot()).thenReturn(spigot);
        doThrow(new UnsupportedOperationException("unsupported")).when(sender).sendRichMessage(anyString());
        ComponentText run = ComponentText.markup("<gradient:#E9A06B:#F6D58A>Language</gradient>")
                .clickRunCommand("/siphon language self")
                .hover(ComponentText.markup("<#72D6C5>Select a personal language"));
        ComponentText copy = ComponentText.literal(" Copy path").clickCopyToClipboard("plugins/Siphon/debug/report.txt");

        ComponentMessenger.send(sender, run.append(copy));

        ArgumentCaptor<BaseComponent[]> components = ArgumentCaptor.forClass(BaseComponent[].class);
        verify(spigot).sendMessage(components.capture());
        String json = ComponentSerializer.toString(components.getValue());
        assertEquals("Language Copy path", BaseComponent.toPlainText(components.getValue()));
        assertTrue(json.toLowerCase(Locale.ROOT).contains("#e9a06b"));
        assertTrue(json.toLowerCase(Locale.ROOT).contains("#f6d58a"));
        assertTrue(json.contains("\"action\":\"run_command\""));
        assertTrue(json.contains("\"value\":\"/siphon language self\""));
        assertTrue(json.contains("\"action\":\"copy_to_clipboard\""));
        assertTrue(json.contains("plugins/Siphon/debug/report.txt"));
        assertTrue(json.contains("\"action\":\"show_text\""));
        assertTrue(json.contains("Select a personal language"));
        assertTrue(json.toLowerCase(Locale.ROOT).contains("#72d6c5"));
    }

    @Test
    public void missingOptionalJsonSerializerRetainsLegacyDelivery() throws ReflectiveOperationException {
        CommandSender sender = mock(CommandSender.class, withSettings().extraInterfaces(Player.class));
        doThrow(new UnsupportedOperationException("unsupported")).when(sender).sendRichMessage(anyString());
        MissingSerializerLoader loader = new MissingSerializerLoader();
        Class<?> messenger = Class.forName(ComponentMessenger.class.getName(), true, loader);

        messenger.getMethod("sendLegacy", CommandSender.class, String.class).invoke(null, sender, "&eWarning");

        assertTrue(loader.serializerRequested);
        verify(sender).sendMessage("\u00a7eWarning");
    }

    @Test
    public void runCommandDeliveryRetainsTheInteractiveAction() {
        CommandSender sender = mock(CommandSender.class, withSettings().extraInterfaces(Player.class));
        Player player = (Player) sender;

        ComponentMessenger.sendRunCommand(
                player,
                ComponentText.markup("&aEnglish"),
                "/plugin language en_US",
                ComponentText.literal("Select English")
        );

        ArgumentCaptor<String> markup = ArgumentCaptor.forClass(String.class);
        verify(sender).sendRichMessage(markup.capture());
        assertEquals("English", ComponentText.markup(markup.getValue()).plain());
        assertTrue(markup.getValue().contains("click:run_command"));
        assertTrue(markup.getValue().contains("hover:show_text"));
        assertTrue(markup.getValue().contains("/plugin language en_US"));
    }

    @Test
    public void runCommandDeliveryRejectsChatText() {
        assertThrows(IllegalArgumentException.class, () -> ComponentMessenger.sendRunCommand(
                mock(Player.class),
                ComponentText.literal("Option"),
                "not-a-command",
                ComponentText.literal("Hover")
        ));
    }

    @Test
    public void clipboardDeliveryRetainsTheInteractiveAction() {
        CommandSender sender = mock(CommandSender.class, withSettings().extraInterfaces(Player.class));

        ComponentMessenger.sendCopyToClipboard((Player) sender, ComponentText.literal("Copy"),
                "https://mclo.gs/Ab12", ComponentText.literal("Copy report link"));

        ArgumentCaptor<String> markup = ArgumentCaptor.forClass(String.class);
        verify(sender).sendRichMessage(markup.capture());
        assertTrue(markup.getValue().contains("click:copy_to_clipboard"));
        assertTrue(markup.getValue().contains("https://mclo.gs/Ab12"));
    }

    @Test
    public void clipboardDeliveryRetainsSpigotClickEvent() {
        CommandSender sender = mock(CommandSender.class, withSettings().extraInterfaces(Player.class));
        Player player = (Player) sender;
        Player.Spigot spigot = mock(Player.Spigot.class);
        when(player.spigot()).thenReturn(spigot);
        doThrow(new UnsupportedOperationException("unsupported")).when(sender).sendRichMessage(anyString());

        ComponentMessenger.sendCopyToClipboard(player, ComponentText.literal("Copy"),
                "https://mclo.gs/Ab12", ComponentText.literal("Copy report link"));

        ArgumentCaptor<BaseComponent[]> components = ArgumentCaptor.forClass(BaseComponent[].class);
        verify(spigot).sendMessage(components.capture());
        assertEquals(ClickEvent.Action.COPY_TO_CLIPBOARD,
                components.getValue()[0].getClickEvent().getAction());
        assertEquals("https://mclo.gs/Ab12", components.getValue()[0].getClickEvent().getValue());
    }

    @Test
    public void openUrlDeliveryRetainsTheInteractiveAction() {
        CommandSender sender = mock(CommandSender.class, withSettings().extraInterfaces(Player.class));
        Player player = (Player) sender;

        ComponentMessenger.sendOpenUrl(
                player,
                ComponentText.markup("&aOpen report"),
                URI.create("https://mclo.gs/Ab12"),
                ComponentText.literal("Open the public report")
        );

        ArgumentCaptor<String> markup = ArgumentCaptor.forClass(String.class);
        verify(sender).sendRichMessage(markup.capture());
        assertEquals("Open report", ComponentText.markup(markup.getValue()).plain());
        assertTrue(markup.getValue().contains("click:open_url"));
        assertTrue(markup.getValue().contains("hover:show_text"));
        assertTrue(markup.getValue().contains("https://mclo.gs/Ab12"));
    }

    private static final class MissingSerializerLoader extends ClassLoader {
        private boolean serializerRequested;

        private MissingSerializerLoader() {
            super(ComponentMessengerTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("net.kyori.adventure.text.serializer.gson.")) {
                serializerRequested = true;
                throw new ClassNotFoundException(name);
            }
            if (!name.startsWith(ComponentMessenger.class.getName()) && !name.equals(ComponentText.class.getName())) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try (InputStream input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (input == null) {
                            throw new ClassNotFoundException(name);
                        }
                        byte[] bytes = input.readAllBytes();
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException exception) {
                        throw new ClassNotFoundException(name, exception);
                    }
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }
}

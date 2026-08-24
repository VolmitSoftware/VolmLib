package art.arcane.volmlib.util.plugin;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.verify;

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
}

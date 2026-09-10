package art.arcane.volmlib.util.config;

import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.google.gson.JsonPrimitive;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class BukkitConfigEditorTest {
    @Test
    public void closesAnInventoryOpenedWhileTheEditorIsShuttingDown() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        Player player = mock(Player.class, withSettings().extraInterfaces(Entity.class, CommandSender.class));
        Entity entity = (Entity) player;
        Inventory inventory = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);
        ItemFactory itemFactory = mock(ItemFactory.class);
        AtomicReference<BukkitConfigEditor> editor = new AtomicReference<>();
        when(plugin.getName()).thenReturn("Example");
        when(plugin.getServer()).thenReturn(server);
        when(plugin.isEnabled()).thenReturn(true);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        when(server.createInventory(any(InventoryHolder.class), eq(54), anyString())).thenReturn(inventory);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(((CommandSender) player).hasPermission("example.config")).thenReturn(true);
        when(player.getOpenInventory()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(inventory);
        when(player.openInventory(inventory)).thenAnswer(invocation -> {
            editor.get().close();
            return view;
        });
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            bukkit.when(Bukkit::getItemFactory).thenReturn(itemFactory);
            scheduler.when(() -> FoliaScheduler.runEntity(eq(plugin), eq(entity), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        Runnable opening = invocation.getArgument(2);
                        opening.run();
                        return true;
                    });
            editor.set(BukkitConfigEditor.register(plugin, new BukkitConfigEditor.Options(
                    () -> ConfigEditorDocument.fromToml("enabled = true"),
                    edit -> edit.original(),
                    new BukkitConfigEditor.Presentation("example.config", DirectorMiniMenu.Theme.irisGreen(),
                            DirectorTextResolver.ENGLISH))));

            editor.get().open((CommandSender) player);

            verify(player).closeInventory();
        } finally {
            if (editor.get() != null) {
                editor.get().close();
            }
        }
    }

    @Test
    public void menuSortsSectionsButKeepsRepeatedRulesInTheirOriginalOrder() throws IOException {
        StringBuilder source = new StringBuilder("z = true\na = 1\n");
        for (int index = 0; index < 12; index++) {
            source.append("[[drops]]\nname = \"rule ").append(index).append("\"\n");
        }
        ConfigEditorDocument document = ConfigEditorDocument.fromToml(source.toString());
        ArrayList<String> rootNames = new ArrayList<>();
        for (ConfigEditorDocument.Entry entry : BukkitConfigEditor.orderedEntries(document, List.of())) {
            rootNames.add(entry.name());
        }
        List<ConfigEditorDocument.Entry> drops = BukkitConfigEditor.orderedEntries(document, List.of("drops"));

        assertEquals(List.of("a", "drops", "z"), rootNames);
        assertEquals("rule 2", drops.get(2).value().getAsJsonObject().get("name").getAsString());
        assertEquals("rule 10", drops.get(10).value().getAsJsonObject().get("name").getAsString());
        assertEquals(List.of("drops", "10"), drops.get(10).path());
    }

    @Test
    public void valuePreviewBoundsLoreWithoutChangingTheEditedValue() throws IOException {
        JsonPrimitive value = new JsonPrimitive("line one\nline two\r" + "x".repeat(140));

        String preview = BukkitConfigEditor.preview(value);

        assertEquals(120, preview.length());
        assertTrue(preview.startsWith("line one\\nline two\\r"));
        assertTrue(preview.endsWith("..."));
        assertFalse(preview.contains("\n"));
        assertTrue(value.getAsString().contains("\n"));
        assertEquals("drops / 10 / chance", BukkitConfigEditor.displayPath(List.of("drops", "10", "chance")));
        assertEquals("/", BukkitConfigEditor.displayPath(List.of()));
    }

    @Test
    public void sharedFeedbackCatalogHasValidCompletePlaceholderDefinitions() {
        MessageCatalog catalog = MessageCatalog.builder("en_US").addAll(BukkitConfigMessages.keys()).build();
        assertEquals(BukkitConfigMessages.keys().size(), catalog.ids().size());
        for (MessageKey definition : catalog.keys()) {
            TextKey key = (TextKey) definition;
            MessageArgs.Builder arguments = MessageArgs.builder();
            for (String placeholder : key.placeholders()) {
                arguments.untrusted(placeholder, "example");
            }
            String rendered = DirectorTextResolver.ENGLISH.resolve(key, arguments.build());
            assertFalse(key.id(), rendered.isBlank());
            assertFalse(key.id(), rendered.contains("{"));
        }
    }
}

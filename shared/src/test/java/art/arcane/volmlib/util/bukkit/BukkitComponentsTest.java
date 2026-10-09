package art.arcane.volmlib.util.bukkit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BukkitComponentsTest {
    @Test
    public void paperMetadataKeepsTranslationComponentsAndTheirStyles() {
        ItemMeta meta = mock(ItemMeta.class);
        Component name = Component.translatable("item.minecraft.player_head").color(TextColor.color(0x72D6C5));
        List<Component> lore = List.of(Component.translatable("item.minecraft.diamond"));
        when(meta.displayName()).thenReturn(name);
        when(meta.lore()).thenReturn(lore);
        assertSame(name, BukkitComponents.displayName(meta));
        assertSame(lore, BukkitComponents.lore(meta));
        BukkitComponents.displayName(meta, name);
        BukkitComponents.lore(meta, lore);
        verify(meta).displayName(name);
        verify(meta).lore(lore);
    }

    @Test
    public void serverRejectionIsNotHiddenByLegacyFallback() {
        ItemMeta meta = mock(ItemMeta.class);
        Component name = Component.text("Name");
        IllegalArgumentException rejected = new IllegalArgumentException("invalid component");
        doThrow(rejected).when(meta).displayName(name);
        assertSame(rejected, assertThrows(IllegalArgumentException.class, () -> BukkitComponents.displayName(meta, name)));
    }
}

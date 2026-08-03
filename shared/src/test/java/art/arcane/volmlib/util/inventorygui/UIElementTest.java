package art.arcane.volmlib.util.inventorygui;

import art.arcane.volmlib.util.data.MaterialBlock;
import org.bukkit.Material;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UIElementTest {
    @Test
    public void damageableIcon_isUndamagedByDefault() {
        UIElement element = new UIElement("icon").setMaterial(damageable((short) 100));

        assertEquals(1D, element.getProgress(), 0D);
        assertEquals(0, element.getEffectiveDurability());
    }

    @Test
    public void damageableIcon_mapsExplicitProgressToDurability() {
        UIElement element = new UIElement("bar").setMaterial(damageable((short) 100));

        element.setProgress(0D);
        assertEquals(99, element.getEffectiveDurability());

        element.setProgress(0.5D);
        assertEquals(50, element.getEffectiveDurability());
    }

    @Test
    public void nonDamageableIcon_hasNoDurability() {
        UIElement element = new UIElement("icon").setMaterial(damageable((short) 0));

        element.setProgress(0D);
        assertEquals(0, element.getEffectiveDurability());
    }

    @Test
    public void middleClick_dispatchesToItsOwnCallback() {
        AtomicInteger middle = new AtomicInteger();
        AtomicInteger left = new AtomicInteger();
        UIElement element = new UIElement("icon");
        element.onMiddleClick(e -> middle.incrementAndGet());
        element.onLeftClick(e -> left.incrementAndGet());

        assertSame(element, element.call(ElementEvent.MIDDLE, element));
        assertEquals(1, middle.get());
        assertEquals(0, left.get());

        element.call(ElementEvent.LEFT, element);
        assertEquals(1, middle.get());
        assertEquals(1, left.get());
    }

    @Test
    public void middleClick_withoutCallbackIsSilent() {
        UIElement element = new UIElement("icon");

        assertSame(element, element.call(ElementEvent.MIDDLE, element));
    }

    // Material.getMaxDurability() reads the paper registry, which needs a running server
    private static MaterialBlock damageable(short maxDurability) {
        Material material = mock(Material.class);
        when(material.getMaxDurability()).thenReturn(maxDurability);
        MaterialBlock block = mock(MaterialBlock.class);
        when(block.getMaterial()).thenReturn(material);
        return block;
    }
}

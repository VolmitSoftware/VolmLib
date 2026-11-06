package art.arcane.volmlib.util.director.theme;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DirectorThemesTest {
    @Test
    public void echelonRetainsItsIndigoPalette() {
        DirectorTheme theme = DirectorThemes.forProduct(DirectorProduct.ECHELON);
        assertEquals("#4f63e6", theme.getPrimaryHex());
        assertEquals("#2f3bb0", theme.getSecondaryHex());
        assertEquals("minecraft:entity.experience_orb.pickup", theme.getSuccessSound());
    }

    @Test
    public void dreadRetainsItsEmberAndGoldPalette() {
        DirectorTheme theme = DirectorThemes.forProduct(DirectorProduct.DREAD);
        assertEquals("#FF725E", theme.getPrimaryHex());
        assertEquals("#FFD166", theme.getSecondaryHex());
        assertEquals("minecraft:block.note_block.chime", theme.getSuccessSound());
        assertEquals("minecraft:block.note_block.bass", theme.getErrorSound());
    }

    @Test
    public void skyPrimeKeepsItsCyanAndWhitePalette() {
        DirectorTheme theme = DirectorThemes.forProduct(DirectorProduct.SKYPRIME);
        assertEquals("#00d8e8", theme.getPrimaryHex());
        assertEquals("#087e8b", theme.getSecondaryHex());
        assertEquals("#ffffff", theme.getAccentHex());
    }

    @Test
    public void brimstoneKeepsItsTntRedAndIvoryPalette() {
        DirectorTheme theme = DirectorThemes.forProduct(DirectorProduct.BRIMSTONE);
        assertEquals("#ff6b6b", theme.getPrimaryHex());
        assertEquals("#9e2a2a", theme.getSecondaryHex());
        assertEquals("#fff1d0", theme.getAccentHex());
    }

    @Test
    public void everyProductHasAMatchingTheme() {
        for (DirectorProduct product : DirectorProduct.values()) {
            DirectorTheme theme = DirectorThemes.forProduct(product);

            assertNotNull(theme);
            assertEquals(product, theme.getProduct());
        }
    }
}

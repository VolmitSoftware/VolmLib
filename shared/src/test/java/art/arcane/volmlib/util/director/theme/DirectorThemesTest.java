package art.arcane.volmlib.util.director.theme;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DirectorThemesTest {
    @Test
    public void everyProductHasAMatchingTheme() {
        for (DirectorProduct product : DirectorProduct.values()) {
            DirectorTheme theme = DirectorThemes.forProduct(product);

            assertNotNull(theme);
            assertEquals(product, theme.getProduct());
        }
    }
}

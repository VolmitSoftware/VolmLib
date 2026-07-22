package art.arcane.volmlib.util.localization;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class VolmitLocalesTest {
    @Test
    public void exposesTheCanonicalImmutableLocaleSet() {
        assertEquals(18, VolmitLocales.all().size());
        assertEquals(17, VolmitLocales.nonEnglish().size());
        assertEquals(VolmitLocales.ENGLISH, VolmitLocales.all().getFirst());
        assertTrue(VolmitLocales.isBundled("ja-JP"));
        assertTrue(VolmitLocales.isBundled(" zh_TW "));
        assertFalse(VolmitLocales.isBundled(null));
        assertFalse(VolmitLocales.isBundled("en_GB"));
        assertThrows(UnsupportedOperationException.class, () -> VolmitLocales.all().add("en_GB"));
        assertThrows(UnsupportedOperationException.class, () -> VolmitLocales.nonEnglish().clear());
    }

    @Test
    public void convertsBundledLocalesToMinecraftResourceCodes() {
        assertEquals("en_us", VolmitLocales.minecraftCode("en_US"));
        assertEquals("ja_jp", VolmitLocales.minecraftCode("ja-JP"));
        assertEquals("zh_tw", VolmitLocales.minecraftCode("zh_TW"));
        assertThrows(IllegalArgumentException.class, () -> VolmitLocales.minecraftCode("en_GB"));
        assertThrows(IllegalArgumentException.class, () -> VolmitLocales.minecraftCode(null));
    }
}

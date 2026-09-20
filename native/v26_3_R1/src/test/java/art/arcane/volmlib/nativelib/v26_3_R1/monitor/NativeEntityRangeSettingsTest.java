package art.arcane.volmlib.nativelib.v26_3_R1.monitor;

import art.arcane.volmlib.nativelib.monitor.EntityRangeSettings.Range;
import org.junit.Test;
import org.spigotmc.SpigotWorldConfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

public class NativeEntityRangeSettingsTest {
    @Test
    public void settingsMutateTheWorldConfigurationWithoutChangingOtherRanges() {
        SpigotWorldConfig config = mock(SpigotWorldConfig.class);
        config.animalActivationRange = 64;
        config.animalTrackingRange = 96;
        config.miscTrackingRange = 80;
        config.tickInactiveVillagers = true;
        NativeEntityRangeSettings settings = new NativeEntityRangeSettings(config);

        settings.range(Range.ACTIVATION_ANIMAL, 32);
        settings.range(Range.TRACKING_MISC, 40);
        settings.tickInactiveVillagers(false);

        assertEquals(32, config.animalActivationRange);
        assertEquals(96, config.animalTrackingRange);
        assertEquals(40, config.miscTrackingRange);
        assertFalse(config.tickInactiveVillagers);
        config.animalActivationRange = 48;
        assertEquals(48, settings.range(Range.ACTIVATION_ANIMAL));
        settings.tickInactiveVillagers(true);
        assertTrue(settings.tickInactiveVillagers());
        assertFalse(settings.supportedRanges().contains(Range.TRACKING_ITEM));
        assertThrows(UnsupportedOperationException.class, () -> settings.range(Range.TRACKING_ITEM));
    }
}

package art.arcane.volmlib.util.bukkit.papi;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

public class PlaceholderValuesTest {
    @Test
    public void count_neverEmitsAGroupingSeparator() {
        assertEquals("1234567", PlaceholderValues.count(1234567L));
        assertEquals("-1234567", PlaceholderValues.count(-1234567L));
        assertEquals("0", PlaceholderValues.count(0L));
        assertEquals("9223372036854775807", PlaceholderValues.count(Long.MAX_VALUE));
    }

    @Test
    public void count_smallValuesAreInternedSoTheHotPathAllocatesNothing() {
        assertSame(PlaceholderValues.count(0L), PlaceholderValues.count(0L));
        assertSame(PlaceholderValues.count(1023L), PlaceholderValues.count(1023L));
    }

    @Test
    public void num_isTwoDecimalsWithADotAndNoGrouping() {
        assertEquals("0.00", PlaceholderValues.num(0.0D));
        assertEquals("19.99", PlaceholderValues.num(19.99D));
        assertEquals("12.33", PlaceholderValues.num(12.333333333333334D));
        assertEquals("1234567.89", PlaceholderValues.num(1234567.891D));
        assertEquals("-4.50", PlaceholderValues.num(-4.5D));
        assertEquals("0.00", PlaceholderValues.num(-0.001D));
        assertEquals("2.00", PlaceholderValues.num(1.995D));
    }

    @Test
    public void num_nonFiniteIsUnavailableRatherThanNaNText() {
        assertEquals(PlaceholderValues.UNAVAILABLE, PlaceholderValues.num(Double.NaN));
        assertEquals(PlaceholderValues.UNAVAILABLE, PlaceholderValues.num(Double.POSITIVE_INFINITY));
        assertEquals(PlaceholderValues.UNAVAILABLE, PlaceholderValues.num(Double.NEGATIVE_INFINITY));
    }

    @Test
    public void percent_isTheNumberOnlyWithNoPercentCharacter() {
        assertEquals("53.20", PlaceholderValues.percent(0.532D));
        assertEquals("100.00", PlaceholderValues.percent(1.0D));
        assertFalse(PlaceholderValues.percent(0.532D).contains("%"));
    }

    @Test
    public void formattedOutputCarriesNoGroupingSeparatorAndNoPercentSign() {
        Random random = new Random(20260725L);

        for (int index = 0; index < 20000; index++) {
            double value = (random.nextDouble() - 0.5D) * Math.pow(10.0D, random.nextInt(12));
            long counted = random.nextLong();

            assertNoGroupingOrPercent(PlaceholderValues.num(value));
            assertNoGroupingOrPercent(PlaceholderValues.percent(value));
            assertNoGroupingOrPercent(PlaceholderValues.count(counted));
        }
    }

    @Test
    public void bool_isLowercaseAscii() {
        assertEquals("true", PlaceholderValues.bool(true));
        assertEquals("false", PlaceholderValues.bool(false));
    }

    @Test
    public void text_stripsPercentAndLegacyColourAndFallsBackToUnavailable() {
        assertEquals(PlaceholderValues.UNAVAILABLE, PlaceholderValues.text(null));
        assertEquals(PlaceholderValues.UNAVAILABLE, PlaceholderValues.text(""));
        assertEquals(PlaceholderValues.UNAVAILABLE, PlaceholderValues.text("\u00A7a\u00A7l"));
        assertEquals("Hot Desert Dunes", PlaceholderValues.text("Hot Desert Dunes"));
        assertSame("Hot Desert Dunes", PlaceholderValues.text("Hot Desert Dunes"));
        assertEquals("Mining", PlaceholderValues.text("\u00A7aMining"));
        assertEquals("100 done", PlaceholderValues.text("100% done"));
        assertEquals("vault_eco_balance", PlaceholderValues.text("%vault_eco_balance%"));
        assertEquals("x", PlaceholderValues.text("x\u00A7"));
    }

    private static void assertNoGroupingOrPercent(String formatted) {
        assertFalse(formatted, formatted.indexOf(',') >= 0);
        assertFalse(formatted, formatted.indexOf('%') >= 0);
    }
}

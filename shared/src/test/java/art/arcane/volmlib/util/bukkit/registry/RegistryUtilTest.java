package art.arcane.volmlib.util.bukkit.registry;

import org.bukkit.NamespacedKey;
import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class RegistryUtilTest {
    @Test
    public void enumFieldKeysRemainAsciiUnderTurkishLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertSame(EnumValue.ITEM, RegistryUtil.findByEnum(EnumValue.class, NamespacedKey.minecraft("item")));
            assertThrows(IllegalArgumentException.class, () -> RegistryUtil.findByEnum(EnumValue.class, NamespacedKey.minecraft("missing")));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    public void interfaceFieldKeysRemainAsciiUnderTurkishLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertSame(InterfaceValue.ITEM, RegistryUtil.findByEnum(InterfaceValue.class, NamespacedKey.minecraft("item")));
        } finally {
            Locale.setDefault(previous);
        }
    }

    public enum EnumValue {
        ITEM
    }

    public interface InterfaceValue {
        InterfaceValue ITEM = new InterfaceEntry();
    }

    public record InterfaceEntry() implements InterfaceValue {
    }
}

package art.arcane.volmlib.nativelib.item;

import java.util.List;
import java.util.Map;

public interface NativeItemRecipe {
    String typeKey();
    int amount();
    List<? extends Enchantment> enchantments();
    List<? extends Attribute> attributes();
    double chance();
    boolean unbreakable();
    List<String> itemFlags();
    Integer customModel();
    double durability();
    String leatherColor();
    String dyeColor();
    String displayName();
    List<String> lore();
    Map<String, Object> customNbt();

    interface Enchantment {
        String name();
        double chance();
        int level();
    }

    interface Attribute {
        String attribute();
        String name();
        String operation();
        double chance();
        double amount();
    }
}

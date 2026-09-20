package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.List;

public final class NativeTaggedItems {
    private NativeTaggedItems() {
    }

    public static NativeItemStack create(Options options) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(options.itemKey())));
        stack.set(DataComponents.CUSTOM_NAME, options.name().component());
        stack.set(DataComponents.LORE, new ItemLore(options.lore().stream().map(NativeCommandText::component).toList()));
        if (options.unbreakable()) {
            stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
            stack.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.UNBREAKABLE, true));
        }
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, options.glint());
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(options.flag(), true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return new NativeItemStack(stack);
    }

    public record Options(String itemKey, NativeCommandText name, List<NativeCommandText> lore,
                          String flag, boolean unbreakable, boolean glint) {
    }
}

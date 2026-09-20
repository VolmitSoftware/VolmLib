package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.List;

public final class NativeItemStack {
    private final ItemStack stack;

    NativeItemStack(ItemStack stack) {
        this.stack = stack;
    }

    public static NativeItemStack of(ItemStack stack) {
        return new NativeItemStack(stack);
    }

    public static void appendTo(Collection<ItemStack> stacks, List<NativeItemStack> items) {
        for (NativeItemStack item : items) {
            stacks.add(item.stack);
        }
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    public NativeItemStack copy() {
        return new NativeItemStack(stack.copy());
    }

    public NativeItemStack copyWithCount(int count) {
        return new NativeItemStack(stack.copyWithCount(count));
    }

    public int count() {
        return stack.getCount();
    }

    public int maxStackSize() {
        return stack.getMaxStackSize();
    }

    public void grow(int amount) {
        stack.grow(amount);
    }

    public void shrink(int amount) {
        stack.shrink(amount);
    }

    public static boolean sameItemAndComponents(NativeItemStack first, NativeItemStack second) {
        return ItemStack.isSameItemSameComponents(first.stack, second.stack);
    }

    public ItemStack stack() {
        return stack;
    }
}

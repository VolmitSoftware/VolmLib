package art.arcane.volmlib.util.inventorygui;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.data.MaterialBlock;
import art.arcane.volmlib.util.scheduling.Callback;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class UIElement implements Element {
    private final String id;
    private final KList<String> lore;
    private MaterialBlock material;
    private boolean enchanted;
    private String name;
    private double progress;
    private boolean bg;
    private Callback<Element> eLeft;
    private Callback<Element> eRight;
    private Callback<Element> eShiftLeft;
    private Callback<Element> eShiftRight;
    private Callback<Element> eDraggedInto;
    private Callback<Element> eOtherDraggedInto;
    private int count;

    public UIElement(String id) {
        this.id = id;
        lore = new KList<>();
        enchanted = false;
        count = 1;
        material = new MaterialBlock(Material.AIR);
    }

    protected void reportError(Throwable e) {
        e.printStackTrace();
    }

    @Override
    public MaterialBlock getMaterial() {
        return material;
    }

    @Override
    public UIElement setMaterial(MaterialBlock material) {
        this.material = material;
        return this;
    }

    public Double clip(double value, double min, double max) {
        return Math.min(max, Math.max(min, value));
    }

    @Override
    public boolean isEnchanted() {
        return enchanted;
    }

    @Override
    public UIElement setEnchanted(boolean enchanted) {
        this.enchanted = enchanted;
        return this;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public UIElement setName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public KList<String> getLore() {
        return lore;
    }

    @Override
    public UIElement onLeftClick(Callback<Element> clicked) {
        eLeft = clicked;
        return this;
    }

    @Override
    public UIElement onRightClick(Callback<Element> clicked) {
        eRight = clicked;
        return this;
    }

    @Override
    public UIElement onShiftLeftClick(Callback<Element> clicked) {
        eShiftLeft = clicked;
        return this;
    }

    @Override
    public UIElement onShiftRightClick(Callback<Element> clicked) {
        eShiftRight = clicked;
        return this;
    }

    @Override
    public UIElement onDraggedInto(Callback<Element> into) {
        eDraggedInto = into;
        return this;
    }

    @Override
    public UIElement onOtherDraggedInto(Callback<Element> other) {
        eOtherDraggedInto = other;
        return this;
    }

    @Override
    public Element call(ElementEvent event, Element context) {
        try {
            switch (event) {
                case DRAG_INTO -> {
                    eDraggedInto.run(context);
                    return this;
                }
                case LEFT -> {
                    eLeft.run(context);
                    return this;
                }
                case OTHER_DRAG_INTO -> {
                    eOtherDraggedInto.run(context);
                    return this;
                }
                case RIGHT -> {
                    eRight.run(context);
                    return this;
                }
                case SHIFT_LEFT -> {
                    eShiftLeft.run(context);
                    return this;
                }
                case SHIFT_RIGHT -> {
                    eShiftRight.run(context);
                    return this;
                }
            }
        } catch (Throwable e) {
            reportError(e);
        }

        return this;
    }

    @Override
    public Element addLore(String loreLine) {
        getLore().add(loreLine);
        return this;
    }

    @Override
    public Element setBackground(boolean bg) {
        this.bg = bg;
        return this;
    }

    @Override
    public boolean isBackgrond() {
        return bg;
    }

    @Override
    public Element setCount(int c) {
        count = clip(c, 1, 64).intValue();
        return this;
    }

    @Override
    public int getCount() {
        return count;
    }

    @SuppressWarnings("deprecation")
    @Override
    public ItemStack computeItemStack() {
        try {
            ItemStack is = new ItemStack(getMaterial().getMaterial(), getCount(), getEffectiveDurability());
            ItemMeta im = is.getItemMeta();
            im.setDisplayName(getName());
            im.setLore(getLore().copy());

            if (isEnchanted()) {
                im.addEnchant(Enchantment.FIRE_ASPECT, 1, true);
            }

            is.setItemMeta(im);
            return is;
        } catch (Throwable e) {
            reportError(e);
        }

        return null;
    }

    @Override
    public Element setProgress(double progress) {
        this.progress = clip(progress, 0D, 1D);
        return this;
    }

    @Override
    public double getProgress() {
        return progress;
    }

    @Override
    public short getEffectiveDurability() {
        if (getMaterial().getMaterial().getMaxDurability() == 0) {
            return 0;
        } else {
            int prog = (int) ((double) getMaterial().getMaterial().getMaxDurability() * (1D - getProgress()));
            return clip(prog, 1, (getMaterial().getMaterial().getMaxDurability() - 1)).shortValue();
        }
    }
}

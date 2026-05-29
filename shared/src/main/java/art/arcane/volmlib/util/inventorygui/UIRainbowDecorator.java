package art.arcane.volmlib.util.inventorygui;

import art.arcane.volmlib.util.data.MaterialBlock;
import org.bukkit.Material;

public class UIRainbowDecorator implements WindowDecorator {
    private static final Material[] CYCLE = new Material[] {
        Material.WHITE_STAINED_GLASS_PANE,
        Material.ORANGE_STAINED_GLASS_PANE,
        Material.MAGENTA_STAINED_GLASS_PANE,
        Material.LIGHT_BLUE_STAINED_GLASS_PANE,
        Material.YELLOW_STAINED_GLASS_PANE,
        Material.LIME_STAINED_GLASS_PANE,
        Material.PINK_STAINED_GLASS_PANE,
        Material.GRAY_STAINED_GLASS_PANE,
        Material.LIGHT_GRAY_STAINED_GLASS_PANE,
        Material.CYAN_STAINED_GLASS_PANE,
        Material.PURPLE_STAINED_GLASS_PANE,
        Material.BLUE_STAINED_GLASS_PANE,
        Material.BROWN_STAINED_GLASS_PANE,
        Material.GREEN_STAINED_GLASS_PANE,
        Material.RED_STAINED_GLASS_PANE,
        Material.BLACK_STAINED_GLASS_PANE
    };

    @Override
    public Element onDecorateBackground(Window window, int position, int row) {
        int real = window.getRealPosition(position, row);
        Material material = CYCLE[Math.floorMod(real, CYCLE.length)];
        return new UIElement("bh").setBackground(true).setName(" ").setMaterial(new MaterialBlock(material));
    }
}

package art.arcane.volmlib.util.inventorygui;

import art.arcane.volmlib.util.data.MaterialBlock;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

public class UIPaneDecorator extends UIStaticDecorator {
    public UIPaneDecorator(Material material) {
        super(buildElement(material));
    }

    public UIPaneDecorator(String paneId) {
        super(buildElement(resolve(paneId)));
    }

    public UIPaneDecorator() {
        this(Material.GRAY_STAINED_GLASS_PANE);
    }

    private static Element buildElement(Material material) {
        Material resolved = material == null ? Material.GRAY_STAINED_GLASS_PANE : material;
        return new UIElement("c").setName(" ").setMaterial(new MaterialBlock(resolved));
    }

    private static Material resolve(String paneId) {
        if (paneId == null || paneId.isEmpty()) {
            return Material.GRAY_STAINED_GLASS_PANE;
        }
        Material material = Registry.MATERIAL.get(NamespacedKey.minecraft(paneId.toLowerCase()));
        if (material == null) {
            material = Material.matchMaterial(paneId);
        }
        return material == null ? Material.GRAY_STAINED_GLASS_PANE : material;
    }
}

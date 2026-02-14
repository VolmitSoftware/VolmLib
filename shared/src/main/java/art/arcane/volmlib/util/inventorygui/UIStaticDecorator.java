package art.arcane.volmlib.util.inventorygui;

import art.arcane.volmlib.util.data.MaterialBlock;
import org.bukkit.Material;

@SuppressWarnings("ClassCanBeRecord")
public class UIStaticDecorator implements WindowDecorator {
    private final Element element;

    public UIStaticDecorator(Element element) {
        this.element = element == null ? new UIElement("bg").setMaterial(new MaterialBlock(Material.AIR)) : element;
    }

    @Override
    public Element onDecorateBackground(Window window, int position, int row) {
        return element;
    }
}

package art.arcane.volmlib.util.inventorygui;

import art.arcane.volmlib.util.scheduling.Callback;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface Window {
    WindowDecorator getDecorator();

    Window setDecorator(WindowDecorator decorator);

    WindowResolution getResolution();

    Window setResolution(WindowResolution resolution);

    Window clearElements();

    Window close();

    Window open();

    Window callClosed();

    Window updateInventory();

    ItemStack computeItemStack(int viewportSlot);

    int getLayoutRow(int viewportSlottedPosition);

    int getLayoutPosition(int viewportSlottedPosition);

    int getRealLayoutPosition(int viewportSlottedPosition);

    int getRealPosition(int position, int row);

    int getRow(int realPosition);

    int getPosition(int realPosition);

    boolean isVisible();

    Window setVisible(boolean visible);

    int getViewportPosition();

    Window setViewportPosition(int position);

    int getViewportSlots();

    int getMaxViewportPosition();

    Window scroll(int direction);

    int getViewportHeight();

    Window setViewportHeight(int height);

    String getTitle();

    Window setTitle(String title);

    default String getTag() {
        return null;
    }

    default Window setTag(String tag) {
        return this;
    }

    boolean hasElement(int position, int row);

    Window setElement(int position, int row, Element e);

    Element getElement(int position, int row);

    Player getViewer();

    Window reopen();

    Window onClosed(Callback<Window> window);
}

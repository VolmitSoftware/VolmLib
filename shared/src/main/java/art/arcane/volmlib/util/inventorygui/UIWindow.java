package art.arcane.volmlib.util.inventorygui;

import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.volmlib.util.scheduling.Callback;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class UIWindow implements Window, Listener {
    private static final Map<UUID, UIWindow> ACTIVE_WINDOWS = new ConcurrentHashMap<>();
    private final JavaPlugin plugin;
    private final Player viewer;
    private final KMap<Integer, Element> elements;
    private WindowDecorator decorator;
    private Callback<Window> eClose;
    private WindowResolution resolution;
    private String title;
    private boolean visible;
    private int viewportPosition;
    private int viewportSize;
    private int highestRow;
    private Inventory inventory;
    private int clickcheck;
    private boolean doubleclicked;
    private String tag;

    public UIWindow(JavaPlugin plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.elements = new KMap<>();
        this.clickcheck = 0;
        this.doubleclicked = false;
        setTitle("");
        setDecorator(new UIVoidDecorator());
        setResolution(WindowResolution.W9_H6);
        setViewportHeight(clip(3, 1, getResolution().getMaxHeight()).intValue());
        setViewportPosition(0);
        setTag(null);
    }

    @EventHandler
    public void on(InventoryClickEvent e) {
        if (!e.getWhoClicked().equals(viewer)) {
            return;
        }

        if (!isVisible()) {
            return;
        }

        if (inventory == null) {
            return;
        }

        if (!e.getView().getTopInventory().equals(inventory)) {
            return;
        }

        if (e.getClickedInventory() == null) {
            return;
        }

        if (!e.getView().getType().equals(getResolution().getType())) {
            return;
        }

        if (e.getClickedInventory().getType().equals(getResolution().getType())) {
            Element element = getElement(getLayoutPosition(e.getSlot()), getLayoutRow(e.getSlot()));

            switch (e.getAction()) {
                case CLONE_STACK:
                case UNKNOWN:
                case SWAP_WITH_CURSOR:
                case PLACE_SOME:
                case PLACE_ONE:
                case PLACE_ALL:
                case PICKUP_SOME:
                case PICKUP_ONE:
                case PICKUP_HALF:
                case PICKUP_ALL:
                case NOTHING:
                case MOVE_TO_OTHER_INVENTORY:
                case HOTBAR_SWAP:
                case HOTBAR_MOVE_AND_READD:
                case DROP_ONE_SLOT:
                case DROP_ONE_CURSOR:
                case DROP_ALL_SLOT:
                case DROP_ALL_CURSOR:
                case COLLECT_TO_CURSOR:
                    break;
            }

            switch (e.getClick()) {
                case DOUBLE_CLICK:
                    doubleclicked = true;
                    break;
                case LEFT:
                    clickcheck++;

                    if (clickcheck == 1) {
                        queueSync(() -> {
                            if (clickcheck == 1) {
                                clickcheck = 0;

                                if (element != null) {
                                    element.call(ElementEvent.LEFT, element);
                                }
                            }
                        });
                    } else if (clickcheck == 2) {
                        queueSync(() -> {
                            if (doubleclicked) {
                                doubleclicked = false;
                            } else {
                                scroll(1);
                            }

                            clickcheck = 0;
                        });
                    }

                    break;
                case RIGHT:
                    if (element != null) {
                        element.call(ElementEvent.RIGHT, element);
                    } else {
                        scroll(-1);
                    }
                    break;
                case SHIFT_LEFT:
                    if (element != null) {
                        element.call(ElementEvent.SHIFT_LEFT, element);
                    }
                    break;
                case SHIFT_RIGHT:
                    if (element != null) {
                        element.call(ElementEvent.SHIFT_RIGHT, element);
                    }
                    break;
                case SWAP_OFFHAND:
                case UNKNOWN:
                case WINDOW_BORDER_RIGHT:
                case WINDOW_BORDER_LEFT:
                case NUMBER_KEY:
                case MIDDLE:
                case DROP:
                case CREATIVE:
                case CONTROL_DROP:
                default:
                    break;
            }
        }

        e.setCancelled(true);
    }

    @EventHandler
    public void on(InventoryCloseEvent e) {
        if (!e.getPlayer().equals(viewer)) {
            return;
        }

        if (inventory == null) {
            return;
        }

        if (!e.getInventory().equals(inventory)) {
            return;
        }

        if (isVisible()) {
            deactivate(false);
            callClosed();
        }
    }

    private void queueSync(Runnable runnable) {
        if (runnable == null || plugin == null || !plugin.isEnabled()) {
            return;
        }

        // Mirror Bukkit's no-delay scheduleSyncDelayedTask behavior: run on the next tick.
        if (FoliaScheduler.runEntity(plugin, viewer, runnable, 1L)) {
            return;
        }

        if (FoliaScheduler.runGlobal(plugin, runnable, 1L)) {
            return;
        }

        try {
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, runnable);
        } catch (UnsupportedOperationException e) {
            throw new IllegalStateException("Failed to schedule sync task on Folia-safe scheduler.", e);
        }
    }

    @Override
    public WindowDecorator getDecorator() {
        return decorator;
    }

    @Override
    public UIWindow setDecorator(WindowDecorator decorator) {
        this.decorator = decorator;
        return this;
    }

    @Override
    public UIWindow close() {
        setVisible(false);
        return this;
    }

    @Override
    public UIWindow open() {
        setVisible(true);
        return this;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public UIWindow setVisible(boolean visible) {
        if (isVisible() == visible) {
            return this;
        }

        if (visible) {
            UIWindow activeWindow = ACTIVE_WINDOWS.get(viewer.getUniqueId());
            if (activeWindow != null && activeWindow != this) {
                activeWindow.deactivate(false);
            }

            Bukkit.getPluginManager().registerEvents(this, plugin);

            Inventory activeTopInventory = viewer.getOpenInventory() == null ? null : viewer.getOpenInventory().getTopInventory();
            if (canReuseInventory(activeTopInventory)) {
                inventory = activeTopInventory;
            } else {
                if (getResolution().getType().equals(InventoryType.CHEST)) {
                    inventory = Bukkit.createInventory(null, getViewportHeight() * 9, getTitle());
                } else {
                    inventory = Bukkit.createInventory(null, getResolution().getType(), getTitle());
                }

                viewer.openInventory(inventory);
            }
            this.visible = true;
            ACTIVE_WINDOWS.put(viewer.getUniqueId(), this);
            updateInventory();
        } else {
            deactivate(true);
        }

        return this;
    }

    @Override
    public int getViewportPosition() {
        return viewportPosition;
    }

    @Override
    public UIWindow setViewportPosition(int viewportPosition) {
        this.viewportPosition = viewportPosition;
        scroll(0);
        updateInventory();

        return this;
    }

    @Override
    public int getMaxViewportPosition() {
        return Math.max(0, highestRow - getViewportHeight());
    }

    @Override
    public UIWindow scroll(int direction) {
        viewportPosition = (int) clip(viewportPosition + direction, 0, getMaxViewportPosition()).doubleValue();
        updateInventory();

        return this;
    }

    @Override
    public int getViewportHeight() {
        return viewportSize;
    }

    @Override
    public UIWindow setViewportHeight(int height) {
        viewportSize = (int) clip(height, 1, getResolution().getMaxHeight()).doubleValue();

        if (isVisible()) {
            reopen();
        }

        return this;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public UIWindow setTitle(String title) {
        this.title = title;

        if (isVisible()) {
            reopen();
        }

        return this;
    }

    @Override
    public String getTag() {
        return tag;
    }

    @Override
    public UIWindow setTag(String tag) {
        this.tag = tag;
        return this;
    }

    @Override
    public UIWindow setElement(int position, int row, Element e) {
        if (row > highestRow) {
            highestRow = row;
        }

        elements.put(getRealPosition((int) clip(position, -getResolution().getMaxWidthOffset(), getResolution().getMaxWidthOffset()).doubleValue(), row), e);
        updateInventory();
        return this;
    }

    @Override
    public Element getElement(int position, int row) {
        return elements.get(getRealPosition((int) clip(position, -getResolution().getMaxWidthOffset(), getResolution().getMaxWidthOffset()).doubleValue(), row));
    }

    @Override
    public Player getViewer() {
        return viewer;
    }

    @Override
    public UIWindow onClosed(Callback<Window> window) {
        eClose = window;
        return this;
    }

    @Override
    public int getViewportSlots() {
        return getViewportHeight() * getResolution().getWidth();
    }

    @Override
    public int getLayoutRow(int viewportSlottedPosition) {
        return getRow(getRealLayoutPosition(viewportSlottedPosition));
    }

    @Override
    public int getLayoutPosition(int viewportSlottedPosition) {
        return getPosition(viewportSlottedPosition);
    }

    @Override
    public int getRealLayoutPosition(int viewportSlottedPosition) {
        return getRealPosition(getPosition(viewportSlottedPosition), getRow(viewportSlottedPosition) + getViewportPosition());
    }

    @Override
    public int getRealPosition(int position, int row) {
        return (int) (((row * getResolution().getWidth()) + getResolution().getMaxWidthOffset()) + clip(position, -getResolution().getMaxWidthOffset(), getResolution().getMaxWidthOffset()));
    }

    @Override
    public int getRow(int realPosition) {
        return realPosition / getResolution().getWidth();
    }

    @Override
    public int getPosition(int realPosition) {
        return (realPosition % getResolution().getWidth()) - getResolution().getMaxWidthOffset();
    }

    @Override
    public Window callClosed() {
        if (eClose != null) {
            eClose.run(this);
        }

        return this;
    }

    @Override
    public boolean hasElement(int position, int row) {
        return getElement(position, row) != null;
    }

    @Override
    public WindowResolution getResolution() {
        return resolution;
    }

    public Double clip(double value, double min, double max) {
        return Math.min(max, Math.max(min, value));
    }

    @Override
    public Window setResolution(WindowResolution resolution) {
        close();
        this.resolution = resolution;
        setViewportHeight((int) clip(getViewportHeight(), 1, getResolution().getMaxHeight()).doubleValue());
        return this;
    }

    @Override
    public Window clearElements() {
        highestRow = 0;
        elements.clear();
        updateInventory();
        return this;
    }

    @Override
    public Window updateInventory() {
        if (isVisible()) {
            ItemStack[] is = inventory.getContents();
            @SuppressWarnings("MismatchedQueryAndUpdateOfCollection") KSet<ItemStack> isf = new KSet<>();

            for (int i = 0; i < is.length; i++) {
                ItemStack isc = is[i];
                ItemStack isx = computeItemStack(i);
                int layoutRow = getLayoutRow(i);
                int layoutPosition = getLayoutPosition(i);

                if (isx != null && !hasElement(layoutPosition, layoutRow)) {
                    ItemStack gg = isx.clone();
                    gg.setAmount(gg.getAmount() + 1);
                    isf.add(gg);
                }

                if (((isc == null) != (isx == null)) || isx != null && isc != null && !isc.equals(isx)) {
                    inventory.setItem(i, isx);
                }
            }
        }

        return this;
    }

    @Override
    public ItemStack computeItemStack(int viewportSlot) {
        int layoutRow = getLayoutRow(viewportSlot);
        int layoutPosition = getLayoutPosition(viewportSlot);
        Element e = hasElement(layoutPosition, layoutRow) ? getElement(layoutPosition, layoutRow) : getDecorator().onDecorateBackground(this, layoutPosition, layoutRow);

        if (e != null) {
            return e.computeItemStack();
        }

        return null;
    }

    @Override
    public Window reopen() {
        return this.close().open();
    }

    private boolean canReuseInventory(Inventory activeTopInventory) {
        if (activeTopInventory == null) {
            return false;
        }

        if (activeTopInventory.getHolder() != null) {
            return false;
        }

        if (getResolution().getType().equals(InventoryType.CHEST)) {
            if (!activeTopInventory.getType().equals(InventoryType.CHEST)) {
                return false;
            }

            int expectedSize = getViewportHeight() * getResolution().getWidth();
            if (activeTopInventory.getSize() != expectedSize) {
                return false;
            }
        } else if (!activeTopInventory.getType().equals(getResolution().getType())) {
            return false;
        }

        String activeTitle = viewer.getOpenInventory() == null ? null : viewer.getOpenInventory().getTitle();
        return activeTitle != null && activeTitle.equals(getTitle());
    }

    private void deactivate(boolean closeInventory) {
        this.visible = false;
        HandlerList.unregisterAll(this);

        Inventory currentInventory = inventory;
        if (closeInventory && currentInventory != null) {
            Inventory topInventory = viewer.getOpenInventory() == null ? null : viewer.getOpenInventory().getTopInventory();
            if (topInventory != null && topInventory.equals(currentInventory)) {
                viewer.closeInventory();
            }
        }

        inventory = null;
        ACTIVE_WINDOWS.remove(viewer.getUniqueId(), this);
    }
}

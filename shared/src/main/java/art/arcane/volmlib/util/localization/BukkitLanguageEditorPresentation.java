package art.arcane.volmlib.util.localization;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public record BukkitLanguageEditorPresentation(Layout layout, Function<String, Optional<ItemStack>> icons) {
    private static final BukkitLanguageEditorPresentation STANDARD =
            new BukkitLanguageEditorPresentation(Layout.CENTERED, ignored -> Optional.empty());

    public BukkitLanguageEditorPresentation {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(icons, "icons");
    }

    public static BukkitLanguageEditorPresentation standard() {
        return STANDARD;
    }

    ItemStack icon(String messageId, Material fallback) {
        Optional<ItemStack> resolved = Objects.requireNonNull(icons.apply(messageId), "resolved icon");
        if (resolved.isEmpty() || resolved.get().getType().isAir()) {
            return new ItemStack(fallback);
        }
        return resolved.get().clone();
    }

    public enum Layout {
        CENTERED(16, 45),
        FOUR_ROWS(36, 36);

        private final int categoryPageSize;
        private final int messagePageSize;

        Layout(int categoryPageSize, int messagePageSize) {
            this.categoryPageSize = categoryPageSize;
            this.messagePageSize = messagePageSize;
        }

        public int categoryPageSize() {
            return categoryPageSize;
        }

        public int messagePageSize() {
            return messagePageSize;
        }
    }
}

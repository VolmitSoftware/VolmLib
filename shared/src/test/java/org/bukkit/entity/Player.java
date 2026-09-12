package org.bukkit.entity;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.UUID;

public interface Player {
  default UUID getUniqueId() {
    return new UUID(0L, 0L);
  }

  default void setMetadata(String metadataKey, MetadataValue newMetadataValue) {
  }

  default List<MetadataValue> getMetadata(String metadataKey) {
    return List.of();
  }

  default void removeMetadata(String metadataKey, Plugin owningPlugin) {
  }

  default Spigot spigot() {
    return new Spigot();
  }

  default void updateCommands() {
  }

  default void sendTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
  }

  default InventoryView getOpenInventory() {
    return null;
  }

  default void closeInventory() {
  }

  default void resetTitle() {
  }

  default boolean isOnline() {
    return false;
  }

  default InventoryView openInventory(Inventory inventory) {
    return null;
  }

  class Spigot extends CommandSender.Spigot {
    public void sendMessage(ChatMessageType position, BaseComponent... components) {
    }
  }
}

package org.bukkit.entity;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
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

  class Spigot {
    public void sendMessage(ChatMessageType position, BaseComponent... components) {
    }
  }
}

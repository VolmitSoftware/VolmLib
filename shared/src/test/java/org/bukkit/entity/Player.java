package org.bukkit.entity;

import java.util.UUID;

public interface Player {
  default UUID getUniqueId() {
    return new UUID(0L, 0L);
  }
}

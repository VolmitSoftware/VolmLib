package art.arcane.volmlib.util.bukkit;

import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;

public final class ChunkPositionSet {
  private static final int[] EMPTY = new int[0];

  private final NamespacedKey key;

  public ChunkPositionSet(Plugin plugin, String name) {
    this.key = new NamespacedKey(plugin, name);
  }

  public boolean contains(Block block) {
    return contains(snapshot(block.getChunk()), pack(block));
  }

  public void add(Block block) {
    Chunk chunk = block.getChunk();
    int[] positions = snapshot(chunk);
    int[] updated = insert(positions, pack(block));
    if (updated != positions) {
      write(chunk, updated);
    }
  }

  public boolean remove(Block block) {
    Chunk chunk = block.getChunk();
    int[] positions = snapshot(chunk);
    int[] updated = removeValue(positions, pack(block));
    if (updated == positions) {
      return false;
    }
    write(chunk, updated);
    return true;
  }

  public int[] snapshot(Chunk chunk) {
    int[] positions = chunk.getPersistentDataContainer().get(key, PersistentDataType.INTEGER_ARRAY);
    return positions == null ? EMPTY : positions;
  }

  public static boolean contains(int[] positions, int packed) {
    return positions.length > 0 && Arrays.binarySearch(positions, packed) >= 0;
  }

  public static int[] insert(int[] positions, int packed) {
    int index = Arrays.binarySearch(positions, packed);
    if (index >= 0) {
      return positions;
    }
    int insertion = -(index + 1);
    int[] updated = new int[positions.length + 1];
    System.arraycopy(positions, 0, updated, 0, insertion);
    updated[insertion] = packed;
    System.arraycopy(positions, insertion, updated, insertion + 1, positions.length - insertion);
    return updated;
  }

  public static int[] removeValue(int[] positions, int packed) {
    int index = Arrays.binarySearch(positions, packed);
    if (index < 0) {
      return positions;
    }
    int[] updated = new int[positions.length - 1];
    System.arraycopy(positions, 0, updated, 0, index);
    System.arraycopy(positions, index + 1, updated, index, positions.length - index - 1);
    return updated;
  }

  public static int pack(int localX, int y, int localZ, int minHeight) {
    return ((y - minHeight) << 8) | (localZ << 4) | localX;
  }

  public static int unpackLocalX(int packed) {
    return packed & 15;
  }

  public static int unpackLocalZ(int packed) {
    return (packed >> 4) & 15;
  }

  public static int unpackY(int packed, int minHeight) {
    return (packed >>> 8) + minHeight;
  }

  private int pack(Block block) {
    return pack(block.getX() & 15, block.getY(), block.getZ() & 15, block.getWorld().getMinHeight());
  }

  private void write(Chunk chunk, int[] positions) {
    PersistentDataContainer container = chunk.getPersistentDataContainer();
    if (positions.length == 0) {
      container.remove(key);
    } else {
      container.set(key, PersistentDataType.INTEGER_ARRAY, positions);
    }
  }
}

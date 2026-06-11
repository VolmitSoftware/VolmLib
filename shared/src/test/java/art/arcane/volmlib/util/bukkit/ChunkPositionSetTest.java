package art.arcane.volmlib.util.bukkit;

import org.junit.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ChunkPositionSetTest {
  @Test
  public void test_pack_roundTrip_recoversCoordinates() {
    int minHeight = -64;
    for (int localX = 0; localX < 16; localX++) {
      for (int localZ = 0; localZ < 16; localZ++) {
        for (int y = -64; y <= 320; y += 7) {
          int packed = ChunkPositionSet.pack(localX, y, localZ, minHeight);
          assertEquals(localX, ChunkPositionSet.unpackLocalX(packed));
          assertEquals(localZ, ChunkPositionSet.unpackLocalZ(packed));
          assertEquals(y, ChunkPositionSet.unpackY(packed, minHeight));
        }
      }
    }
  }

  @Test
  public void test_pack_distinctPositions_produceDistinctValues() {
    Set<Integer> seen = new HashSet<>();
    for (int localX = 0; localX < 16; localX++) {
      for (int localZ = 0; localZ < 16; localZ++) {
        for (int y = -64; y < 320; y += 13) {
          assertTrue(seen.add(ChunkPositionSet.pack(localX, y, localZ, -64)));
        }
      }
    }
  }

  @Test
  public void test_insert_keepsArraySortedAndDeduplicated() {
    Random random = new Random(42L);
    int[] positions = new int[0];
    Set<Integer> reference = new HashSet<>();
    for (int i = 0; i < 500; i++) {
      int packed = random.nextInt(100_000);
      positions = ChunkPositionSet.insert(positions, packed);
      reference.add(packed);
    }
    assertEquals(reference.size(), positions.length);
    for (int i = 1; i < positions.length; i++) {
      assertTrue(positions[i - 1] < positions[i]);
    }
    for (int packed : reference) {
      assertTrue(ChunkPositionSet.contains(positions, packed));
    }
  }

  @Test
  public void test_insert_existingValue_returnsSameArray() {
    int[] positions = ChunkPositionSet.insert(new int[0], 17);
    assertSame(positions, ChunkPositionSet.insert(positions, 17));
  }

  @Test
  public void test_removeValue_removesOnlyTarget() {
    int[] positions = new int[0];
    positions = ChunkPositionSet.insert(positions, 5);
    positions = ChunkPositionSet.insert(positions, 9);
    positions = ChunkPositionSet.insert(positions, 1);
    int[] updated = ChunkPositionSet.removeValue(positions, 5);
    assertNotEquals(positions.length, updated.length);
    assertFalse(ChunkPositionSet.contains(updated, 5));
    assertTrue(ChunkPositionSet.contains(updated, 1));
    assertTrue(ChunkPositionSet.contains(updated, 9));
  }

  @Test
  public void test_removeValue_missingValue_returnsSameArray() {
    int[] positions = ChunkPositionSet.insert(new int[0], 3);
    assertSame(positions, ChunkPositionSet.removeValue(positions, 4));
  }
}

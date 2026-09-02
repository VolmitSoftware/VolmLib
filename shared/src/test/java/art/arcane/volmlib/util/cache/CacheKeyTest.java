package art.arcane.volmlib.util.cache;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CacheKeyTest {
    @Test
    public void mixedKeysStayDistinctForEveryCoordinate() {
        Set<Long> mixed = new HashSet<>();
        for (int x = -64; x < 64; x++) {
            for (int z = -64; z < 64; z++) {
                assertTrue(mixed.add(CacheKey.mix(CacheKey.key(x, z))));
            }
        }
        assertEquals(128 * 128, mixed.size());
    }

    @Test
    public void mixedKeysSpreadNeighbouringChunksAcrossHashBuckets() {
        Set<Integer> plainBuckets = new HashSet<>();
        Set<Integer> mixedBuckets = new HashSet<>();
        for (int x = 0; x < 64; x++) {
            for (int z = 0; z < 64; z++) {
                long key = CacheKey.key(x, z);
                plainBuckets.add(Long.hashCode(key) & 4095);
                mixedBuckets.add(Long.hashCode(CacheKey.mix(key)) & 4095);
            }
        }
        // x ^ z collapses a 64 by 64 block of chunks onto 64 buckets; the mix spreads it.
        assertEquals(64, plainBuckets.size());
        assertTrue("mixed buckets " + mixedBuckets.size(), mixedBuckets.size() > 2400);
    }
}

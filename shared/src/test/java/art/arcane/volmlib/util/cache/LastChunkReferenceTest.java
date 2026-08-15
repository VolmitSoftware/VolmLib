package art.arcane.volmlib.util.cache;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class LastChunkReferenceTest {
    @Test
    public void quartRowMajorTraceKeepsThreeOfFourLookupsLocal() {
        LastChunkReference<Object> lastChunk = new LastChunkReference<>();
        Map<Long, Object> chunks = new HashMap<>();
        int hits = 0;
        int misses = 0;

        for (int quartZ = 0; quartZ < 16; quartZ++) {
            for (int quartX = 0; quartX < 16; quartX++) {
                int blockX = quartX << 2;
                int blockZ = quartZ << 2;
                long key = CacheKey.key(blockX >> 4, blockZ >> 4);
                Object chunk = lastChunk.get(key);
                if (chunk == null) {
                    misses++;
                    chunk = chunks.computeIfAbsent(key, ignored -> new Object());
                    lastChunk.set(key, chunk);
                } else {
                    hits++;
                }
            }
        }

        assertEquals(16, chunks.size());
        assertEquals(64, misses);
        assertEquals(192, hits);
    }
}

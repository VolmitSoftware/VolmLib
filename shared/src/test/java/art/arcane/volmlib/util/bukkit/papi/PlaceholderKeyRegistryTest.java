package art.arcane.volmlib.util.bukkit.papi;

import org.junit.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PlaceholderKeyRegistryTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Test
    public void resolve_unknownPathReturnsNull() {
        PlaceholderKeyRegistry registry = PlaceholderKeyRegistry.builder()
                .key(PlaceholderKeyRegistry.AVAILABLE, playerId -> PlaceholderValues.TRUE)
                .key("player.level", playerId -> "7")
                .build();

        assertNull(registry.resolve(PLAYER, "player.levl"));
        assertNull(registry.resolve(PLAYER, "skil.mining.level"));
        assertNull(registry.resolve(PLAYER, "player"));
        assertNull(registry.resolve(PLAYER, ""));
    }

    @Test
    public void resolve_exactKeyWinsBeforeAnyGroupLookup() {
        AtomicInteger groupCalls = new AtomicInteger();
        PlaceholderKeyRegistry registry = PlaceholderKeyRegistry.builder()
                .key(PlaceholderKeyRegistry.AVAILABLE, playerId -> PlaceholderValues.TRUE)
                .key("skill.count", playerId -> "12")
                .group("skill", (playerId, tail) -> {
                    groupCalls.incrementAndGet();
                    return tail;
                })
                .build();

        assertEquals("12", registry.resolve(PLAYER, "skill.count"));
        assertEquals(0, groupCalls.get());
        assertEquals("mining.level", registry.resolve(PLAYER, "skill.mining.level"));
        assertEquals(1, groupCalls.get());
    }

    @Test
    public void resolve_groupReceivesTailAndPlayer() {
        PlaceholderKeyRegistry registry = PlaceholderKeyRegistry.builder()
                .key(PlaceholderKeyRegistry.AVAILABLE, playerId -> PlaceholderValues.TRUE)
                .group("skill", (playerId, tail) -> playerId + "/" + tail)
                .build();

        assertEquals(PLAYER + "/mining.has-level.5", registry.resolve(PLAYER, "skill.mining.has-level.5"));
        assertNull(registry.resolve(PLAYER, "adaptation.mining.level"));
    }

    @Test
    public void resolve_malformedPathsNeverReachAGroup() {
        AtomicInteger groupCalls = new AtomicInteger();
        PlaceholderKeyRegistry registry = PlaceholderKeyRegistry.builder()
                .key(PlaceholderKeyRegistry.AVAILABLE, playerId -> PlaceholderValues.TRUE)
                .group("skill", (playerId, tail) -> {
                    groupCalls.incrementAndGet();
                    return tail;
                })
                .build();

        assertNull(registry.resolve(PLAYER, "skill."));
        assertNull(registry.resolve(PLAYER, ".skill"));
        assertNull(registry.resolve(PLAYER, "."));
        assertEquals(0, groupCalls.get());
    }

    @Test
    public void resolve_missingSnapshotReturnsUnavailableSentinel() {
        PlaceholderSnapshot<String> snapshot = new PlaceholderSnapshot<>();
        PlayerSnapshotStore<String> perPlayer = new PlayerSnapshotStore<>();
        PlaceholderKeyRegistry registry = PlaceholderKeyRegistry.builder()
                .key(PlaceholderKeyRegistry.AVAILABLE, playerId -> snapshot.available())
                .key("world.biome", playerId -> {
                    String value = snapshot.get();
                    return value == null ? PlaceholderValues.UNAVAILABLE : value;
                })
                .key("player.level", playerId -> {
                    String value = perPlayer.get(playerId);
                    return value == null ? PlaceholderValues.UNAVAILABLE : value;
                })
                .build();

        assertEquals(PlaceholderValues.UNAVAILABLE, registry.resolve(PLAYER, "world.biome"));
        assertEquals(PlaceholderValues.UNAVAILABLE, registry.resolve(PLAYER, "player.level"));
        assertEquals(PlaceholderValues.FALSE, registry.resolve(PLAYER, PlaceholderKeyRegistry.AVAILABLE));

        snapshot.publish("Hot Desert Dunes");
        perPlayer.publish(PLAYER, "42");

        assertEquals("Hot Desert Dunes", registry.resolve(PLAYER, "world.biome"));
        assertEquals("42", registry.resolve(PLAYER, "player.level"));
        assertEquals(PlaceholderValues.TRUE, registry.resolve(PLAYER, PlaceholderKeyRegistry.AVAILABLE));
    }

    @Test
    public void builder_rejectsKeysOutsideTheGrammar() {
        PlaceholderKeyRegistry.Builder builder = PlaceholderKeyRegistry.builder();

        assertThrows(IllegalArgumentException.class, () -> builder.key("can_claim_next", playerId -> "x"));
        assertThrows(IllegalArgumentException.class, () -> builder.key("Player.Level", playerId -> "x"));
        assertThrows(IllegalArgumentException.class, () -> builder.key("player..level", playerId -> "x"));
        assertThrows(IllegalArgumentException.class, () -> builder.key("player.level.", playerId -> "x"));
        assertThrows(IllegalArgumentException.class, () -> builder.key("a.b.c.d.e", playerId -> "x"));
        assertThrows(IllegalArgumentException.class, () -> builder.key("", playerId -> "x"));
        assertThrows(IllegalArgumentException.class, () -> builder.group("skill.mining", (playerId, tail) -> "x"));
    }

    @Test
    public void builder_rejectsDuplicates() {
        PlaceholderKeyRegistry.Builder builder = PlaceholderKeyRegistry.builder()
                .key("player.level", playerId -> "1")
                .group("skill", (playerId, tail) -> "1");

        assertThrows(IllegalArgumentException.class, () -> builder.key("player.level", playerId -> "2"));
        assertThrows(IllegalArgumentException.class, () -> builder.group("skill", (playerId, tail) -> "2"));
    }

    @Test
    public void keys_publishesExactKeysAndGroupWildcardsSorted() {
        PlaceholderKeyRegistry registry = PlaceholderKeyRegistry.builder()
                .key("player.level", playerId -> "1")
                .key(PlaceholderKeyRegistry.AVAILABLE, playerId -> PlaceholderValues.TRUE)
                .group("skill", (playerId, tail) -> "1")
                .build();

        assertEquals(List.of("available", "player.level", "skill.*"), registry.keys());
        assertTrue(registry.containsKey(PlaceholderKeyRegistry.AVAILABLE));
        assertFalse(registry.containsKey("skill"));
    }
}

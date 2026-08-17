package art.arcane.volmlib.util.hud;

import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class HudTitleServiceTest {
  private final Map<Plugin, String> store = new LinkedHashMap<>();
  private final UUID playerId = UUID.randomUUID();
  private Plugin plugin;
  private Player player;
  private HudTitleService service;

  @Before
  public void setUp() {
    plugin = mock(Plugin.class);
    when(plugin.getName()).thenReturn("Wormholes");
    player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(playerId);
    doAnswer(invocation -> {
      MetadataValue value = invocation.getArgument(1);
      store.put(value.getOwningPlugin(), value.asString());
      return null;
    }).when(player).setMetadata(eq(HudTitleService.METADATA_KEY), any(MetadataValue.class));
    doAnswer(invocation -> {
      store.remove((Plugin) invocation.getArgument(1));
      return null;
    }).when(player).removeMetadata(eq(HudTitleService.METADATA_KEY), any(Plugin.class));
    when(player.getMetadata(HudTitleService.METADATA_KEY)).thenAnswer(invocation -> {
      List<MetadataValue> values = new ArrayList<>();
      store.forEach((owner, encoded) -> values.add(new FixedMetadataValue(owner, encoded)));
      return values;
    });
    service = new HudTitleService(plugin);
  }

  private void seedForeignBid(String ownerName, int priority) {
    Plugin foreign = mock(Plugin.class);
    when(foreign.getName()).thenReturn(ownerName);
    long now = System.currentTimeMillis();
    store.put(foreign, new HudBid(priority, now - 1000L, now, 5000L, "foreign:purpose").encode());
  }

  @Test
  public void test_resolve_uncontested_grants() {
    HudTitleClaim claim = service.open(player, "wormholes:look", HudPriority.AMBIENT, 1500L);
    assertTrue(claim.resolve());
    assertTrue(claim.granted());
  }

  @Test
  public void test_resolve_lowerPriorityThanForeignBid_denied() {
    seedForeignBid("React", HudPriority.INTERACTIVE);
    HudTitleClaim claim = service.open(player, "wormholes:look", HudPriority.AMBIENT, 1500L);
    assertFalse(claim.resolve());
    assertFalse(claim.granted());
  }

  @Test
  public void test_resolve_higherPriorityThanForeignBid_grants() {
    seedForeignBid("React", HudPriority.INTERACTIVE);
    HudTitleClaim claim = service.open(player, "wormholes:direction", HudPriority.MODAL, 1500L);
    assertTrue(claim.resolve());
  }

  @Test
  public void test_release_removesOwnBidMetadata() {
    HudTitleClaim claim = service.open(player, "wormholes:look", HudPriority.AMBIENT, 1500L);
    assertTrue(claim.resolve());
    claim.release();
    assertFalse(store.containsKey(plugin));
    assertFalse(claim.granted());
  }

  @Test
  public void test_retire_neverTouchesThePlayer() {
    HudTitleClaim claim = service.open(player, "wormholes:look", HudPriority.AMBIENT, 1500L);
    assertTrue(claim.resolve());
    clearInvocations(player);
    claim.retire();
    verifyNoInteractions(player);
  }

  @Test
  public void test_open_purposeWithPipe_rejected() {
    try {
      service.open(player, "bad|purpose", HudPriority.AMBIENT, 1500L);
      throw new AssertionError("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }
}

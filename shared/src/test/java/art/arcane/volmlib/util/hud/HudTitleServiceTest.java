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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
  public void test_dismiss_currentWinningClaim_resetsTitleOnce() {
    HudTitleClaim claim = service.open(player, "wormholes:look", HudPriority.NOTICE, 1500L);
    assertTrue(claim.resolve());

    assertTrue(claim.dismiss());
    assertFalse(claim.dismiss());

    verify(player, times(1)).resetTitle();
    assertFalse(store.containsKey(plugin));
    assertFalse(claim.granted());
  }

  @Test
  public void test_dismiss_foreignWinner_releasesOwnBidWithoutResettingTitle() {
    HudTitleClaim claim = service.open(player, "wormholes:look", HudPriority.NOTICE, 1500L);
    assertTrue(claim.resolve());
    seedForeignBid("React", HudPriority.INTERACTIVE);

    assertFalse(claim.dismiss());

    verify(player, never()).resetTitle();
    assertFalse(store.containsKey(plugin));
    assertTrue(store.size() == 1);
  }

  @Test
  public void test_dismiss_replacedLocalClaim_preservesCurrentTitle() {
    HudTitleClaim previous = service.open(player, "wormholes:look", HudPriority.NOTICE, 1500L);
    assertTrue(previous.resolve());
    previous.release();
    HudTitleClaim current = service.open(player, "wormholes:direction", HudPriority.NOTICE, 1500L);
    assertTrue(current.resolve());

    assertFalse(previous.dismiss());

    verify(player, never()).resetTitle();
    assertTrue(store.containsKey(plugin));
    assertTrue(current.dismiss());
    verify(player).resetTitle();
  }

  @Test
  public void test_dismiss_otherServiceFromSamePlugin_preservesItsMetadataAndTitle() {
    HudTitleClaim previous = service.open(player, "wormholes:look", HudPriority.NOTICE, 1500L);
    assertTrue(previous.resolve());
    HudTitleService replacement = new HudTitleService(plugin);
    HudTitleClaim current = replacement.open(player, "wormholes:direction", HudPriority.INTERACTIVE, 1500L);
    assertTrue(current.resolve());

    assertFalse(previous.dismiss());

    verify(player, never()).resetTitle();
    assertTrue(store.containsKey(plugin));
    assertTrue(current.dismiss());
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
  public void test_resolve_higherPriorityClaim_preemptsTheLocalHolder() {
    List<HudTitleClaim> preempted = new ArrayList<>();
    HudTitleClaim ambient = service.open(player, "gloss:surface:welcome", HudPriority.AMBIENT, 1500L, preempted::add);
    assertTrue(ambient.resolve());

    HudTitleClaim modal = service.open(player, "gloss:surface:alert", HudPriority.MODAL, 1500L, claim -> {
    });
    assertTrue(modal.resolve());

    assertEquals(List.of(ambient), preempted);
    assertFalse(ambient.granted());
  }

  @Test
  public void test_resolve_sameClaimTwice_neverPreemptsItself() {
    List<HudTitleClaim> preempted = new ArrayList<>();
    HudTitleClaim claim = service.open(player, "gloss:surface:welcome", HudPriority.AMBIENT, 1500L, preempted::add);
    assertTrue(claim.resolve());
    assertTrue(claim.resolve());
    assertTrue(preempted.isEmpty());
  }

  @Test
  public void test_show_granted_sendsTheTitle() {
    HudTitleClaim claim = service.open(player, "gloss:surface:welcome", HudPriority.NOTICE, 1500L);
    assertTrue(claim.show("§6Welcome", "§7to the server", 10, 40, 10));
    verify(player).sendTitle("§6Welcome", "§7to the server", 10, 40, 10);
  }

  @Test
  public void test_show_denied_sendsNothing() {
    seedForeignBid("React", HudPriority.MODAL);
    HudTitleClaim claim = service.open(player, "gloss:surface:welcome", HudPriority.AMBIENT, 1500L);
    assertFalse(claim.show("§6Welcome", "", 10, 40, 10));
    verify(player, never()).sendTitle(anyString(), anyString(), anyInt(), anyInt(), anyInt());
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

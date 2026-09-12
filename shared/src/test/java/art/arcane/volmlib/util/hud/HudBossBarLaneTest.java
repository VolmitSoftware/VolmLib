package art.arcane.volmlib.util.hud;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HudBossBarLaneTest {
  private final Map<String, Map<Plugin, String>> store = new LinkedHashMap<>();
  private final UUID playerId = UUID.randomUUID();
  private final List<BossBar> createdBars = new ArrayList<>();
  private Player player;
  private AtomicLong now;
  private List<ScheduledSweep> scheduledSweeps;

  @Before
  public void setUp() {
    player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(playerId);
    doAnswer(invocation -> {
      String key = invocation.getArgument(0);
      MetadataValue value = invocation.getArgument(1);
      store.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).put(value.getOwningPlugin(), value.asString());
      return null;
    }).when(player).setMetadata(anyString(), any(MetadataValue.class));
    doAnswer(invocation -> {
      String key = invocation.getArgument(0);
      Map<Plugin, String> owners = store.get(key);
      if (owners != null) {
        owners.remove((Plugin) invocation.getArgument(1));
        if (owners.isEmpty()) {
          store.remove(key);
        }
      }
      return null;
    }).when(player).removeMetadata(anyString(), any(Plugin.class));
    when(player.getMetadata(anyString())).thenAnswer(invocation -> {
      String key = invocation.getArgument(0);
      List<MetadataValue> values = new ArrayList<>();
      store.getOrDefault(key, Map.of()).forEach((owner, encoded) -> values.add(new FixedMetadataValue(owner, encoded)));
      return values;
    });
    now = new AtomicLong(10_000L);
    scheduledSweeps = new ArrayList<>();
    createdBars.clear();
  }

  @Test
  public void test_show_topBidWinsTheOnlySlotAndTheLoserIsDenied() {
    HudBossBarLane iris = lane("Iris");
    HudBossBarLane gloss = lane("Gloss");

    assertTrue(iris.show(player, "iris:pregen", HudPriority.PROGRESS, "Pregen", 0.5D, BarColor.GREEN, BarStyle.SOLID, 5_000L, 1));
    assertFalse(gloss.show(player, "gloss:surface:arena", HudPriority.STATUS, "Arena", 0.25D, BarColor.RED, BarStyle.SEGMENTED_10, 5_000L, 1));
  }

  @Test
  public void test_show_deniedBarIsHiddenButKeepsItsBid() {
    HudBossBarLane iris = lane("Iris");
    HudBossBarLane gloss = lane("Gloss");

    iris.show(player, "iris:pregen", HudPriority.PROGRESS, "Pregen", 0.5D, BarColor.GREEN, BarStyle.SOLID, 5_000L, 1);
    gloss.show(player, "gloss:surface:arena", HudPriority.STATUS, "Arena", 0.25D, BarColor.RED, BarStyle.SEGMENTED_10, 5_000L, 1);

    BossBar denied = createdBars.get(1);
    verify(denied, never()).addPlayer(player);
    assertTrue(store.containsKey(HudBossBarLane.METADATA_KEY + "|gloss:surface:arena"));
  }

  @Test
  public void test_sweep_expiredWinnerReleasesTheSlotToTheNextBid() {
    HudBossBarLane iris = lane("Iris");
    HudBossBarLane gloss = lane("Gloss");

    iris.show(player, "iris:pregen", HudPriority.PROGRESS, "Pregen", 0.5D, BarColor.GREEN, BarStyle.SOLID, 5_000L, 1);
    assertFalse(gloss.show(player, "gloss:surface:arena", HudPriority.STATUS, "Arena", 0.25D, BarColor.RED, BarStyle.SEGMENTED_10, 5_000L, 1));

    now.set(20_001L);
    iris.sweep();

    assertTrue(gloss.show(player, "gloss:surface:arena", HudPriority.STATUS, "Arena", 0.25D, BarColor.RED, BarStyle.SEGMENTED_10, 5_000L, 1));
    verify(createdBars.get(1), atLeastOnce()).addPlayer(player);
  }

  @Test
  public void test_sweep_removesTheExpiredBarAndItsBid() {
    HudBossBarLane iris = lane("Iris");
    iris.show(player, "iris:pregen", HudPriority.PROGRESS, "Pregen", 0.5D, BarColor.GREEN, BarStyle.SOLID, 5_000L, 1);

    now.set(20_001L);
    iris.sweep();

    verify(createdBars.get(0)).removeAll();
    assertFalse(store.containsKey(HudBossBarLane.METADATA_KEY + "|iris:pregen"));
    assertFalse(store.containsKey(HudBossBarLane.METADATA_KEY + "|index"));
  }

  @Test
  public void test_show_reappliesStyleOnlyWhenItChanges() {
    HudBossBarLane gloss = lane("Gloss");
    gloss.show(player, "gloss:surface:arena", HudPriority.STATUS, "Arena", 0.25D, BarColor.RED, BarStyle.SOLID, 5_000L, 4);
    BossBar bar = createdBars.get(0);
    clearInvocations(bar);

    gloss.show(player, "gloss:surface:arena", HudPriority.STATUS, "Arena", 0.30D, BarColor.RED, BarStyle.SOLID, 5_000L, 4);
    verify(bar, never()).setStyle(any(BarStyle.class));

    gloss.show(player, "gloss:surface:arena", HudPriority.STATUS, "Arena", 0.30D, BarColor.RED, BarStyle.SEGMENTED_12, 5_000L, 4);
    verify(bar).setStyle(BarStyle.SEGMENTED_12);
  }

  @Test
  public void test_show_belowTheCapKeepsEveryOwnBar() {
    HudBossBarLane gloss = lane("Gloss");
    assertTrue(gloss.show(player, "gloss:a", HudPriority.STATUS, "A", 0.1D, BarColor.RED, BarStyle.SOLID, 5_000L, 2));
    assertTrue(gloss.show(player, "gloss:b", HudPriority.NOTICE, "B", 0.2D, BarColor.BLUE, BarStyle.SOLID, 5_000L, 2));
    assertFalse(gloss.show(player, "gloss:c", HudPriority.AMBIENT, "C", 0.3D, BarColor.PINK, BarStyle.SOLID, 5_000L, 2));
  }

  @Test
  public void test_hide_dropsTheBidSoOtherPluginsAdvance() {
    HudBossBarLane iris = lane("Iris");
    HudBossBarLane gloss = lane("Gloss");

    iris.show(player, "iris:pregen", HudPriority.PROGRESS, "Pregen", 0.5D, BarColor.GREEN, BarStyle.SOLID, 5_000L, 1);
    assertFalse(gloss.show(player, "gloss:surface:arena", HudPriority.STATUS, "Arena", 0.25D, BarColor.RED, BarStyle.SEGMENTED_10, 5_000L, 1));

    iris.hide(player, "iris:pregen");

    assertTrue(gloss.show(player, "gloss:surface:arena", HudPriority.STATUS, "Arena", 0.25D, BarColor.RED, BarStyle.SEGMENTED_10, 5_000L, 1));
  }

  @Test
  public void test_legacyShow_withoutAPluginStillShowsAndWritesNoMetadata() {
    HudBossBarLane lane = new HudBossBarLane(null, now::get, this::createBar, (runnable, delayTicks) -> false);
    lane.show(player, "shaped:portal", "Portal", 0.5D, BarColor.PURPLE, BarStyle.SOLID, 5_000L);
    verify(createdBars.get(0)).addPlayer(player);
    assertTrue(store.isEmpty());
  }

  @Test
  public void test_startSweeper_reschedulesItselfEveryPeriod() {
    HudBossBarLane gloss = lane("Gloss");
    gloss.startSweeper(40L);

    assertEquals(1, scheduledSweeps.size());
    assertEquals(40L, scheduledSweeps.get(0).delayTicks());
    scheduledSweeps.remove(0).runnable().run();
    assertEquals(1, scheduledSweeps.size());
    assertEquals(40L, scheduledSweeps.get(0).delayTicks());
  }

  private HudBossBarLane lane(String pluginName) {
    Plugin plugin = mock(Plugin.class);
    when(plugin.getName()).thenReturn(pluginName);
    return new HudBossBarLane(plugin, now::get, this::createBar, (runnable, delayTicks) -> {
      scheduledSweeps.add(new ScheduledSweep(runnable, delayTicks));
      return true;
    });
  }

  private BossBar createBar(String title, BarColor color, BarStyle style) {
    BossBar bar = mock(BossBar.class);
    createdBars.add(bar);
    return bar;
  }

  private record ScheduledSweep(Runnable runnable, long delayTicks) {
  }
}

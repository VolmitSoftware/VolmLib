package art.arcane.volmlib.util.hud;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class HudActionBarTest {
  private final Map<Plugin, String> store = new LinkedHashMap<>();
  private final UUID playerId = UUID.randomUUID();
  private Plugin plugin;
  private Player player;
  private Player.Spigot spigot;
  private HudActionBar bar;

  @Before
  public void setUp() {
    plugin = mock(Plugin.class);
    when(plugin.getName()).thenReturn("Adapt");
    player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(playerId);
    spigot = mock(Player.Spigot.class);
    when(player.spigot()).thenReturn(spigot);
    doAnswer(invocation -> {
      MetadataValue value = invocation.getArgument(1);
      store.put(value.getOwningPlugin(), value.asString());
      return null;
    }).when(player).setMetadata(eq(HudActionBar.METADATA_KEY), any(MetadataValue.class));
    doAnswer(invocation -> {
      store.remove((Plugin) invocation.getArgument(1));
      return null;
    }).when(player).removeMetadata(eq(HudActionBar.METADATA_KEY), any(Plugin.class));
    when(player.getMetadata(HudActionBar.METADATA_KEY)).thenAnswer(invocation -> {
      List<MetadataValue> values = new ArrayList<>();
      store.forEach((owner, encoded) -> values.add(new FixedMetadataValue(owner, encoded)));
      return values;
    });
    bar = new HudActionBar(plugin);
  }

  private String lastSentPlainText() {
    ArgumentCaptor<BaseComponent[]> sent = ArgumentCaptor.forClass(BaseComponent[].class);
    verify(spigot, atLeastOnce()).sendMessage(eq(ChatMessageType.ACTION_BAR), sent.capture());
    return BaseComponent.toPlainText(sent.getValue());
  }

  @Test
  public void test_publish_postsEncodedMetadataAndSendsComposedLine() {
    bar.publish(player, new HudSegment("adapt:xp", HudPriority.AMBIENT, 1500L, List.of(HudSlot.LEFT), "+12XP"));
    assertTrue(store.containsKey(plugin));
    List<HudStampedSegment> posted = HudSegmentCodec.decode(store.get(plugin));
    assertEquals(1, posted.size());
    assertEquals("adapt:xp", posted.get(0).purpose());
    assertEquals("+12XP", posted.get(0).text());
    assertEquals("+12XP", lastSentPlainText());
  }

  @Test
  public void test_publish_composesForeignSegmentsIntoOneLine() {
    Plugin react = mock(Plugin.class);
    when(react.getName()).thenReturn("React");
    long now = System.currentTimeMillis();
    store.put(react, HudSegmentCodec.encode(List.of(new HudStampedSegment(HudPriority.PINNED, now - 100L, now, 5000L, List.of(HudSlot.CENTER), "react:monitor", "monitor"))));
    bar.publish(player, new HudSegment("adapt:xp", HudPriority.AMBIENT, 1500L, List.of(HudSlot.LEFT), "+12XP"));
    assertEquals("+12XP  monitor", lastSentPlainText());
  }

  @Test
  public void test_clear_lastSegment_removesMetadataAndWipesBar() {
    bar.publish(player, new HudSegment("adapt:xp", HudPriority.AMBIENT, 1500L, List.of(HudSlot.LEFT), "+12XP"));
    bar.clear(player, "adapt:xp");
    assertFalse(store.containsKey(plugin));
    assertEquals(" ", lastSentPlainText());
  }

  @Test
  public void test_clear_unknownPurpose_sendsNothing() {
    bar.clear(player, "adapt:xp");
    verifyNoInteractions(spigot);
  }

  @Test
  public void test_publish_republish_preservesSinceMillis() {
    bar.publish(player, new HudSegment("adapt:status:sense", HudPriority.STATUS, 2500L, List.of(HudSlot.CENTER, HudSlot.LEFT), "one"));
    long since = HudSegmentCodec.decode(store.get(plugin)).get(0).sinceMillis();
    bar.publish(player, new HudSegment("adapt:status:sense", HudPriority.STATUS, 2500L, List.of(HudSlot.CENTER, HudSlot.LEFT), "two"));
    assertEquals(since, HudSegmentCodec.decode(store.get(plugin)).get(0).sinceMillis());
    assertEquals("two", HudSegmentCodec.decode(store.get(plugin)).get(0).text());
  }

  @Test
  public void test_retire_neverTouchesThePlayer() {
    bar.publish(player, new HudSegment("adapt:xp", HudPriority.AMBIENT, 1500L, List.of(HudSlot.LEFT), "+12XP"));
    clearInvocations(player, spigot);
    bar.retire(playerId);
    verifyNoInteractions(player, spigot);
  }

  @Test
  public void test_retirePurpose_preservesOtherLocalSegmentsWithoutPlayerAccess() {
    bar.publish(player, new HudSegment("iris:job", HudPriority.PROGRESS, 1500L, List.of(HudSlot.CENTER), "progress"));
    bar.publish(player, new HudSegment("iris:notice", HudPriority.NOTICE, 1500L, List.of(HudSlot.RIGHT), "notice"));
    clearInvocations(player, spigot);

    bar.retire(playerId, "iris:job");
    verifyNoInteractions(player, spigot);

    bar.publish(player, new HudSegment("iris:status", HudPriority.STATUS, 1500L, List.of(HudSlot.LEFT), "status"));
    List<HudStampedSegment> posted = HudSegmentCodec.decode(store.get(plugin));
    assertEquals(2, posted.size());
    assertTrue(posted.stream().anyMatch(segment -> segment.purpose().equals("iris:notice")));
    assertTrue(posted.stream().anyMatch(segment -> segment.purpose().equals("iris:status")));
    assertFalse(posted.stream().anyMatch(segment -> segment.purpose().equals("iris:job")));
  }
}

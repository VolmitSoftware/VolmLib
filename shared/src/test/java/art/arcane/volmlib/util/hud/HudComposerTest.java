package art.arcane.volmlib.util.hud;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class HudComposerTest {
  private static final String SEP = HudComposer.SEPARATOR;

  private static HudComposer.Source source(String owner, String purpose, int priority, long since, List<HudSlot> slots, String text) {
    return new HudComposer.Source(owner, new HudStampedSegment(priority, since, 1000L, 5000L, slots, purpose, text));
  }

  @Test
  public void test_compose_singleSegment_rendersItsTextOnly() {
    String line = HudComposer.compose(List.of(source("React", "react:monitor", HudPriority.PINNED, 1L, List.of(HudSlot.CENTER), "monitor")), 1000L);
    assertEquals("monitor", line);
  }

  @Test
  public void test_compose_adaptTrioWithoutMonitor_xpLeftAbilityCenterNoticeRight() {
    String line = HudComposer.compose(List.of(
      source("Adapt", "adapt:xp", HudPriority.AMBIENT, 3L, List.of(HudSlot.LEFT), "+12XP"),
      source("Adapt", "adapt:status:sense", HudPriority.STATUS, 1L, List.of(HudSlot.CENTER, HudSlot.LEFT), "sense"),
      source("Adapt", "adapt:notice", HudPriority.NOTICE, 2L, List.of(HudSlot.CENTER, HudSlot.RIGHT), "notice")
    ), 1000L);
    assertEquals("+12XP" + SEP + "sense" + SEP + "notice", line);
  }

  @Test
  public void test_compose_monitorPinned_takesCenterAndAbilityGroupFlanksLeft() {
    String line = HudComposer.compose(List.of(
      source("React", "react:monitor", HudPriority.PINNED, 9L, List.of(HudSlot.CENTER), "monitor"),
      source("Adapt", "adapt:xp", HudPriority.AMBIENT, 3L, List.of(HudSlot.LEFT), "+12XP"),
      source("Adapt", "adapt:status:sense", HudPriority.STATUS, 1L, List.of(HudSlot.CENTER, HudSlot.LEFT), "sense"),
      source("Adapt", "adapt:notice", HudPriority.NOTICE, 2L, List.of(HudSlot.CENTER, HudSlot.RIGHT), "notice")
    ), 1000L);
    assertEquals("+12XP" + SEP + "sense" + SEP + "monitor" + SEP + "notice", line);
  }

  @Test
  public void test_compose_nativeLeftRendersBeforeSpilledJoiner() {
    String line = HudComposer.compose(List.of(
      source("Adapt", "adapt:status:sense", HudPriority.STATUS, 1L, List.of(HudSlot.CENTER, HudSlot.LEFT), "sense"),
      source("React", "react:monitor", HudPriority.PINNED, 9L, List.of(HudSlot.CENTER), "monitor"),
      source("Adapt", "adapt:xp", HudPriority.AMBIENT, 3L, List.of(HudSlot.LEFT), "+12XP")
    ), 1000L);
    assertEquals("+12XP" + SEP + "sense" + SEP + "monitor", line);
  }

  @Test
  public void test_compose_contestedSlot_stacksInDeterministicOrder() {
    String line = HudComposer.compose(List.of(
      source("Wormholes", "wormholes:notice", HudPriority.NOTICE, 5L, List.of(HudSlot.CENTER, HudSlot.RIGHT), "wormholes"),
      source("Gloss", "gloss:reload", HudPriority.NOTICE, 5L, List.of(HudSlot.CENTER, HudSlot.RIGHT), "gloss"),
      source("React", "react:monitor", HudPriority.PINNED, 9L, List.of(HudSlot.CENTER), "monitor")
    ), 1000L);
    assertEquals("monitor" + SEP + "gloss" + SEP + "wormholes", line);
  }

  @Test
  public void test_compose_expiredSegments_skipped() {
    String line = HudComposer.compose(List.of(
      source("React", "react:monitor", HudPriority.PINNED, 9L, List.of(HudSlot.CENTER), "monitor"),
      new HudComposer.Source("Adapt", new HudStampedSegment(HudPriority.NOTICE, 1L, 1000L, 100L, List.of(HudSlot.RIGHT), "adapt:notice", "stale"))
    ), 5000L);
    assertEquals("monitor", line);
  }

  @Test
  public void test_compose_blankSegments_skipped() {
    String line = HudComposer.compose(List.of(
      source("React", "react:monitor", HudPriority.PINNED, 9L, List.of(HudSlot.CENTER), "monitor"),
      source("Adapt", "adapt:notice", HudPriority.NOTICE, 1L, List.of(HudSlot.RIGHT), "  ")
    ), 1000L);
    assertEquals("monitor", line);
  }

  @Test
  public void test_compose_overBudget_lowestPrioritySkipped() {
    String big = "m".repeat(120);
    String line = HudComposer.compose(List.of(
      source("React", "react:monitor", HudPriority.PINNED, 9L, List.of(HudSlot.CENTER), big),
      source("Iris", "iris:action", HudPriority.PROGRESS, 1L, List.of(HudSlot.CENTER, HudSlot.LEFT), "p".repeat(40)),
      source("Adapt", "adapt:xp", HudPriority.AMBIENT, 3L, List.of(HudSlot.LEFT), "+12XP")
    ), 1000L);
    assertEquals("+12XP" + SEP + big, line);
  }

  @Test
  public void test_compose_singleOverBudgetSegment_stillIncluded() {
    String huge = "m".repeat(HudComposer.VISIBLE_BUDGET + 50);
    String line = HudComposer.compose(List.of(source("React", "react:monitor", HudPriority.PINNED, 9L, List.of(HudSlot.CENTER), huge)), 1000L);
    assertEquals(huge, line);
  }

  @Test
  public void test_compose_colorCodes_doNotCountAgainstBudget() {
    String colored = "§a§l" + "x".repeat(100);
    String line = HudComposer.compose(List.of(
      source("React", "react:monitor", HudPriority.PINNED, 9L, List.of(HudSlot.CENTER), colored),
      source("Adapt", "adapt:xp", HudPriority.AMBIENT, 3L, List.of(HudSlot.LEFT), "y".repeat(50))
    ), 1000L);
    assertEquals("y".repeat(50) + SEP + colored, line);
  }

  @Test
  public void test_compose_equalPriority_tieBreaksBySinceThenOwnerThenPurpose() {
    String line = HudComposer.compose(List.of(
      source("Wormholes", "wormholes:notice", HudPriority.NOTICE, 5L, List.of(HudSlot.CENTER), "later"),
      source("Adapt", "adapt:notice", HudPriority.NOTICE, 1L, List.of(HudSlot.CENTER), "earlier")
    ), 1000L);
    assertEquals("earlier" + SEP + "later", line);
  }

  @Test
  public void test_compose_emptyInput_returnsEmptyLine() {
    assertEquals("", HudComposer.compose(List.of(), 1000L));
  }
}

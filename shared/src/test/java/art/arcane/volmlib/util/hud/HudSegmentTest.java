package art.arcane.volmlib.util.hud;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class HudSegmentTest {
  @Test
  public void test_ctor_blankPurpose_rejected() {
    assertThrows(IllegalArgumentException.class, () -> new HudSegment(" ", 10, 1000L, List.of(HudSlot.CENTER), "x"));
  }

  @Test
  public void test_ctor_controlCharacterInPurpose_rejected() {
    assertThrows(IllegalArgumentException.class, () -> new HudSegment("ab", 10, 1000L, List.of(HudSlot.CENTER), "x"));
  }

  @Test
  public void test_ctor_nonPositiveTtl_rejected() {
    assertThrows(IllegalArgumentException.class, () -> new HudSegment("p", 10, 0L, List.of(HudSlot.CENTER), "x"));
  }

  @Test
  public void test_ctor_emptySlots_rejected() {
    assertThrows(IllegalArgumentException.class, () -> new HudSegment("p", 10, 1000L, List.of(), "x"));
  }

  @Test
  public void test_ctor_slotsCopiedDefensively() {
    List<HudSlot> source = new ArrayList<>(List.of(HudSlot.CENTER));
    HudSegment segment = new HudSegment("p", 10, 1000L, source, "x");
    source.add(HudSlot.LEFT);
    assertEquals(List.of(HudSlot.CENTER), segment.slots());
  }

  @Test
  public void test_ctor_textSeparatorsSanitizedToSpaces() {
    HudSegment segment = new HudSegment("p", 10, 1000L, List.of(HudSlot.CENTER), "abc\nd");
    assertEquals("a b c d", segment.text());
  }
}

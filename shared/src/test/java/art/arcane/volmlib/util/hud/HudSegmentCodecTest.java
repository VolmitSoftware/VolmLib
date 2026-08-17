package art.arcane.volmlib.util.hud;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HudSegmentCodecTest {
  @Test
  public void test_encodeDecode_roundTrip_recoversAllFields() {
    HudStampedSegment segment = new HudStampedSegment(40, 1000L, 2000L, 2500L, List.of(HudSlot.CENTER, HudSlot.LEFT), "adapt:status:sense", "§b⌂ §fMansion §eNE §7212m");
    List<HudStampedSegment> decoded = HudSegmentCodec.decode(HudSegmentCodec.encode(List.of(segment)));
    assertEquals(List.of(segment), decoded);
  }

  @Test
  public void test_encodeDecode_multipleSegments_allRecovered() {
    HudStampedSegment a = new HudStampedSegment(10, 1L, 2L, 3L, List.of(HudSlot.LEFT), "adapt:xp", "+12XP");
    HudStampedSegment b = new HudStampedSegment(30, 4L, 5L, 6L, List.of(HudSlot.CENTER, HudSlot.RIGHT), "adapt:notice", "Level up | nice");
    assertEquals(List.of(a, b), HudSegmentCodec.decode(HudSegmentCodec.encode(List.of(a, b))));
  }

  @Test
  public void test_decode_malformedInput_returnsEmpty() {
    assertTrue(HudSegmentCodec.decode(null).isEmpty());
    assertTrue(HudSegmentCodec.decode("").isEmpty());
    assertTrue(HudSegmentCodec.decode("garbage").isEmpty());
    assertTrue(HudSegmentCodec.decode("1|60|1|2|3|x").isEmpty());
  }

  @Test
  public void test_decode_foreignProtocolVersion_returnsEmpty() {
    String encoded = HudSegmentCodec.encode(List.of(new HudStampedSegment(10, 1L, 2L, 3L, List.of(HudSlot.LEFT), "p", "t")));
    assertTrue(HudSegmentCodec.decode("9" + encoded.substring(1)).isEmpty());
  }

  @Test
  public void test_decode_malformedRecord_skippedButValidKept() {
    HudStampedSegment good = new HudStampedSegment(10, 1L, 2L, 3L, List.of(HudSlot.LEFT), "p", "t");
    String raw = HudSegmentCodec.encode(List.of(good)) + HudSegmentCodec.RECORD_SEPARATOR + "not-a-record";
    assertEquals(List.of(good), HudSegmentCodec.decode(raw));
  }

  @Test
  public void test_decode_textWithPipesAndSections_survives() {
    HudStampedSegment segment = new HudStampedSegment(10, 1L, 2L, 3L, List.of(HudSlot.RIGHT), "wormholes:notice", "a|b§cred");
    assertEquals(List.of(segment), HudSegmentCodec.decode(HudSegmentCodec.encode(List.of(segment))));
  }
}

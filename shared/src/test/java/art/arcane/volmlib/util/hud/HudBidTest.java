package art.arcane.volmlib.util.hud;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HudBidTest {
  @Test
  public void test_encodeDecode_roundTrip_recoversAllFields() {
    HudBid bid = new HudBid(60, 1000L, 2000L, 1500L, "iris:studio-open");
    HudBid decoded = HudBid.decode(bid.encode());
    assertEquals(bid, decoded);
  }

  @Test
  public void test_decode_malformedInput_returnsNull() {
    assertNull(HudBid.decode(null));
    assertNull(HudBid.decode(""));
    assertNull(HudBid.decode("1|60|1|2"));
    assertNull(HudBid.decode("1|sixty|1|2|3|x"));
    assertNull(HudBid.decode("garbage"));
  }

  @Test
  public void test_decode_foreignProtocolVersion_returnsNull() {
    assertNull(HudBid.decode("2|60|1|2|3|x"));
    assertNull(HudBid.decode("0|60|1|2|3|x"));
  }

  @Test
  public void test_isExpired_boundsAtAssertedPlusTtl() {
    HudBid bid = new HudBid(10, 0L, 1000L, 500L, "p");
    assertFalse(bid.isExpired(1000L));
    assertFalse(bid.isExpired(1500L));
    assertTrue(bid.isExpired(1501L));
  }

  @Test
  public void test_encode_purposeWithColons_survivesRoundTrip() {
    HudBid bid = new HudBid(100, 5L, 6L, 7L, "wormholes:hold:extra");
    assertEquals(bid, HudBid.decode(bid.encode()));
  }
}

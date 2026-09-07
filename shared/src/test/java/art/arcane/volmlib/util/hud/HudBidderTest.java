package art.arcane.volmlib.util.hud;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class HudBidderTest {
  @Test
  public void test_winner_resolvesEveryTiebreakInOrder() {
    HudBidder highPriority = new HudBidder("Iris", new HudBid(60, 200L, 1000L, 1500L, "iris:studio-open"));
    HudBidder lowPriority = new HudBidder("React", new HudBid(10, 100L, 1000L, 1500L, "react:monitor"));
    HudBidder olderSince = new HudBidder("React", new HudBid(10, 100L, 1000L, 1500L, "react:monitor"));
    HudBidder newerSince = new HudBidder("Adapt", new HudBid(10, 900L, 1000L, 1500L, "adapt:xp"));
    HudBidder smallerPlugin = new HudBidder("Adapt", new HudBid(10, 100L, 1000L, 1500L, "adapt:xp"));
    HudBidder largerPlugin = new HudBidder("React", new HudBid(10, 100L, 1000L, 1500L, "react:monitor"));
    HudBidder smallerPurpose = new HudBidder("Iris", new HudBid(60, 100L, 1000L, 1500L, "iris:chunk-job"));
    HudBidder largerPurpose = new HudBidder("Iris", new HudBid(60, 100L, 1000L, 1500L, "iris:job"));

    assertSame("priority", highPriority, HudBidder.winner(List.of(lowPriority, highPriority), 1000L));
    assertSame("priority reversed", highPriority, HudBidder.winner(List.of(highPriority, lowPriority), 1000L));
    assertSame("since", olderSince, HudBidder.winner(List.of(newerSince, olderSince), 1000L));
    assertSame("plugin name", smallerPlugin, HudBidder.winner(List.of(largerPlugin, smallerPlugin), 1000L));
    assertSame("purpose", smallerPurpose, HudBidder.winner(List.of(largerPurpose, smallerPurpose), 1000L));
  }

  @Test
  public void test_winner_expiredBidsIgnored() {
    HudBidder expiredHigh = new HudBidder("Iris", new HudBid(100, 100L, 0L, 500L, "iris:studio-open"));
    HudBidder liveLow = new HudBidder("React", new HudBid(10, 900L, 900L, 1500L, "react:monitor"));
    assertNull(HudBidder.winner(List.of(expiredHigh), 1000L));
    assertSame(liveLow, HudBidder.winner(List.of(expiredHigh, liveLow), 1000L));
  }

  @Test
  public void test_winner_nullAndDecodeFailedEntriesSkipped() {
    HudBidder live = new HudBidder("React", new HudBid(10, 900L, 900L, 1500L, "react:monitor"));
    List<HudBidder> bidders = new ArrayList<>();
    bidders.add(null);
    bidders.add(new HudBidder("Broken", null));
    bidders.add(new HudBidder(null, new HudBid(100, 0L, 900L, 1500L, "x")));
    bidders.add(live);
    assertSame(live, HudBidder.winner(bidders, 1000L));
  }

  @Test
  public void test_winner_emptyList_returnsNull() {
    assertNull(HudBidder.winner(List.of(), 1000L));
  }
}

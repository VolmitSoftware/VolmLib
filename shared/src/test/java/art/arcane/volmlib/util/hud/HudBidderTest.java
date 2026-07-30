package art.arcane.volmlib.util.hud;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class HudBidderTest {
  @Test
  public void test_winner_higherPriorityWins() {
    HudBidder ambient = new HudBidder("React", new HudBid(10, 100L, 1000L, 1500L, "react:monitor"));
    HudBidder progress = new HudBidder("Iris", new HudBid(60, 200L, 1000L, 1500L, "iris:studio-open"));
    assertSame(progress, HudBidder.winner(List.of(ambient, progress), 1000L));
    assertSame(progress, HudBidder.winner(List.of(progress, ambient), 1000L));
  }

  @Test
  public void test_winner_earlierSinceWinsAtEqualPriority() {
    HudBidder older = new HudBidder("React", new HudBid(10, 100L, 1000L, 1500L, "react:monitor"));
    HudBidder newer = new HudBidder("Adapt", new HudBid(10, 900L, 1000L, 1500L, "adapt:xp"));
    assertSame(older, HudBidder.winner(List.of(newer, older), 1000L));
  }

  @Test
  public void test_winner_smallerPluginNameBreaksTies() {
    HudBidder adapt = new HudBidder("Adapt", new HudBid(10, 100L, 1000L, 1500L, "adapt:xp"));
    HudBidder react = new HudBidder("React", new HudBid(10, 100L, 1000L, 1500L, "react:monitor"));
    assertSame(adapt, HudBidder.winner(List.of(react, adapt), 1000L));
  }

  @Test
  public void test_winner_smallerPurposeBreaksFullTies() {
    HudBidder first = new HudBidder("Iris", new HudBid(60, 100L, 1000L, 1500L, "iris:chunk-job"));
    HudBidder second = new HudBidder("Iris", new HudBid(60, 100L, 1000L, 1500L, "iris:job"));
    assertSame(first, HudBidder.winner(List.of(second, first), 1000L));
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

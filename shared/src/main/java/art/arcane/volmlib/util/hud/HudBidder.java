package art.arcane.volmlib.util.hud;

import java.util.List;

public record HudBidder(String ownerName, HudBid bid) {
  public static HudBidder winner(List<HudBidder> bidders, long nowMillis) {
    HudBidder best = null;
    for (HudBidder candidate : bidders) {
      if (candidate == null || candidate.ownerName == null || candidate.bid == null || candidate.bid.isExpired(nowMillis)) {
        continue;
      }
      if (best == null || candidate.beats(best)) {
        best = candidate;
      }
    }
    return best;
  }

  private boolean beats(HudBidder other) {
    if (bid.priority() != other.bid.priority()) {
      return bid.priority() > other.bid.priority();
    }
    if (bid.sinceMillis() != other.bid.sinceMillis()) {
      return bid.sinceMillis() < other.bid.sinceMillis();
    }
    int ownerOrder = ownerName.compareTo(other.ownerName);
    if (ownerOrder != 0) {
      return ownerOrder < 0;
    }
    return bid.purpose().compareTo(other.bid.purpose()) < 0;
  }
}

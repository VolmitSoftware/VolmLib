package art.arcane.volmlib.util.hud;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class HudSlotRequestTest {
  @Test(expected = IllegalArgumentException.class)
  public void test_ctor_emptyPreferences_rejected() {
    new HudSlotRequest("p", HudPriority.NOTICE, 1500L, List.of());
  }

  @Test(expected = IllegalArgumentException.class)
  public void test_ctor_nonPositiveTtl_rejected() {
    new HudSlotRequest("p", HudPriority.NOTICE, 0L, List.of(HudSurface.ACTION_BAR));
  }

  @Test(expected = IllegalArgumentException.class)
  public void test_ctor_pipeInPurpose_rejected() {
    new HudSlotRequest("bad|purpose", HudPriority.NOTICE, 1500L, List.of(HudSurface.ACTION_BAR));
  }

  @Test(expected = NullPointerException.class)
  public void test_ctor_nullPurpose_rejected() {
    new HudSlotRequest(null, HudPriority.NOTICE, 1500L, List.of(HudSurface.ACTION_BAR));
  }

  @Test
  public void test_ctor_preferencesCopiedDefensively() {
    List<HudSurface> source = new ArrayList<>();
    source.add(HudSurface.ACTION_BAR);
    HudSlotRequest request = new HudSlotRequest("p", HudPriority.AMBIENT, 1500L, source);
    source.add(HudSurface.BOSS_BAR);
    assertEquals(List.of(HudSurface.ACTION_BAR), request.preferences());
  }

  @Test(expected = UnsupportedOperationException.class)
  public void test_preferences_immutable() {
    HudSlotRequest request = new HudSlotRequest("p", HudPriority.AMBIENT, 1500L, List.of(HudSurface.ACTION_BAR));
    request.preferences().add(HudSurface.BOSS_BAR);
  }
}

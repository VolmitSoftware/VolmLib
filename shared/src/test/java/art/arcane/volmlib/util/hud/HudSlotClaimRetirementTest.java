package art.arcane.volmlib.util.hud;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class HudSlotClaimRetirementTest {
  @Test
  public void retirementDoesNotCallTheRetiredPlayer() {
    Plugin plugin = mock(Plugin.class);
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    HudSlotService service = new HudSlotService(plugin);
    HudSlotRequest request = new HudSlotRequest(
      "download",
      HudPriority.PROGRESS,
      4_000L,
      List.of(HudSurface.ACTION_BAR, HudSurface.BOSS_BAR)
    );
    HudSlotClaim claim = new HudSlotClaim(service, player, request, 1L, 1L);
    clearInvocations(player);

    claim.retire();

    verifyNoInteractions(player);
  }
}

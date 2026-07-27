package art.arcane.volmlib.integration;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.junit.Test;

import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class VaultEconomyTest {
    @Test
    public void successfulWithdrawalRefundsExactlyOnce() {
        Harness harness = new Harness();
        when(harness.economy.has(harness.player, 12.5D)).thenReturn(true);
        when(harness.economy.withdrawPlayer(harness.player, 12.5D))
            .thenReturn(success(12.5D));
        when(harness.economy.depositPlayer(harness.player, 12.5D))
            .thenReturn(success(12.5D));

        VaultEconomy.ChargeResult result = harness.vault.withdraw(harness.player, 12.5D, "test travel");

        assertTrue(result.successful());
        assertTrue(result.charge().refund());
        assertTrue(result.charge().refund());
        verify(harness.economy, times(1)).depositPlayer(harness.player, 12.5D);
    }

    @Test
    public void committedWithdrawalCannotRefund() {
        Harness harness = new Harness();
        when(harness.economy.has(harness.player, 4D)).thenReturn(true);
        when(harness.economy.withdrawPlayer(harness.player, 4D))
            .thenReturn(success(4D));

        VaultEconomy.ChargeResult result = harness.vault.withdraw(harness.player, 4D, "test learning");

        assertTrue(result.charge().commit());
        assertFalse(result.charge().refund());
        verify(harness.economy, times(0)).depositPlayer(harness.player, 4D);
    }

    @Test
    public void failedRefundCanBeRetried() {
        Harness harness = new Harness();
        when(harness.economy.has(harness.player, 6D)).thenReturn(true);
        when(harness.economy.withdrawPlayer(harness.player, 6D)).thenReturn(success(6D));
        when(harness.economy.depositPlayer(harness.player, 6D))
            .thenReturn(new EconomyResponse(0D, 94D, EconomyResponse.ResponseType.FAILURE, "temporary failure"))
            .thenReturn(success(6D));

        VaultEconomy.ChargeResult result = harness.vault.withdraw(harness.player, 6D, "test retry");

        assertFalse(result.charge().refund());
        assertFalse(result.charge().settled());
        assertTrue(result.charge().refund());
        assertTrue(result.charge().settled());
        verify(harness.economy, times(2)).depositPlayer(harness.player, 6D);
    }

    @Test
    public void insufficientBalanceDoesNotWithdraw() {
        Harness harness = new Harness();
        when(harness.economy.has(harness.player, 9D)).thenReturn(false);

        VaultEconomy.ChargeResult result = harness.vault.withdraw(harness.player, 9D, "test");

        assertEquals(VaultEconomy.ChargeStatus.INSUFFICIENT_FUNDS, result.status());
        verify(harness.economy, times(0)).withdrawPlayer(harness.player, 9D);
    }

    @Test
    public void missingVaultIsReportedWithoutResolvingAProvider() {
        Harness harness = new Harness();
        when(harness.pluginManager.getPlugin("Vault")).thenReturn(null);

        assertEquals(VaultEconomy.Availability.VAULT_UNAVAILABLE, harness.vault.availability());
        assertEquals(
            VaultEconomy.ChargeStatus.VAULT_UNAVAILABLE,
            harness.vault.withdraw(harness.player, 1D, "test").status()
        );
    }

    private static EconomyResponse success(double amount) {
        return new EconomyResponse(amount, 100D, EconomyResponse.ResponseType.SUCCESS, "");
    }

    private static final class Harness {
        private final Plugin plugin = mock(Plugin.class);
        private final Server server = mock(Server.class);
        private final PluginManager pluginManager = mock(PluginManager.class);
        private final ServicesManager servicesManager = mock(ServicesManager.class);
        private final Plugin vaultPlugin = mock(Plugin.class);
        private final Economy economy = mock(Economy.class);
        private final OfflinePlayer player = mock(OfflinePlayer.class);
        private final RegisteredServiceProvider<Economy> registration;
        private final VaultEconomy vault;

        @SuppressWarnings("unchecked")
        private Harness() {
            registration = mock(RegisteredServiceProvider.class);
            when(plugin.getServer()).thenReturn(server);
            when(plugin.getLogger()).thenReturn(Logger.getLogger("VaultEconomyTest"));
            when(server.getPluginManager()).thenReturn(pluginManager);
            when(server.getServicesManager()).thenReturn(servicesManager);
            when(pluginManager.getPlugin("Vault")).thenReturn(vaultPlugin);
            when(vaultPlugin.isEnabled()).thenReturn(true);
            when(servicesManager.getRegistration(Economy.class)).thenReturn(registration);
            when(registration.getProvider()).thenReturn(economy);
            vault = new VaultEconomy(plugin);
        }
    }
}

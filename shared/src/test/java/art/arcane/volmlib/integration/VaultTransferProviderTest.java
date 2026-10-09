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

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class VaultTransferProviderTest {
    @Test
    public void exactSuccessfulTransfersUseCapturedProviderWithoutCompensation() {
        Harness harness = new Harness();
        VaultEconomy.TransferProvider selected = harness.vault.transferProvider().orElseThrow();
        assertEquals("Provider/Coins", selected.identity());
        for (VaultEconomy.TransferDirection direction : VaultEconomy.TransferDirection.values()) {
            assertEquals(VaultEconomy.TransferStatus.SUCCESS, selected.transfer(harness.player, request(direction)).status());
        }
        verify(harness.economy).withdrawPlayer(harness.player, 12D);
        verify(harness.economy).depositPlayer(harness.player, 12D);
    }

    @Test
    public void disabledVaultOwnerOrEconomyRejectsSelection() {
        Harness harness = new Harness();
        when(harness.vaultPlugin.isEnabled()).thenReturn(false);
        assertTrue(harness.vault.transferProvider().isEmpty());
        when(harness.vaultPlugin.isEnabled()).thenReturn(true);
        when(harness.owner.isEnabled()).thenReturn(false);
        assertTrue(harness.vault.transferProvider().isEmpty());
        when(harness.owner.isEnabled()).thenReturn(true);
        when(harness.economy.isEnabled()).thenReturn(false);
        assertTrue(harness.vault.transferProvider().isEmpty());
    }

    @Test
    public void replacingRegistrationWithSameProviderRejectsPinnedTransfer() {
        Harness harness = new Harness();
        VaultEconomy.TransferProvider selected = harness.vault.transferProvider().orElseThrow();
        RegisteredServiceProvider<Economy> replacement = mock(RegisteredServiceProvider.class);
        when(replacement.getProvider()).thenReturn(harness.economy);
        when(replacement.getPlugin()).thenReturn(harness.owner);
        when(harness.services.getRegistration(Economy.class)).thenReturn(replacement);
        assertFalse(selected.current());
        assertEquals(VaultEconomy.TransferStatus.REJECTED, selected.transfer(harness.player, request(VaultEconomy.TransferDirection.DEPOSIT)).status());
        verify(harness.economy, never()).depositPlayer(any(OfflinePlayer.class), anyDouble());
    }

    @Test
    public void accountAndFundsChecksRejectBeforeMutationIncludingExceptions() {
        Harness harness = new Harness();
        VaultEconomy.TransferProvider selected = harness.vault.transferProvider().orElseThrow();
        when(harness.economy.hasAccount(harness.player)).thenReturn(false);
        assertEquals(VaultEconomy.TransferStatus.REJECTED, selected.transfer(harness.player, request(VaultEconomy.TransferDirection.WITHDRAW)).status());
        when(harness.economy.hasAccount(harness.player)).thenReturn(true);
        when(harness.economy.has(harness.player, 12D)).thenReturn(false);
        assertEquals(VaultEconomy.TransferStatus.REJECTED, selected.transfer(harness.player, request(VaultEconomy.TransferDirection.WITHDRAW)).status());
        when(harness.economy.has(harness.player, 12D)).thenThrow(new IllegalStateException("balance unavailable"));
        assertEquals(VaultEconomy.TransferStatus.REJECTED, selected.transfer(harness.player, request(VaultEconomy.TransferDirection.WITHDRAW)).status());
        verify(harness.economy, never()).withdrawPlayer(any(OfflinePlayer.class), anyDouble());
    }

    @Test
    public void invalidAmountsNeverCallMoneyMutation() {
        Harness harness = new Harness();
        VaultEconomy.TransferProvider selected = harness.vault.transferProvider().orElseThrow();
        for (double amount : new double[]{0D, -1D, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertEquals(VaultEconomy.TransferStatus.REJECTED,
                    selected.transfer(harness.player, new VaultEconomy.TransferRequest(VaultEconomy.TransferDirection.DEPOSIT, amount, "test")).status());
        }
        assertEquals(VaultEconomy.TransferStatus.REJECTED, selected.transfer(null, request(VaultEconomy.TransferDirection.DEPOSIT)).status());
        verify(harness.economy, never()).depositPlayer(any(OfflinePlayer.class), anyDouble());
    }

    @Test
    public void definiteZeroAmountFailureIsRejectedButPartialAndMalformedResponsesAreUnknown() {
        Harness harness = new Harness();
        VaultEconomy.TransferProvider selected = harness.vault.transferProvider().orElseThrow();
        when(harness.economy.depositPlayer(harness.player, 12D)).thenReturn(new EconomyResponse(0D, 100D, EconomyResponse.ResponseType.FAILURE, "refused"));
        assertEquals(VaultEconomy.TransferStatus.REJECTED, selected.transfer(harness.player, request(VaultEconomy.TransferDirection.DEPOSIT)).status());
        List<EconomyResponse> malformed = List.of(
                new EconomyResponse(6D, 100D, EconomyResponse.ResponseType.SUCCESS, ""),
                new EconomyResponse(6D, 100D, EconomyResponse.ResponseType.FAILURE, "partial"),
                new EconomyResponse(12D, Double.NaN, EconomyResponse.ResponseType.SUCCESS, ""),
                new EconomyResponse(Double.NaN, 100D, EconomyResponse.ResponseType.FAILURE, ""),
                new EconomyResponse(0D, 100D, null, ""));
        for (EconomyResponse response : malformed) {
            when(harness.economy.depositPlayer(harness.player, 12D)).thenReturn(response);
            assertEquals(VaultEconomy.TransferStatus.UNKNOWN, selected.transfer(harness.player, request(VaultEconomy.TransferDirection.DEPOSIT)).status());
        }
        when(harness.economy.depositPlayer(harness.player, 12D)).thenReturn(null);
        assertEquals(VaultEconomy.TransferStatus.UNKNOWN, selected.transfer(harness.player, request(VaultEconomy.TransferDirection.DEPOSIT)).status());
        verify(harness.economy, never()).withdrawPlayer(any(OfflinePlayer.class), anyDouble());
    }

    @Test
    public void mutationExceptionsAreUnknownAndLogFullFailureWithoutCompensation() {
        Harness harness = new Harness();
        VaultEconomy.TransferProvider selected = harness.vault.transferProvider().orElseThrow();
        IllegalStateException failure = new IllegalStateException("response lost after debit");
        when(harness.economy.withdrawPlayer(harness.player, 12D)).thenThrow(failure);
        assertEquals(VaultEconomy.TransferStatus.UNKNOWN, selected.transfer(harness.player, request(VaultEconomy.TransferDirection.WITHDRAW)).status());
        verify(harness.logger).log(eq(Level.SEVERE), contains("unknown"), eq(failure));
        verify(harness.economy, never()).depositPlayer(any(OfflinePlayer.class), anyDouble());
    }

    @Test
    public void providerReplacementDuringFundsCheckRejectsBeforeMutation() {
        Harness harness = new Harness();
        VaultEconomy.TransferProvider selected = harness.vault.transferProvider().orElseThrow();
        when(harness.economy.has(harness.player, 12D)).thenAnswer(ignored -> {
            when(harness.services.getRegistration(Economy.class)).thenReturn(null);
            return true;
        });
        assertEquals(VaultEconomy.TransferStatus.REJECTED, selected.transfer(harness.player, request(VaultEconomy.TransferDirection.WITHDRAW)).status());
        verify(harness.economy, never()).withdrawPlayer(any(OfflinePlayer.class), anyDouble());
    }

    private static VaultEconomy.TransferRequest request(VaultEconomy.TransferDirection direction) {
        return new VaultEconomy.TransferRequest(direction, 12D, "island transfer");
    }

    private static final class Harness {
        private final Plugin plugin = mock(Plugin.class);
        private final Plugin vaultPlugin = mock(Plugin.class);
        private final Plugin owner = mock(Plugin.class);
        private final Economy economy = mock(Economy.class);
        private final OfflinePlayer player = mock(OfflinePlayer.class);
        private final ServicesManager services = mock(ServicesManager.class);
        private final Logger logger = mock(Logger.class);
        private final VaultEconomy vault;

        private Harness() {
            Server server = mock(Server.class);
            PluginManager manager = mock(PluginManager.class);
            RegisteredServiceProvider<Economy> registration = mock(RegisteredServiceProvider.class);
            when(plugin.getServer()).thenReturn(server);
            when(plugin.getLogger()).thenReturn(logger);
            when(server.getPluginManager()).thenReturn(manager);
            when(server.getServicesManager()).thenReturn(services);
            when(manager.getPlugin("Vault")).thenReturn(vaultPlugin);
            when(vaultPlugin.isEnabled()).thenReturn(true);
            when(owner.isEnabled()).thenReturn(true);
            when(owner.getName()).thenReturn("Provider");
            when(economy.getName()).thenReturn("Coins");
            when(economy.isEnabled()).thenReturn(true);
            when(economy.hasAccount(player)).thenReturn(true);
            when(economy.has(player, 12D)).thenReturn(true);
            when(economy.withdrawPlayer(player, 12D)).thenReturn(new EconomyResponse(12D, 88D, EconomyResponse.ResponseType.SUCCESS, ""));
            when(economy.depositPlayer(player, 12D)).thenReturn(new EconomyResponse(12D, 112D, EconomyResponse.ResponseType.SUCCESS, ""));
            when(services.getRegistration(Economy.class)).thenReturn(registration);
            when(registration.getPlugin()).thenReturn(owner);
            when(registration.getProvider()).thenReturn(economy);
            vault = new VaultEconomy(plugin);
        }
    }
}

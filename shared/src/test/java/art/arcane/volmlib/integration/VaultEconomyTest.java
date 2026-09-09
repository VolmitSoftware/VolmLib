package art.arcane.volmlib.integration;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import io.papermc.paper.plugin.configuration.PluginMeta;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;

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

    @Test
    public void disabledRegistrationOwnerCannotHandleNewTransactions() {
        Harness harness = new Harness();
        when(harness.providerOwner.isEnabled()).thenReturn(false);

        assertEquals(VaultEconomy.Availability.PROVIDER_UNAVAILABLE, harness.vault.availability());
        assertEquals(VaultEconomy.ChargeStatus.PROVIDER_UNAVAILABLE,
            harness.vault.withdraw(harness.player, 1D, "disabled provider").status());
        assertFalse(harness.vault.canAfford(harness.player, 1D));
        assertFalse(harness.vault.deposit(harness.player, 1D, "disabled provider"));
        verifyNoInteractions(harness.economy);
    }

    @Test
    public void ordinaryBukkitProviderDoesNotRequireFoliaMetadata() {
        Harness harness = new Harness();
        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.isFoliaThreading(harness.server)).thenReturn(false);
            when(harness.economy.has(harness.player, 5D)).thenReturn(true);
            when(harness.economy.withdrawPlayer(harness.player, 5D)).thenReturn(success(5D));

            assertEquals(VaultEconomy.Availability.AVAILABLE, harness.vault.availability());
            assertTrue(harness.vault.withdraw(harness.player, 5D, "Bukkit provider").successful());
            verify(harness.economy).withdrawPlayer(harness.player, 5D);
        }
    }

    @Test
    public void foliaRejectsProviderWithoutExplicitSupport() {
        Harness harness = new Harness();
        FoliaMetadata metadata = mock(FoliaMetadata.class);
        harness.provider(metadata);
        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.isFoliaThreading(harness.server)).thenReturn(true);

            assertEquals(VaultEconomy.Availability.PROVIDER_UNAVAILABLE, harness.vault.availability());
            assertEquals(VaultEconomy.ChargeStatus.PROVIDER_UNAVAILABLE,
                harness.vault.withdraw(harness.player, 5D, "unsupported provider").status());
            assertFalse(harness.vault.deposit(harness.player, 5D, "unsupported provider"));
            verifyNoInteractions(harness.economy);
            verify(metadata, times(1)).isFoliaSupported();
        }
    }

    @Test
    public void foliaSupportIsCachedWhileOwnerEnablementRemainsLive() {
        Harness harness = new Harness();
        FoliaMetadata metadata = mock(FoliaMetadata.class);
        when(metadata.isFoliaSupported()).thenReturn(true);
        Plugin owner = harness.provider(metadata);
        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.isFoliaThreading(harness.server)).thenReturn(true);
            assertTrue(harness.vault.isAvailable());
            assertTrue(harness.vault.isAvailable());
            when(owner.isEnabled()).thenReturn(false);
            assertFalse(harness.vault.isAvailable());
            when(owner.isEnabled()).thenReturn(true);
            assertTrue(harness.vault.isAvailable());
            verify(metadata, times(1)).isFoliaSupported();
        }
    }

    @Test
    public void aReplacementRegistrationOwnerGetsItsOwnSupportCheck() {
        Harness harness = new Harness();
        FoliaMetadata supported = mock(FoliaMetadata.class);
        when(supported.isFoliaSupported()).thenReturn(true);
        harness.provider(supported);
        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.isFoliaThreading(harness.server)).thenReturn(true);
            assertTrue(harness.vault.isAvailable());
            FoliaMetadata unsupported = mock(FoliaMetadata.class);
            harness.provider(unsupported);
            assertFalse(harness.vault.isAvailable());
            verify(supported, times(1)).isFoliaSupported();
            verify(unsupported, times(1)).isFoliaSupported();
        }
    }

    @Test
    public void metadataFailureRejectsProviderAndLogsItsStackOnce() {
        Harness harness = new Harness();
        FoliaMetadata metadata = mock(FoliaMetadata.class);
        when(metadata.isFoliaSupported()).thenThrow(new IllegalStateException("metadata failure"));
        harness.provider(metadata);
        Logger logger = mock(Logger.class);
        when(harness.plugin.getLogger()).thenReturn(logger);
        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.isFoliaThreading(harness.server)).thenReturn(true);
            assertFalse(harness.vault.isAvailable());
            assertFalse(harness.vault.isAvailable());
            verifyNoInteractions(harness.economy);
            verify(logger, times(1)).log(eq(Level.WARNING), contains("Folia support"), any(Throwable.class));
        }
    }

    public interface FoliaMetadata extends PluginMeta {
        boolean isFoliaSupported();
    }

    public interface MetadataPlugin {
        FoliaMetadata getPluginMeta();
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
        private final Plugin providerOwner = mock(Plugin.class);
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
            when(registration.getPlugin()).thenReturn(providerOwner);
            when(providerOwner.isEnabled()).thenReturn(true);
            vault = new VaultEconomy(plugin);
        }

        private Plugin provider(FoliaMetadata metadata) {
            Plugin owner = mock(Plugin.class, withSettings().extraInterfaces(MetadataPlugin.class));
            when(owner.isEnabled()).thenReturn(true);
            when(owner.getName()).thenReturn("EconomyProvider");
            when(((MetadataPlugin) owner).getPluginMeta()).thenReturn(metadata);
            when(registration.getPlugin()).thenReturn(owner);
            return owner;
        }
    }
}

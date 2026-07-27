package art.arcane.volmlib.integration;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public final class VaultEconomy {
    private final Plugin plugin;

    public VaultEconomy(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public Availability availability() {
        Plugin vault = plugin.getServer().getPluginManager().getPlugin("Vault");
        if (vault == null || !vault.isEnabled()) {
            return Availability.VAULT_UNAVAILABLE;
        }
        return provider() == null ? Availability.PROVIDER_UNAVAILABLE : Availability.AVAILABLE;
    }

    public boolean isAvailable() {
        return availability() == Availability.AVAILABLE;
    }

    public boolean canAfford(OfflinePlayer player, double amount) {
        if (player == null || !validAmount(amount)) {
            return false;
        }
        Economy economy = provider();
        if (economy == null) {
            return false;
        }
        try {
            return economy.has(player, amount);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                "Vault economy provider failed while checking the balance for " + player.getUniqueId(), exception);
            return false;
        }
    }

    public String format(double amount) {
        Economy economy = provider();
        if (economy == null) {
            return String.format(Locale.ROOT, "%.2f", amount);
        }
        try {
            return economy.format(amount);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Vault economy provider failed while formatting " + amount, exception);
            return String.format(Locale.ROOT, "%.2f", amount);
        }
    }

    public ChargeResult withdraw(OfflinePlayer player, double amount, String context) {
        if (player == null || !validAmount(amount)) {
            return ChargeResult.failed(ChargeStatus.INVALID_AMOUNT, "Invalid Vault charge amount");
        }
        Availability availability = availability();
        if (availability != Availability.AVAILABLE) {
            return ChargeResult.failed(
                availability == Availability.VAULT_UNAVAILABLE
                    ? ChargeStatus.VAULT_UNAVAILABLE
                    : ChargeStatus.PROVIDER_UNAVAILABLE,
                availability.name()
            );
        }
        Economy economy = provider();
        if (economy == null) {
            return ChargeResult.failed(ChargeStatus.PROVIDER_UNAVAILABLE, "No Vault economy provider is registered");
        }
        try {
            if (!economy.has(player, amount)) {
                return ChargeResult.failed(ChargeStatus.INSUFFICIENT_FUNDS, "Insufficient funds");
            }
            EconomyResponse response = economy.withdrawPlayer(player, amount);
            if (response == null || !response.transactionSuccess()) {
                String error = response == null ? "Vault provider returned no response" : response.errorMessage;
                return ChargeResult.failed(ChargeStatus.TRANSACTION_FAILED, error);
            }
            return ChargeResult.success(new Charge(plugin, economy, player, amount, normalizeContext(context)));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                "Vault economy provider failed while withdrawing " + amount + " for " + normalizeContext(context), exception);
            return ChargeResult.failed(ChargeStatus.TRANSACTION_FAILED, exception.getMessage());
        }
    }

    public boolean deposit(OfflinePlayer player, double amount, String context) {
        if (player == null || !validAmount(amount)) {
            return false;
        }
        Economy economy = provider();
        if (economy == null) {
            plugin.getLogger().warning("Could not deposit " + amount + " for " + normalizeContext(context)
                + " because no Vault economy provider is available");
            return false;
        }
        return deposit(plugin, economy, player, amount, normalizeContext(context));
    }

    private Economy provider() {
        Plugin vault = plugin.getServer().getPluginManager().getPlugin("Vault");
        if (vault == null || !vault.isEnabled()) {
            return null;
        }
        RegisteredServiceProvider<Economy> registration =
            plugin.getServer().getServicesManager().getRegistration(Economy.class);
        return registration == null ? null : registration.getProvider();
    }

    private static boolean deposit(
        Plugin plugin,
        Economy economy,
        OfflinePlayer player,
        double amount,
        String context
    ) {
        try {
            EconomyResponse response = economy.depositPlayer(player, amount);
            if (response != null && response.transactionSuccess()) {
                return true;
            }
            String error = response == null ? "Vault provider returned no response" : response.errorMessage;
            plugin.getLogger().severe("Vault refund failed for " + context + ": " + error);
            return false;
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Vault refund failed for " + context, exception);
            return false;
        }
    }

    private static boolean validAmount(double amount) {
        return Double.isFinite(amount) && amount > 0D;
    }

    private static String normalizeContext(String context) {
        return context == null || context.isBlank() ? "unspecified transaction" : context;
    }

    public enum Availability {
        AVAILABLE,
        VAULT_UNAVAILABLE,
        PROVIDER_UNAVAILABLE
    }

    public enum ChargeStatus {
        SUCCESS,
        INVALID_AMOUNT,
        VAULT_UNAVAILABLE,
        PROVIDER_UNAVAILABLE,
        INSUFFICIENT_FUNDS,
        TRANSACTION_FAILED
    }

    public record ChargeResult(ChargeStatus status, Charge charge, String error) {
        public static ChargeResult success(Charge charge) {
            return new ChargeResult(ChargeStatus.SUCCESS, Objects.requireNonNull(charge, "charge"), "");
        }

        public static ChargeResult failed(ChargeStatus status, String error) {
            if (status == ChargeStatus.SUCCESS) {
                throw new IllegalArgumentException("A failed Vault charge cannot use SUCCESS status");
            }
            return new ChargeResult(status, null, error == null ? "" : error);
        }

        public boolean successful() {
            return status == ChargeStatus.SUCCESS && charge != null;
        }
    }

    public static final class Charge {
        private final Plugin plugin;
        private final Economy economy;
        private final OfflinePlayer player;
        private final double amount;
        private final String context;
        private final AtomicReference<Settlement> settlement;

        private Charge(
            Plugin plugin,
            Economy economy,
            OfflinePlayer player,
            double amount,
            String context
        ) {
            this.plugin = plugin;
            this.economy = economy;
            this.player = player;
            this.amount = amount;
            this.context = context;
            settlement = new AtomicReference<>(Settlement.PENDING);
        }

        public double amount() {
            return amount;
        }

        public boolean commit() {
            return settlement.compareAndSet(Settlement.PENDING, Settlement.COMMITTED);
        }

        public boolean refund() {
            if (!settlement.compareAndSet(Settlement.PENDING, Settlement.REFUNDING)) {
                return settlement.get() == Settlement.REFUNDED;
            }
            if (deposit(plugin, economy, player, amount, context)) {
                settlement.set(Settlement.REFUNDED);
                return true;
            }
            settlement.compareAndSet(Settlement.REFUNDING, Settlement.PENDING);
            return false;
        }

        public boolean settled() {
            Settlement current = settlement.get();
            return current == Settlement.COMMITTED || current == Settlement.REFUNDED;
        }
    }

    private enum Settlement {
        PENDING,
        REFUNDING,
        COMMITTED,
        REFUNDED
    }
}

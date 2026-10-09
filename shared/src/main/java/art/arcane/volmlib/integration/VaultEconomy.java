package art.arcane.volmlib.integration;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public final class VaultEconomy {
    private final Plugin plugin;
    private final Map<Plugin, Boolean> foliaSupport = Collections.synchronizedMap(new WeakHashMap<>());

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

    public Optional<TransferProvider> transferProvider() {
        try {
            Economy economy = provider();
            if (economy == null) { return Optional.empty(); }
            RegisteredServiceProvider<Economy> registration = plugin.getServer().getServicesManager().getRegistration(Economy.class);
            if (registration == null || registration.getProvider() != economy) { return Optional.empty(); }
            TransferProvider selected = new TransferProvider(registration, economy);
            return selected.current() ? Optional.of(selected) : Optional.empty();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not select a Vault transfer provider", exception);
            return Optional.empty();
        }
    }

    private Economy provider() {
        Plugin vault = plugin.getServer().getPluginManager().getPlugin("Vault");
        if (vault == null || !vault.isEnabled()) {
            return null;
        }
        RegisteredServiceProvider<Economy> registration =
            plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null) {
            return null;
        }
        Plugin owner = registration.getPlugin();
        if (owner == null || !owner.isEnabled()) {
            return null;
        }
        if (FoliaScheduler.isFoliaThreading(plugin.getServer()) && !foliaSupport.computeIfAbsent(owner, this::supportsFolia)) {
            return null;
        }
        return registration.getProvider();
    }

    private boolean supportsFolia(Plugin owner) {
        try {
            Object metadata;
            Class<?> metadataType;
            try {
                Method getter = owner.getClass().getMethod("getPluginMeta");
                metadata = getter.invoke(owner);
                metadataType = getter.getReturnType();
            } catch (NoSuchMethodException exception) {
                metadata = owner.getDescription();
                metadataType = metadata == null ? Object.class : metadata.getClass();
            }
            if (metadata == null) {
                throw new IllegalStateException("Economy provider returned no plugin metadata");
            }
            Method supported;
            try {
                supported = metadataType.getMethod("isFoliaSupported");
            } catch (NoSuchMethodException exception) {
                supported = metadata.getClass().getMethod("isFoliaSupported");
            }
            return Boolean.TRUE.equals(supported.invoke(metadata));
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                "Could not verify Folia support for Vault economy provider " + owner.getName(), exception);
            return false;
        }
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

    public enum TransferDirection {
        DEPOSIT,
        WITHDRAW
    }

    public enum TransferStatus {
        SUCCESS,
        REJECTED,
        UNKNOWN
    }

    public record TransferRequest(TransferDirection direction, double amount, String context) {
    }

    public record TransferResult(TransferStatus status, String error) {
        public TransferResult {
            Objects.requireNonNull(status);
            error = error == null ? "" : error;
        }
    }

    public final class TransferProvider {
        private final RegisteredServiceProvider<Economy> registration;
        private final Economy economy;
        private final Plugin owner;
        private final String identity;

        private TransferProvider(RegisteredServiceProvider<Economy> registration, Economy economy) {
            this.registration = registration;
            this.economy = economy;
            owner = Objects.requireNonNull(registration.getPlugin());
            identity = owner.getName() + "/" + economy.getName();
        }

        public String identity() { return identity; }

        public boolean available() {
            try {
                Plugin vault = plugin.getServer().getPluginManager().getPlugin("Vault");
                return vault != null && vault.isEnabled() && owner.isEnabled() && economy.isEnabled()
                        && (!FoliaScheduler.isFoliaThreading(plugin.getServer()) || foliaSupport.computeIfAbsent(owner, VaultEconomy.this::supportsFolia));
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not check Vault transfer provider " + identity, exception);
                return false;
            }
        }

        public boolean current() {
            try {
                return plugin.getServer().getServicesManager().getRegistration(Economy.class) == registration
                        && registration.getProvider() == economy && registration.getPlugin() == owner && available();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not check current Vault transfer provider " + identity, exception);
                return false;
            }
        }

        public TransferResult transfer(OfflinePlayer player, TransferRequest request) {
            if (player == null || request == null || request.direction() == null || !validAmount(request.amount())) {
                return new TransferResult(TransferStatus.REJECTED, "Invalid Vault transfer request");
            }
            if (!current()) { return new TransferResult(TransferStatus.REJECTED, "Vault transfer provider changed or is unavailable"); }
            try {
                if (!economy.hasAccount(player)) { return new TransferResult(TransferStatus.REJECTED, "Vault account is unavailable"); }
                if (request.direction() == TransferDirection.WITHDRAW && !economy.has(player, request.amount())) {
                    return new TransferResult(TransferStatus.REJECTED, "Insufficient funds");
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Vault transfer validation failed for " + normalizeContext(request.context()), exception);
                return new TransferResult(TransferStatus.REJECTED, exception.toString());
            }
            if (!current()) { return new TransferResult(TransferStatus.REJECTED, "Vault transfer provider changed during validation"); }
            try {
                EconomyResponse response = request.direction() == TransferDirection.WITHDRAW
                        ? economy.withdrawPlayer(player, request.amount()) : economy.depositPlayer(player, request.amount());
                if (response == null || response.type == null || !Double.isFinite(response.amount) || !Double.isFinite(response.balance)) {
                    return unknown(request, new IllegalStateException("Vault provider returned a missing or malformed transfer response"));
                }
                if (response.transactionSuccess() && Double.compare(response.amount, request.amount()) == 0) {
                    return new TransferResult(TransferStatus.SUCCESS, "");
                }
                if (!response.transactionSuccess() && response.amount == 0D) {
                    return new TransferResult(TransferStatus.REJECTED, response.errorMessage);
                }
                return unknown(request, new IllegalStateException("Vault transfer response reported an inconsistent amount: " + response.amount));
            } catch (RuntimeException exception) {
                return unknown(request, exception);
            }
        }

        private TransferResult unknown(TransferRequest request, RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "Vault transfer outcome is unknown for " + normalizeContext(request.context())
                    + " via " + identity + " (" + request.direction() + " " + request.amount() + "); do not retry automatically", failure);
            return new TransferResult(TransferStatus.UNKNOWN, failure.toString());
        }
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

package art.arcane.volmlib.util.localization;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import art.arcane.volmlib.util.bukkit.BukkitCommandMaps;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.entity.Player;
import org.bukkit.help.HelpMap;
import org.bukkit.help.HelpTopic;
import org.bukkit.help.IndexHelpTopic;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

final class BukkitVolmitCommand extends Command implements PluginIdentifiableCommand {
    private static final String ROOT = "volmit";
    private static final String NAMESPACED_ROOT = "volmit:volmit";

    private final Plugin plugin;
    private final BukkitLanguageSwitcher switcher;
    private CommandMap registeredMap;
    private Map<String, Command> registeredCommands;
    private HelpTopic helpTopic;
    private Command displaced;
    private Map<?, ?> displacedProvider;
    private HelpTopic displacedHelp;
    private volatile boolean released;

    BukkitVolmitCommand(Plugin plugin, BukkitLanguageSwitcher switcher) {
        super(ROOT, "Manage Volmit plugins", "/volmit plugins [languages|debug|updates]", List.of());
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.switcher = Objects.requireNonNull(switcher, "switcher");
    }

    void claim() {
        if (released || registeredMap != null) {
            return;
        }
        Server server = plugin.getServer();
        try {
            CommandMap commandMap = BukkitCommandMaps.commandMap(server);
            Map<String, Command> knownCommands = BukkitCommandMaps.knownCommands(commandMap);
            synchronized (commandMap) {
                retireOlderOwner(server, commandMap, knownCommands);
                if (knownCommands.containsKey(ROOT) || knownCommands.containsKey(NAMESPACED_ROOT)) {
                    return;
                }
                try {
                    boolean registered = commandMap.register(ROOT, ROOT, this);
                    if (!registered || knownCommands.get(ROOT) != this || knownCommands.get(NAMESPACED_ROOT) != this) {
                        throw new IllegalStateException("The command map rejected /volmit registration");
                    }
                } catch (RuntimeException exception) {
                    removeOwned(knownCommands);
                    unregister(commandMap);
                    throw exception;
                }
                registeredMap = commandMap;
                registeredCommands = knownCommands;
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Unable to register the shared /volmit command", exception);
            return;
        }
        installHelpTopic(server);
        refreshCommands(server);
    }

    private void retireOlderOwner(Server server, CommandMap commandMap, Map<String, Command> commands) {
        Command existing = commands.get(ROOT);
        if (existing == null || existing != commands.get(NAMESPACED_ROOT)
                || !(existing instanceof PluginIdentifiableCommand identified)
                || !existing.getClass().getSimpleName().equals("BukkitVolmitCommand")) {
            return;
        }
        for (RegisteredServiceProvider<?> registration : server.getServicesManager().getRegistrations(Map.class)) {
            if (registration.getPlugin() != identified.getPlugin() || !(registration.getProvider() instanceof Map<?, ?> values)
                    || !"2".equals(values.get("volmit.language.protocol")) || values.containsKey("command.revision")) {
                continue;
            }
            commands.remove(ROOT, existing);
            commands.remove(NAMESPACED_ROOT, existing);
            existing.unregister(commandMap);
            displaced = existing;
            displacedProvider = values;
            HelpMap help = server.getHelpMap();
            if (help != null) {
                HelpTopic previous = help.getHelpTopic("/" + ROOT);
                if (previous instanceof IndexHelpTopic) {
                    displacedHelp = previous;
                    help.getHelpTopics().removeIf(topic -> topic == previous);
                }
            }
            return;
        }
    }

    void release() {
        released = true;
        CommandMap commandMap = registeredMap;
        Map<String, Command> knownCommands = registeredCommands;
        registeredMap = null;
        registeredCommands = null;
        if (commandMap == null || knownCommands == null) {
            return;
        }
        synchronized (commandMap) {
            removeOwned(knownCommands);
            unregister(commandMap);
        }
        removeHelpTopic(plugin.getServer());
        restoreDisplaced(commandMap, knownCommands);
        refreshCommands(plugin.getServer());
    }

    private void restoreDisplaced(CommandMap commandMap, Map<String, Command> commands) {
        Command previous = displaced;
        displaced = null;
        if (previous == null) {
            return;
        }
        synchronized (commandMap) {
            boolean available = plugin.getServer().getServicesManager().getRegistrations(Map.class).stream()
                    .anyMatch(registration -> registration.getProvider() == displacedProvider && registration.getPlugin().isEnabled());
            if (!available || commands.containsKey(ROOT) || commands.containsKey(NAMESPACED_ROOT)) {
                return;
            }
            previous.register(commandMap);
            commands.put(ROOT, previous);
            commands.put(NAMESPACED_ROOT, previous);
            if (displacedHelp != null && plugin.getServer().getHelpMap().getHelpTopic("/" + ROOT) == null) {
                plugin.getServer().getHelpMap().addTopic(displacedHelp);
            }
        }
    }

    @Override
    public Plugin getPlugin() {
        return plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] arguments) {
        return released || switcher.commandVolmit(sender, arguments);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] arguments) {
        return released ? List.of() : switcher.completeVolmit(sender, arguments);
    }

    private void removeOwned(Map<String, Command> knownCommands) {
        if (knownCommands.get(ROOT) == this) {
            knownCommands.remove(ROOT);
        }
        if (knownCommands.get(NAMESPACED_ROOT) == this) {
            knownCommands.remove(NAMESPACED_ROOT);
        }
    }

    private void installHelpTopic(Server server) {
        HelpMap help = server.getHelpMap();
        if (help == null || help.getHelpTopic("/" + ROOT) != null) {
            return;
        }
        HelpTopic topic = new IndexHelpTopic("/" + ROOT, getDescription(), null, List.of(), getUsage());
        help.addTopic(topic);
        if (help.getHelpTopic("/" + ROOT) == topic) {
            helpTopic = topic;
        }
    }

    private void removeHelpTopic(Server server) {
        HelpTopic owned = helpTopic;
        helpTopic = null;
        if (owned != null) {
            server.getHelpMap().getHelpTopics().removeIf(topic -> topic == owned);
        }
    }

    private void refreshCommands(Server server) {
        Plugin schedulerOwner = schedulerOwner(server);
        if (schedulerOwner == null) {
            return;
        }
        if (!FoliaScheduler.isFoliaThreading(server)) {
            if (!FoliaScheduler.runGlobal(schedulerOwner, () -> synchronizeCommands(server), 1L)) {
                plugin.getLogger().warning("Unable to schedule the shared /volmit command tree refresh");
            }
            return;
        }
        List<Player> players = new ArrayList<>(server.getOnlinePlayers());
        for (Player player : players) {
            if (!FoliaScheduler.runEntity(schedulerOwner, player, player::updateCommands)) {
                plugin.getLogger().warning("Unable to schedule a player command refresh after /volmit changed");
            }
        }
    }

    private Plugin schedulerOwner(Server server) {
        if (plugin.isEnabled()) {
            return plugin;
        }
        for (RegisteredServiceProvider<?> registration : server.getServicesManager().getRegistrations(Map.class)) {
            if (registration.getPlugin().isEnabled() && registration.getProvider() instanceof Map<?, ?> provider
                    && "2".equals(provider.get("volmit.language.protocol"))) {
                return registration.getPlugin();
            }
        }
        return null;
    }

    private void synchronizeCommands(Server server) {
        try {
            Method sync = server.getClass().getMethod("syncCommands");
            sync.invoke(server);
        } catch (NoSuchMethodException exception) {
            return;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Unable to synchronize the shared /volmit command tree", exception);
        }
    }

}

package art.arcane.volmlib.util.localization;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.help.HelpMap;
import org.bukkit.help.HelpTopic;
import org.bukkit.help.IndexHelpTopic;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.SimpleServicesManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class BukkitVolmitCommandTest {
    private final Map<String, Command> commands = new LinkedHashMap<>();
    private final Map<String, HelpTopic> helpTopics = new LinkedHashMap<>();
    private final List<Plugin> refreshOwners = new ArrayList<>();
    private final List<Runnable> refreshTasks = new ArrayList<>();
    private Server server;
    private CommandMap commandMap;
    private SimpleServicesManager services;
    private Plugin plugin;
    private BukkitLanguageSwitcher switcher;
    private MockedStatic<FoliaScheduler> scheduler;
    private MockedStatic<Bukkit> bukkit;
    private boolean folia;

    @Before
    public void setup() {
        server = mock(Server.class, withSettings().extraInterfaces(CommandServer.class));
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        commandMap = mock(CommandMap.class, withSettings().extraInterfaces(KnownCommandMap.class));
        services = new SimpleServicesManager();
        when(((CommandServer) server).getCommandMap()).thenReturn(commandMap);
        when(((KnownCommandMap) commandMap).getKnownCommands()).thenReturn(commands);
        when(server.getServicesManager()).thenReturn(services);
        HelpMap help = mock(HelpMap.class);
        when(server.getHelpMap()).thenReturn(help);
        when(help.getHelpTopic(anyString())).thenAnswer(invocation -> helpTopics.get(invocation.getArgument(0)));
        when(help.getHelpTopics()).thenReturn(helpTopics.values());
        doAnswer(invocation -> {
            HelpTopic topic = invocation.getArgument(0);
            helpTopics.putIfAbsent(topic.getName(), topic);
            return null;
        }).when(help).addTopic(any(HelpTopic.class));
        when(commandMap.register(anyString(), anyString(), any(Command.class))).thenAnswer(invocation -> {
            String label = invocation.getArgument(0);
            String namespace = invocation.getArgument(1);
            Command command = invocation.getArgument(2);
            commands.put(label, command);
            commands.put(namespace + ":" + label, command);
            command.register(commandMap);
            return true;
        });
        plugin = plugin("Adapt");
        switcher = mock(BukkitLanguageSwitcher.class);
        scheduler = mockStatic(FoliaScheduler.class, invocation -> {
            if (invocation.getMethod().getName().equals("isFoliaThreading")) {
                return folia;
            }
            if (invocation.getMethod().getName().equals("runEntity")) {
                refreshOwners.add(invocation.getArgument(0));
                refreshTasks.add(invocation.getArgument(2));
                return true;
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
    }

    @After
    public void cleanup() {
        scheduler.close();
        bukkit.close();
    }

    @Test
    public void claimsBothLabelsOnceAndRoutesTheEntireRootArguments() {
        BukkitVolmitCommand command = new BukkitVolmitCommand(plugin, switcher);
        command.claim();
        command.claim();
        CommandSender sender = mock(CommandSender.class);
        String[] arguments = {"plugins", "languages", "fr_FR"};
        when(switcher.commandVolmit(sender, arguments)).thenReturn(true);
        when(switcher.completeVolmit(sender, arguments)).thenReturn(List.of("fr_FR"));

        assertSame(command, commands.get("volmit"));
        assertSame(command, commands.get("volmit:volmit"));
        assertSame(plugin, command.getPlugin());
        assertTrue(command.isRegistered());
        assertSame(IndexHelpTopic.class, helpTopics.get("/volmit").getClass());
        assertTrue(command.execute(sender, "volmit", arguments));
        assertEquals(List.of("fr_FR"), command.tabComplete(sender, "volmit", arguments));
        verify(commandMap, times(1)).register("volmit", "volmit", command);
        verify(((CommandServer) server), times(1)).syncCommands();
        verify(switcher).commandVolmit(sender, arguments);
        verify(switcher).completeVolmit(sender, arguments);
    }

    @Test
    public void preservesForeignRootAndNamespacedRegistrations() {
        Command foreign = mock(Command.class);
        commands.put("volmit", foreign);
        BukkitVolmitCommand command = new BukkitVolmitCommand(plugin, switcher);
        command.claim();
        command.release();

        assertSame(foreign, commands.get("volmit"));
        assertFalse(commands.containsKey("volmit:volmit"));
        commands.clear();
        commands.put("volmit:volmit", foreign);
        new BukkitVolmitCommand(plugin, switcher).claim();

        assertSame(foreign, commands.get("volmit:volmit"));
        assertFalse(commands.containsKey("volmit"));
        verify(commandMap, never()).register(anyString(), anyString(), any(Command.class));
    }

    @Test
    public void releasesBothLabelsBeforeHandoffAndRetiresThePreviousCallback() {
        BukkitVolmitCommand first = new BukkitVolmitCommand(plugin, switcher);
        BukkitVolmitCommand second = new BukkitVolmitCommand(plugin("Iris"), mock(BukkitLanguageSwitcher.class));
        first.claim();
        second.claim();
        assertSame(first, commands.get("volmit"));

        first.release();
        assertTrue(commands.isEmpty());
        assertTrue(helpTopics.isEmpty());
        assertFalse(first.isRegistered());
        first.claim();
        assertTrue(commands.isEmpty());
        second.claim();
        assertSame(second, commands.get("volmit"));
        assertSame(second, commands.get("volmit:volmit"));
        second.release();
        assertTrue(commands.isEmpty());
        assertTrue(helpTopics.isEmpty());
        assertFalse(second.isRegistered());
        assertTrue(first.execute(mock(CommandSender.class), "volmit", new String[]{"plugins"}));
        assertTrue(first.tabComplete(mock(CommandSender.class), "volmit", new String[]{""}).isEmpty());
    }

    @Test
    public void releasePreservesLabelsReplacedByAnotherCommand() {
        BukkitVolmitCommand command = new BukkitVolmitCommand(plugin, switcher);
        command.claim();
        Command replacement = mock(Command.class);
        commands.put("volmit", replacement);
        commands.put("unrelated", replacement);
        HelpTopic replacementTopic = new IndexHelpTopic("/volmit", "Foreign help", null, List.of(), "Foreign help");
        helpTopics.put("/volmit", replacementTopic);

        command.release();

        assertSame(replacement, commands.get("volmit"));
        assertSame(replacement, commands.get("unrelated"));
        assertSame(replacementTopic, helpTopics.get("/volmit"));
        assertFalse(commands.containsKey("volmit:volmit"));
        assertFalse(command.isRegistered());
    }

    @Test
    public void removesPartialRegistrationWhenTheCommandMapRejectsTheRoot() {
        when(commandMap.register(anyString(), anyString(), any(Command.class))).thenAnswer(invocation -> {
            Command command = invocation.getArgument(2);
            commands.put("volmit:volmit", command);
            command.register(commandMap);
            return false;
        });
        BukkitVolmitCommand command = new BukkitVolmitCommand(plugin, switcher);

        command.claim();

        assertTrue(commands.isEmpty());
        assertFalse(command.isRegistered());
        when(commandMap.register(anyString(), anyString(), any(Command.class))).thenAnswer(invocation -> {
            Command attempted = invocation.getArgument(2);
            commands.put("volmit:volmit", attempted);
            attempted.register(commandMap);
            throw new IllegalStateException("Registration failed");
        });
        command.claim();
        assertTrue(commands.isEmpty());
        assertFalse(command.isRegistered());
        verify(((CommandServer) server), never()).syncCommands();
    }

    @Test
    public void foliaRefreshesOnEntitySchedulersAndUsesTheNextVolmitOwnerDuringDisable() {
        folia = true;
        Player player = mock(Player.class);
        doReturn(List.of(player)).when(server).getOnlinePlayers();
        BukkitVolmitCommand command = new BukkitVolmitCommand(plugin, switcher);

        command.claim();

        assertEquals(List.of(plugin), refreshOwners);
        verify(player, never()).updateCommands();
        refreshTasks.get(0).run();
        verify(player).updateCommands();
        Plugin next = plugin("Iris");
        services.register(Map.class, Map.of("volmit.language.protocol", "2"), next, ServicePriority.Normal);
        when(plugin.isEnabled()).thenReturn(false);
        command.release();

        assertEquals(List.of(plugin, next), refreshOwners);
        verify(((CommandServer) server), never()).syncCommands();
        assertTrue(commands.isEmpty());
    }

    @Test
    public void finalDisabledFoliaOwnerReleasesWithoutSchedulingUnderAnInactivePlugin() {
        folia = true;
        Player player = mock(Player.class);
        doReturn(List.of(player)).when(server).getOnlinePlayers();
        BukkitVolmitCommand command = new BukkitVolmitCommand(plugin, switcher);
        command.claim();
        when(plugin.isEnabled()).thenReturn(false);

        command.release();

        assertTrue(commands.isEmpty());
        assertFalse(command.isRegistered());
        assertEquals(List.of(plugin), refreshOwners);
    }

    private Plugin plugin(String name) {
        Plugin result = mock(Plugin.class);
        when(result.getName()).thenReturn(name);
        when(result.getServer()).thenReturn(server);
        when(result.getLogger()).thenReturn(mock(Logger.class));
        when(result.isEnabled()).thenReturn(true);
        return result;
    }

    public interface CommandServer {
        CommandMap getCommandMap();

        void syncCommands();
    }

    public interface KnownCommandMap {
        Map<String, Command> getKnownCommands();
    }
}

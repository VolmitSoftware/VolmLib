package art.arcane.volmlib.util.update;

import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.ComponentText;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.SimpleServicesManager;
import org.bukkit.plugin.ServicePriority;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class BukkitUpdateServiceTest {
    private Server server;
    private Player player;
    private SimpleServicesManager services;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<FoliaScheduler> scheduler;
    private MockedStatic<ComponentMessenger> messages;
    private final List<Runnable> tasks = new ArrayList<>();
    private final List<Plugin> owners = new ArrayList<>();
    private final List<BukkitUpdateService> registrations = new ArrayList<>();

    @Before
    public void setup() {
        server = mock(Server.class);
        services = new SimpleServicesManager();
        when(server.getServicesManager()).thenReturn(services);
        PluginManager manager = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(manager);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
        player = mock(Player.class, withSettings().extraInterfaces(CommandSender.class, Entity.class));
        when(((CommandSender) player).isOp()).thenReturn(true);
        when(player.isOnline()).thenReturn(true);
        scheduler = mockStatic(FoliaScheduler.class);
        scheduler.when(() -> FoliaScheduler.runEntity(any(), eq((Entity) player), any(), eq(20L))).thenAnswer(call -> {
            owners.add(call.getArgument(0));
            tasks.add(call.getArgument(2));
            return true;
        });
        messages = mockStatic(ComponentMessenger.class);
    }

    @After
    public void cleanup() {
        registrations.forEach(BukkitUpdateService::close);
        messages.close();
        scheduler.close();
        bukkit.close();
    }

    @Test
    public void multipleProvidersProduceExactlyOneCombinedNotice() {
        Fixture first = fixture("Adapt");
        Fixture second = fixture("ShapedPortals");
        first.service.onJoin(join());
        second.service.onJoin(join());
        first.response.complete(update("Adapt"));
        assertTrue(tasks.isEmpty());
        second.response.complete(update("ShapedPortals"));
        assertEquals(1, tasks.size());
        tasks.get(0).run();
        ArgumentCaptor<ComponentText> text = ArgumentCaptor.forClass(ComponentText.class);
        messages.verify(() -> ComponentMessenger.sendRunCommand(eq(player), text.capture(), eq("/volmit plugins updates"), any()));
        assertTrue(text.getValue().plain().contains("Adapt, ShapedPortals"));
        assertEquals("Volmit • 2 plugin updates available\n  Adapt, ShapedPortals  [View updates]", text.getValue().plain());
        assertTrue(text.getValue().miniMessage().toLowerCase(Locale.ROOT).contains("#32bfad"));
    }

    @Test
    public void largeJoinNoticeKeepsAllPluginNamesInHoverWithoutFillingChat() {
        List<Fixture> fixtures = List.of(fixture("Adapt"), fixture("Gloss"), fixture("Iris"), fixture("Wormholes"));
        fixtures.get(0).service.onJoin(join());
        for (Fixture fixture : fixtures) {
            fixture.response.complete(update(fixture.plugin.getName()));
        }
        tasks.get(0).run();
        ArgumentCaptor<ComponentText> text = ArgumentCaptor.forClass(ComponentText.class);
        ArgumentCaptor<ComponentText> hover = ArgumentCaptor.forClass(ComponentText.class);
        messages.verify(() -> ComponentMessenger.sendRunCommand(eq(player), text.capture(), eq("/volmit plugins updates"), hover.capture()));
        assertEquals("Volmit • 4 plugin updates available\n  Adapt, Gloss, Iris +1 more  [View updates]", text.getValue().plain());
        assertTrue(hover.getValue().plain().contains("Wormholes"));
    }

    @Test
    public void disabledCoordinatorStillAggregatesOtherProvidersAndOptOutCancelsQueuedNotice() {
        Fixture first = fixture("Adapt");
        Fixture second = fixture("ShapedPortals");
        first.enabled.set(false);
        first.service.reconfigure();
        first.service.onJoin(join());
        second.response.complete(update("ShapedPortals"));
        verify(first.checker, never()).checkReport(anyBoolean());
        second.enabled.set(false);
        tasks.get(0).run();
        messages.verifyNoInteractions();
    }

    @Test
    public void permissionsAndConnectionAreRecheckedBeforeDelivery() {
        Fixture first = fixture("Adapt");
        first.service.onJoin(join());
        first.response.complete(update("Adapt"));
        when(((CommandSender) player).isOp()).thenReturn(false);
        tasks.get(0).run();
        when(((CommandSender) player).isOp()).thenReturn(true);
        when(player.isOnline()).thenReturn(false);
        tasks.get(0).run();
        messages.verifyNoInteractions();
    }

    @Test
    public void unloadingCoordinatorBeforeCompletionUsesRemainingOwnerAndDropsRemovedResult() {
        Fixture first = fixture("Adapt");
        Fixture second = fixture("ShapedPortals");
        PlayerJoinEvent event = join();
        first.service.onJoin(event);
        first.service.close();
        second.service.onJoin(event);
        first.response.complete(update("Adapt"));
        second.response.complete(update("ShapedPortals"));
        assertEquals(List.of(second.plugin), owners);
        tasks.get(0).run();
        ArgumentCaptor<ComponentText> text = ArgumentCaptor.forClass(ComponentText.class);
        messages.verify(() -> ComponentMessenger.sendRunCommand(eq(player), text.capture(), any(), any()));
        assertFalse(text.getValue().plain().contains("Adapt"));
        assertTrue(text.getValue().plain().contains("ShapedPortals"));
    }

    @Test
    public void commandCombinesFailuresDisabledAndSuccessfulProvidersInOneReport() {
        Fixture first = fixture("Adapt");
        Fixture second = fixture("ShapedPortals");
        Fixture third = fixture("Wormholes");
        third.enabled.set(false);
        third.service.reconfigure();
        BukkitUpdateService.command(first.plugin, (CommandSender) player, art.arcane.volmlib.util.director.help.DirectorMiniMenu.Theme.reactBlue(), art.arcane.volmlib.util.director.DirectorTextResolver.ENGLISH);
        first.response.completeExceptionally(new IllegalStateException("network"));
        second.response.complete(update("ShapedPortals"));
        tasks.get(0).run();
        ArgumentCaptor<ComponentText> text = ArgumentCaptor.forClass(ComponentText.class);
        messages.verify(() -> ComponentMessenger.send(eq((CommandSender) player), text.capture()), org.mockito.Mockito.atLeastOnce());
        String output = text.getAllValues().stream().map(ComponentText::plain).collect(java.util.stream.Collectors.joining("\n"));
        String markup = text.getAllValues().stream().map(ComponentText::miniMessage).collect(java.util.stream.Collectors.joining("\n"));
        assertTrue(output.contains("/volmit plugins updates"));
        assertTrue(output.contains("Back"));
        assertTrue(output.contains("Check failed (1)"));
        assertTrue(output.contains("Updates available (1)"));
        assertTrue(output.contains("[View release]"));
        assertTrue(output.contains("Checks disabled (1)"));
        assertTrue(output.indexOf("ShapedPortals") < output.indexOf("Adapt"));
        assertTrue(output.indexOf("Adapt") < output.indexOf("Wormholes"));
        assertFalse(output.contains("Installed:"));
        assertFalse(output.contains("Latest release:"));
        assertTrue(markup.contains("Installed: 1.0"));
        assertTrue(markup.contains("Latest release: 2.0"));
        assertTrue(markup.contains("check failed;"));
        assertTrue(markup.contains("https://github.com/VolmitSoftware/ShapedPortals/releases/tag/2.0"));
        verify(first.checker).checkReport(true);
        verify(second.checker).checkReport(true);
        verify(third.checker, never()).checkReport(anyBoolean());
    }

    @Test
    public void protocolUsesOnlySharedJavaTypesAndAcceptsIndependentProvider() {
        Fixture first = fixture("Adapt");
        Plugin foreign = plugin("ShapedPortals");
        Map<String, Object> independent = Map.of("volmit.update.protocol", "1", "name", "ShapedPortals",
                "installed", "1.0", "permission", "shapedportals.update", "enabled", (BooleanSupplier) () -> true,
                "generation", (Supplier<Long>) () -> 1L,
                "check", (Function<Boolean, CompletableFuture<Map<String, String>>>) fresh -> CompletableFuture.completedFuture(
                        Map.of("status", "UPDATE", "version", "2.0", "url", "https://github.com/VolmitSoftware/ShapedPortals/releases/tag/2.0")));
        services.register(Map.class, independent, foreign, ServicePriority.Normal);
        first.service.onJoin(join());
        first.response.complete(update("Adapt"));
        tasks.get(0).run();
        ArgumentCaptor<ComponentText> text = ArgumentCaptor.forClass(ComponentText.class);
        messages.verify(() -> ComponentMessenger.sendRunCommand(eq(player), text.capture(), any(), any()));
        assertTrue(text.getValue().plain().contains("Adapt, ShapedPortals"));
    }

    @Test
    public void ordinaryPlayersDoNotCheckAndPerPluginPermissionFiltersResults() {
        Fixture first = fixture("Adapt");
        Fixture second = fixture("ShapedPortals");
        when(((CommandSender) player).isOp()).thenReturn(false);
        first.service.onJoin(join());
        verify(first.checker, never()).checkReport(anyBoolean());
        when(((CommandSender) player).hasPermission("shapedportals.update")).thenReturn(true);
        first.service.onJoin(join());
        second.response.complete(update("ShapedPortals"));
        verify(first.checker, never()).checkReport(anyBoolean());
        tasks.get(0).run();
        ArgumentCaptor<ComponentText> text = ArgumentCaptor.forClass(ComponentText.class);
        messages.verify(() -> ComponentMessenger.sendRunCommand(eq(player), text.capture(), any(), any()));
        assertFalse(text.getValue().plain().contains("Adapt"));
    }

    private PlayerJoinEvent join() {
        return new PlayerJoinEvent(player, (String) null);
    }

    private Fixture fixture(String name) {
        Plugin plugin = plugin(name);
        AtomicBoolean enabled = new AtomicBoolean(true);
        GitHubReleaseChecker checker = mock(GitHubReleaseChecker.class);
        CompletableFuture<GitHubReleaseChecker.Report> response = new CompletableFuture<>();
        when(checker.checkReport(anyBoolean())).thenReturn(response);
        BukkitUpdateService service = new BukkitUpdateService(plugin,
                new BukkitUpdateService.Options("VolmitSoftware", name, name.toLowerCase(Locale.ROOT) + ".update", enabled::get), checker);
        service.register();
        registrations.add(service);
        return new Fixture(plugin, enabled, checker, response, service);
    }

    private Plugin plugin(String name) {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn(name);
        when(plugin.getDescription()).thenReturn(new PluginDescriptionFile(name, "1.0", "example.Main"));
        when(plugin.getServer()).thenReturn(server);
        when(plugin.isEnabled()).thenReturn(true);
        return plugin;
    }

    private GitHubReleaseChecker.Report update(String name) {
        return new GitHubReleaseChecker.Report(GitHubReleaseChecker.Status.UPDATE,
                new GitHubReleaseChecker.Release("2.0", "https://github.com/VolmitSoftware/" + name + "/releases/tag/2.0"), "", 1L, 1L);
    }

    private record Fixture(Plugin plugin, AtomicBoolean enabled, GitHubReleaseChecker checker,
                           CompletableFuture<GitHubReleaseChecker.Report> response, BukkitUpdateService service) {
    }
}

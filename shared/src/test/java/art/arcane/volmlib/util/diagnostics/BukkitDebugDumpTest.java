package art.arcane.volmlib.util.diagnostics;

import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import art.arcane.volmlib.util.web.MclogsClient;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.SimpleServicesManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BukkitDebugDumpTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private final List<String> messages = new ArrayList<>();
    private Plugin plugin;
    private PluginManager pluginManager;
    private SimpleServicesManager services;
    private TestPlayer player;
    private BukkitDebugSnapshot snapshot;
    private Path directory;
    private MockedStatic<FoliaScheduler> scheduler;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<BukkitDebugSnapshot> snapshots;
    private MockedStatic<DebugDumpReport> reports;
    private MockedConstruction<MclogsClient> clients;

    @Before
    public void setup() throws IOException {
        directory = temporary.newFolder("plugin").toPath();
        plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        PluginDescriptionFile description = mock(PluginDescriptionFile.class);
        services = new SimpleServicesManager();
        pluginManager = mock(PluginManager.class);
        when(plugin.getName()).thenReturn("ShapedPortals");
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getDescription()).thenReturn(description);
        when(description.getVersion()).thenReturn("2.0.0");
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(server.getServicesManager()).thenReturn(services);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        player = mock(TestPlayer.class);
        when(player.hasPermission("shapedportals.debugdump")).thenReturn(true);
        doAnswer(invocation -> messages.add(invocation.getArgument(0, String.class)))
                .when(player).sendRichMessage(anyString());
        snapshot = mock(BukkitDebugSnapshot.class);
        when(snapshot.generatedAt()).thenReturn(Instant.parse("2026-09-03T00:00:00Z"));
        when(snapshot.dataDirectory()).thenReturn(directory);
        when(snapshot.pluginName()).thenReturn("ShapedPortals");
        when(snapshot.pluginVersion()).thenReturn("2.0.0");
        snapshots = mockStatic(BukkitDebugSnapshot.class);
        snapshots.when(() -> BukkitDebugSnapshot.capture(plugin, player)).thenReturn(snapshot);
        reports = mockStatic(DebugDumpReport.class);
        reports.when(() -> DebugDumpReport.create(eq(snapshot), anyString()))
                .thenAnswer(invocation -> "Diagnostic report\n" + invocation.getArgument(1, String.class));
        clients = mockConstruction(MclogsClient.class);
        scheduler = mockStatic(FoliaScheduler.class);
        scheduler.when(() -> FoliaScheduler.runGlobal(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return true;
        });
        scheduler.when(() -> FoliaScheduler.runEntity(eq(plugin), any(), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(2, Runnable.class).run();
            return true;
        });
        scheduler.when(() -> FoliaScheduler.runAsync(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return true;
        });
    }

    @After
    public void cleanup() {
        scheduler.close();
        bukkit.close();
        clients.close();
        reports.close();
        snapshots.close();
    }

    @Test
    public void dedicatedPermissionIsRequiredBeforeCapture() {
        when(player.hasPermission("shapedportals.debugdump")).thenReturn(false);
        BukkitDebugDump dumps = BukkitDebugDump.create(plugin);

        dumps.request(player, true);

        snapshots.verifyNoInteractions();
        assertFalse(Files.exists(directory.resolve("debug")));
        assertTrue(String.join("\n", messages).contains("Missing permission: shapedportals.debugdump"));
    }

    @Test
    public void lifecyclePublishesAndRemovesTheSharedDebugProvider() {
        BukkitDebugDump dumps = BukkitDebugDump.create(plugin);

        assertEquals(1, services.getRegistrations(Map.class).size());
        Map<?, ?> provider = (Map<?, ?>) services.getRegistrations(Map.class).get(0).getProvider();
        assertEquals("1", provider.get("volmit.debug.protocol"));
        assertEquals("ShapedPortals", provider.get("name"));
        assertEquals("2.0.0", provider.get("version"));
        assertEquals("shapedportals.debugdump", provider.get("permission"));
        assertTrue(provider.get("request.aggregate") instanceof BiFunction<?, ?, ?>);

        dumps.close();

        assertTrue(services.getRegistrations(Map.class).isEmpty());
    }

    @Test
    public void existingPluginDebugPermissionIsReused() {
        when(pluginManager.getPermission("shapedportals.debug"))
                .thenReturn(new Permission("shapedportals.debug"));

        BukkitDebugDump dumps = BukkitDebugDump.create(plugin);

        assertEquals("shapedportals.debug", dumps.permission());
        Map<?, ?> provider = (Map<?, ?>) services.getRegistrations(Map.class).get(0).getProvider();
        assertEquals("shapedportals.debug", provider.get("permission"));
    }

    @Test
    public void localOnlySavesContributorAndProvidesClipboardPath() throws Exception {
        BukkitDebugDump dumps = BukkitDebugDump.create(plugin,
                new BukkitDebugDump.Options(() -> true, () -> () -> "Portal count: 2",
                        new BukkitDebugDump.Presentation("/shapedportals debug dump", "/shapedportals debug",
                                DirectorMiniMenu.Theme.adaptRed(), DirectorTextResolver.ENGLISH)));

        dumps.request(player, false);

        List<Path> files = savedReports();
        assertEquals(1, files.size());
        assertEquals("shapedportals-v2.0.0-debugdump-2026-09-03-00-00-00.txt",
                files.get(0).getFileName().toString());
        assertEquals("Diagnostic report\nPortal count: 2", Files.readString(files.get(0)));
        String rendered = String.join("\n", messages);
        Matcher pathAction = Pattern.compile("click:copy_to_clipboard:'([^']+)'").matcher(rendered);
        assertTrue(pathAction.find());
        Path copiedPath = Path.of(pathAction.group(1));
        assertTrue(copiedPath.isAbsolute());
        assertEquals(files.get(0).getFileName(), copiedPath.getFileName());
        assertTrue(rendered.contains("<gradient:#8b0000:#ff4d4d>"));
        assertTrue(rendered.contains("/shapedportals debug dump"));
        assertTrue(rendered.contains("<click:run_command:/shapedportals debug>"));
        assertTrue(rendered.contains("〈 Back"));
        verify(clients.constructed().get(0), never()).publish(anyString(), anyString(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void aggregateRequestReturnsAResultWithoutRenderingAPluginMenu() throws Exception {
        BukkitDebugDump.create(plugin);
        Map<?, ?> provider = (Map<?, ?>) services.getRegistrations(Map.class).get(0).getProvider();
        BiFunction<CommandSender, Boolean, CompletableFuture<Map<String, String>>> aggregate =
                (BiFunction<CommandSender, Boolean, CompletableFuture<Map<String, String>>>)
                        provider.get("request.aggregate");

        Map<String, String> result = aggregate.apply(player, false).join();

        assertEquals("success", result.get("status"));
        assertEquals("ShapedPortals", result.get("name"));
        assertEquals("2.0.0", result.get("version"));
        assertEquals(savedReports().get(0).toAbsolutePath().normalize().toString(), result.get("path"));
        assertTrue(messages.isEmpty());
    }

    @Test
    public void uploadedLinkHasOneVisibleOpenAction() throws Exception {
        BukkitDebugDump dumps = BukkitDebugDump.create(plugin);
        when(clients.constructed().get(0).publish(anyString(), anyString(), anyString()))
                .thenReturn(URI.create("https://mclo.gs/Ab12"));

        dumps.request(player, true);

        assertEquals(1, savedReports().size());
        String rendered = String.join("\n", messages);
        assertTrue(rendered.contains("click:open_url:'https://mclo.gs/Ab12'"));
        assertTrue(rendered.contains("Open: https://mclo.gs/Ab12"));
        assertFalse(rendered.contains("click:copy_to_clipboard:'https://mclo.gs/Ab12'"));
        verify(clients.constructed().get(0)).publish(anyString(),
                eq("VolmitSoftware - ShapedPortals - v2.0.0"),
                eq("VolmitSoftware/ShapedPortals/2.0.0"));
    }

    @Test
    public void uploadFailureRetainsLocalReportAndAllowsAnotherRequest() throws Exception {
        BukkitDebugDump dumps = BukkitDebugDump.create(plugin);
        when(snapshot.generatedAt()).thenReturn(
                Instant.parse("2026-09-03T00:00:00Z"),
                Instant.parse("2026-09-03T00:00:01Z"));
        when(clients.constructed().get(0).publish(anyString(), anyString(), anyString()))
                .thenThrow(new IOException("offline"));

        dumps.request(player, true);
        dumps.request(player, false);

        assertEquals(2, savedReports().size());
        assertTrue(String.join("\n", messages).contains("upload failed; the local report is saved"));
    }

    @Test
    public void uploadSettingIsReadForEachRequest() throws Exception {
        AtomicBoolean enabled = new AtomicBoolean(false);
        when(snapshot.generatedAt()).thenReturn(
                Instant.parse("2026-09-03T00:00:00Z"),
                Instant.parse("2026-09-03T00:00:01Z"));
        BukkitDebugDump dumps = BukkitDebugDump.create(plugin,
                new BukkitDebugDump.Options(enabled::get, () -> () -> ""));
        MclogsClient client = clients.constructed().get(0);
        when(client.publish(anyString(), anyString(), anyString())).thenReturn(URI.create("https://mclo.gs/Ab12"));

        dumps.request(player, true);
        verify(client, never()).publish(anyString(), anyString(), anyString());
        enabled.set(true);
        dumps.request(player, true);

        verify(client).publish(anyString(), anyString(), anyString());
        assertEquals(2, savedReports().size());
    }

    @Test
    public void duplicateRequestIsRejectedAndClosePreventsPendingWork() {
        List<Runnable> pending = new ArrayList<>();
        scheduler.when(() -> FoliaScheduler.runGlobal(eq(plugin), any(Runnable.class)))
                .thenAnswer(invocation -> pending.add(invocation.getArgument(1, Runnable.class)));
        BukkitDebugDump dumps = BukkitDebugDump.create(plugin);

        dumps.request(player, false);
        dumps.request(player, false);
        assertEquals(1, pending.size());
        assertTrue(String.join("\n", messages).contains("already being prepared"));
        dumps.close();
        pending.get(0).run();

        snapshots.verifyNoInteractions();
        assertFalse(Files.exists(directory.resolve("debug")));
    }

    @Test
    public void failedContributorStillProducesCommonDiagnostics() throws Exception {
        BukkitDebugDump dumps = BukkitDebugDump.create(plugin,
                new BukkitDebugDump.Options(() -> true, () -> {
                    throw new IllegalStateException("unavailable");
                }));

        dumps.request(player, false);

        assertEquals("Diagnostic report\nPlugin diagnostic capture failed: IllegalStateException",
                Files.readString(savedReports().get(0)));
    }

    @Test
    public void existingOutputSymlinkIsNotWrittenThrough() throws Exception {
        Path elsewhere = temporary.newFolder("elsewhere").toPath();
        Files.createSymbolicLink(directory.resolve("debug"), elsewhere);

        BukkitDebugDump.create(plugin).request(player, false);

        try (Stream<Path> files = Files.list(elsewhere)) {
            assertEquals(0, files.count());
        }
        assertTrue(String.join("\n", messages).contains("Unable to write the debug dump"));
    }

    private List<Path> savedReports() throws IOException {
        try (Stream<Path> files = Files.list(directory.resolve("debug"))) {
            return files.toList();
        }
    }

    public interface TestPlayer extends Player, CommandSender {
        @Override
        Player.Spigot spigot();
    }
}

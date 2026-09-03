package art.arcane.volmlib.util.diagnostics;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import art.arcane.volmlib.util.web.MclogsClient;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
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
    private TestPlayer player;
    private BukkitDebugSnapshot snapshot;
    private Path directory;
    private MockedStatic<FoliaScheduler> scheduler;
    private MockedStatic<BukkitDebugSnapshot> snapshots;
    private MockedStatic<DebugDumpReport> reports;
    private MockedConstruction<MclogsClient> clients;

    @Before
    public void setup() throws IOException {
        directory = temporary.newFolder("plugin").toPath();
        plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        when(plugin.getName()).thenReturn("ShapedPortals");
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
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
    public void localOnlySavesContributorAndProvidesClipboardPath() throws Exception {
        BukkitDebugDump dumps = BukkitDebugDump.create(plugin,
                new BukkitDebugDump.Options(() -> true, () -> () -> "Portal count: 2"));

        dumps.request(player, false);

        List<Path> files = savedReports();
        assertEquals(1, files.size());
        assertEquals("Diagnostic report\nPortal count: 2", Files.readString(files.get(0)));
        assertTrue(String.join("\n", messages).contains("click:copy_to_clipboard"));
        assertTrue(String.join("\n", messages).contains("debug/" + files.get(0).getFileName()));
        verify(clients.constructed().get(0), never()).publish(anyString(), anyString(), anyString());
    }

    @Test
    public void uploadedLinkCanBeOpenedAndCopied() throws Exception {
        BukkitDebugDump dumps = BukkitDebugDump.create(plugin);
        when(clients.constructed().get(0).publish(anyString(), anyString(), anyString()))
                .thenReturn(URI.create("https://mclo.gs/Ab12"));

        dumps.request(player, true);

        assertEquals(1, savedReports().size());
        String rendered = String.join("\n", messages);
        assertTrue(rendered.contains("click:open_url:'https://mclo.gs/Ab12'"));
        assertTrue(rendered.contains("click:copy_to_clipboard:'https://mclo.gs/Ab12'"));
    }

    @Test
    public void uploadFailureRetainsLocalReportAndAllowsAnotherRequest() throws Exception {
        BukkitDebugDump dumps = BukkitDebugDump.create(plugin);
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

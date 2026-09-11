package art.arcane.volmlib.util.diagnostics;

import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.plugin.ComponentText;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import art.arcane.volmlib.util.web.MclogsClient;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentIteratorType;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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

    private final List<String> messages = new CopyOnWriteArrayList<>();
    private Plugin plugin;
    private Server server;
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
        server = mock(Server.class);
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
                                DirectorMiniMenu.Theme.adaptRed(), BukkitDebugDump.TextResolver.ENGLISH)));
        dumps.updateTheme(DirectorMiniMenu.Theme.reactBlue());

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
        List<Component> components = renderedComponents();
        assertTrue(hasColor(components, TextColor.color(0x003366)));
        assertTrue(hasColor(components, TextColor.color(0x00BFFF)));
        assertFalse(hasColor(components, TextColor.color(0x8B0000)));
        assertFalse(hasColor(components, TextColor.color(0xFF4D4D)));
        assertTrue(rendered.contains("/shapedportals debug dump"));
        assertTrue(hasRunCommand(components, "/shapedportals debug"));
        assertTrue(rendered.contains("〈 Back"));
        verify(clients.constructed().get(0), never()).publish(anyString(), anyString(), anyString());
    }

    @Test
    public void presentationResolverLocalizesDebugWorkflowMessages() {
        BukkitDebugDump.TextResolver resolver = (key, arguments) ->
                ComponentText.literal("localized:" + DirectorTextResolver.ENGLISH.resolve(key, arguments));
        BukkitDebugDump dumps = BukkitDebugDump.create(plugin,
                new BukkitDebugDump.Options(() -> true, () -> () -> "",
                        new BukkitDebugDump.Presentation("/shapedportals debug dump", "/shapedportals debug",
                                DirectorMiniMenu.Theme.adaptRed(), resolver)));

        dumps.request(player, false);

        String rendered = String.join("\n", messages);
        assertTrue(rendered.contains("localized:Preparing ShapedPortals debug dump"));
        assertTrue(rendered.contains("localized:Saved ShapedPortals debug dump"));
        assertTrue(rendered.contains("localized:Copy local path"));
    }

    @Test
    public void richMessagesKeepTheirPrefixesAndLiteralPathsThroughMenuDelivery() throws Exception {
        directory = temporary.newFolder("&4[ff0000]-reports").toPath();
        when(snapshot.dataDirectory()).thenReturn(directory);
        BukkitDebugDump dumps = BukkitDebugDump.create(plugin,
                new BukkitDebugDump.Options(() -> true, () -> () -> "",
                        new BukkitDebugDump.Presentation("/shapedportals debug dump", "/shapedportals debug",
                                DirectorMiniMenu.Theme.adaptRed(), this::styledDebugText)));

        dumps.request(player, false);

        String path = savedReports().get(0).toAbsolutePath().normalize().toString();
        String visible = visibleMessages();
        assertTrue(visible.contains("Local › Preparing ShapedPortals debug dump..."));
        assertTrue(visible.contains("Local › Saved Local debug dump to " + path + "."));
        assertFalse(visible.contains("<bold>"));
        assertFalse(visible.contains("<gradient:"));
        assertFalse(visible.contains("{prefix}"));
        List<Component> components = renderedComponents();
        assertTrue(hasColor(components, TextColor.color(0x6F35C5)));
        assertTrue(hasColor(components, TextColor.color(0x35135F)));
        assertFalse(hasColor(components, TextColor.color(0xFF0000)));
        assertFalse(hasColor(components, TextColor.color(0xAA0000)));
        assertTrue(components.stream().anyMatch(component ->
                ClickEvent.copyToClipboard(path).equals(component.clickEvent())));
        assertTrue(components.stream().anyMatch(component -> component.hoverEvent() != null
                && component.hoverEvent().value() instanceof Component hover
                && ComponentText.component(hover).plain().contains(path)));
    }

    @Test
    public void uploadedRichMessagesKeepOneExactOpenAction() throws Exception {
        BukkitDebugDump dumps = BukkitDebugDump.create(plugin,
                new BukkitDebugDump.Options(() -> true, () -> () -> "",
                        new BukkitDebugDump.Presentation("/shapedportals debug dump", "/shapedportals debug",
                                DirectorMiniMenu.Theme.adaptRed(), this::styledDebugText)));
        URI url = URI.create("https://mclo.gs/Ab12?first=1&4=literal");
        when(clients.constructed().get(0).publish(anyString(), anyString(), anyString())).thenReturn(url);

        dumps.request(player, true);

        String visible = visibleMessages();
        assertTrue(visible.contains("Local › Uploaded as VolmitSoftware - Local - v2.0.0."));
        assertTrue(visible.contains("Local › Open: " + url));
        assertFalse(visible.contains("<bold>"));
        assertFalse(visible.contains("<gradient:"));
        long links = renderedComponents().stream().filter(component ->
                ClickEvent.openUrl(url.toString()).equals(component.clickEvent())).count();
        assertEquals(1L, links);
    }

    @Test
    public void richFailureAndUploadNoticeStayFormattedAndAggregateExportsStayPlain() throws Exception {
        BukkitDebugDump dumps = BukkitDebugDump.create(plugin,
                new BukkitDebugDump.Options(() -> true, () -> () -> "",
                        new BukkitDebugDump.Presentation("/shapedportals debug dump", "/shapedportals debug",
                                DirectorMiniMenu.Theme.adaptRed(), this::styledDebugText)));
        when(clients.constructed().get(0).publish(anyString(), anyString(), anyString()))
                .thenThrow(new IOException("offline"));
        dumps.request(player, true);
        assertTrue(visibleMessages().contains("Local › Debug dump upload failed; the local report is saved."));
        assertFalse(visibleMessages().contains("<bold>"));
        assertTrue(hasColor(renderedComponents(), TextColor.color(0x6F35C5)));

        messages.clear();
        when(player.hasPermission("shapedportals.debugdump")).thenReturn(false);
        dumps.request(player, false);
        assertTrue(visibleMessages().contains("Local › Missing permission: shapedportals.debugdump"));
        assertFalse(visibleMessages().contains("<bold>"));
        Map<?, ?> provider = services.getRegistrations(Map.class).get(0).getProvider();
        @SuppressWarnings("unchecked")
        BiFunction<CommandSender, Boolean, CompletableFuture<Map<String, String>>> aggregate =
                (BiFunction<CommandSender, Boolean, CompletableFuture<Map<String, String>>>) provider.get("request.aggregate");
        assertEquals("Local › Missing permission: shapedportals.debugdump",
                aggregate.apply(player, false).join().get("error"));
    }

    @Test
    public void plainResolversKeepFormattingLikeTextLiteral() {
        BukkitDebugDump dumps = BukkitDebugDump.create(plugin,
                new BukkitDebugDump.Options(() -> true, () -> () -> "",
                        new BukkitDebugDump.Presentation("/shapedportals debug dump", "/shapedportals debug",
                                DirectorMiniMenu.Theme.adaptRed(), (key, arguments) -> ComponentText.literal(
                                        "<red>&4[ff0000] " + DirectorTextResolver.ENGLISH.resolve(key, arguments)))));

        dumps.request(player, false);

        assertTrue(visibleMessages().contains("<red>&4[ff0000] Saved ShapedPortals debug dump"));
        assertFalse(hasColor(renderedComponents(), TextColor.color(0xFF0000)));
        assertFalse(hasColor(renderedComponents(), TextColor.color(0xAA0000)));
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
    public void sameSecondRequestsAllocateDistinctReportsWithoutOverwrite() throws Exception {
        reports.when(() -> DebugDumpReport.create(eq(snapshot), anyString()))
                .thenReturn("First report", "Second report");
        BukkitDebugDump dumps = BukkitDebugDump.create(plugin);

        dumps.request(player, false);
        dumps.request(player, false);

        Path output = directory.resolve("debug");
        Path first = output.resolve("shapedportals-v2.0.0-debugdump-2026-09-03-00-00-00.txt");
        Path second = output.resolve("shapedportals-v2.0.0-debugdump-2026-09-03-00-00-00-2.txt");
        assertEquals("First report", Files.readString(first));
        assertEquals("Second report", Files.readString(second));
        assertEquals(2, savedReports().size());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void closeInterruptsAnActiveUploadAndCompletesTheAggregate() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> backgroundFailure = new AtomicReference<>();
        BukkitDebugDump dumps = BukkitDebugDump.create(plugin);
        MclogsClient client = clients.constructed().get(0);
        when(client.publish(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            entered.countDown();
            try {
                release.await();
                return URI.create("https://mclo.gs/too-late");
            } catch (InterruptedException exception) {
                interrupted.countDown();
                throw exception;
            }
        });
        Map<?, ?> provider = (Map<?, ?>) services.getRegistrations(Map.class).get(0).getProvider();
        BiFunction<CommandSender, Boolean, CompletableFuture<Map<String, String>>> aggregate =
                (BiFunction<CommandSender, Boolean, CompletableFuture<Map<String, String>>>)
                        provider.get("request.aggregate");
        ServicesManager closingServices = mock(ServicesManager.class);
        when(server.getServicesManager()).thenReturn(closingServices);
        Thread closer = new Thread(() -> {
            try {
                if (!entered.await(2L, TimeUnit.SECONDS)) {
                    throw new AssertionError("The upload did not start");
                }
                dumps.close();
            } catch (Throwable failure) {
                backgroundFailure.set(failure);
                release.countDown();
            }
        }, "VolmLib-Debug-Dump-Close-Test");
        closer.setDaemon(true);
        closer.start();

        try {
            CompletableFuture<Map<String, String>> result = aggregate.apply(player, true);
            Map<String, String> closedResult = result.get(2L, TimeUnit.SECONDS);
            assertEquals("failure", closedResult.get("status"));
            assertTrue(closedResult.get("error").toLowerCase().contains("closed"));
            assertTrue(interrupted.await(2L, TimeUnit.SECONDS));
            closer.join(2_000L);
            assertFalse(closer.isAlive());
            assertFalse(Thread.currentThread().isInterrupted());
            assertNull(backgroundFailure.get());
            assertTrue(messages.isEmpty());
        } finally {
            release.countDown();
            closer.join(2_000L);
        }
    }

    @Test
    public void closeClearsAQueuedWriterBeforeTheSchedulerRunsIt() throws Exception {
        List<Runnable> pending = new ArrayList<>();
        scheduler.when(() -> FoliaScheduler.runAsync(eq(plugin), any(Runnable.class)))
                .thenAnswer(invocation -> pending.add(invocation.getArgument(1, Runnable.class)));
        BukkitDebugDump dumps = BukkitDebugDump.create(plugin);
        MclogsClient client = clients.constructed().get(0);

        dumps.request(player, true);
        assertEquals(1, pending.size());
        dumps.close();
        pending.get(0).run();

        verify(client, never()).publish(anyString(), anyString(), anyString());
        assertFalse(Files.exists(directory.resolve("debug")));
    }

    @Test
    public void queuedFeedbackRechecksLifecycleBeforeDelivery() {
        List<Runnable> pending = new ArrayList<>();
        AtomicInteger scheduled = new AtomicInteger();
        scheduler.when(() -> FoliaScheduler.runEntity(eq(plugin), any(), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    Runnable delivery = invocation.getArgument(2, Runnable.class);
                    if (scheduled.getAndIncrement() == 0) {
                        delivery.run();
                    } else {
                        pending.add(delivery);
                    }
                    return true;
                });
        BukkitDebugDump dumps = BukkitDebugDump.create(plugin);

        dumps.request(player, false);
        assertEquals(1, pending.size());
        assertTrue(String.join("\n", messages).contains("Preparing"));
        assertFalse(String.join("\n", messages).contains("Saved"));
        int messagesBeforeClose = messages.size();
        dumps.close();
        pending.get(0).run();

        assertEquals(messagesBeforeClose, messages.size());
        assertFalse(String.join("\n", messages).contains("Saved"));
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

    @Test
    public void occupiedReportSymlinkIsNotFollowedOrReplaced() throws Exception {
        Path output = Files.createDirectories(directory.resolve("debug"));
        Path victim = temporary.newFile("victim.txt").toPath();
        Files.writeString(victim, "sentinel");
        Path occupied = output.resolve("shapedportals-v2.0.0-debugdump-2026-09-03-00-00-00.txt");
        Files.createSymbolicLink(occupied, victim.toAbsolutePath());

        BukkitDebugDump.create(plugin).request(player, false);

        assertTrue(Files.isSymbolicLink(occupied));
        assertEquals("sentinel", Files.readString(victim));
        Path report = output.resolve("shapedportals-v2.0.0-debugdump-2026-09-03-00-00-00-2.txt");
        assertEquals("Diagnostic report\n", Files.readString(report));
    }

    private ComponentText styledDebugText(TextKey key, MessageArgs arguments) {
        ComponentText prefix = ComponentText.markup("<bold><gradient:#6f35c5:#35135f>Local</gradient></bold>");
        ComponentText body;
        if (key.equals(BukkitDebugMessages.SAVED)) {
            body = ComponentText.literal("Saved ").append(prefix)
                    .append(ComponentText.literal(" debug dump to " + arguments.require("path").value() + "."));
        } else if (key.equals(BukkitDebugMessages.UPLOADED_AS)) {
            body = ComponentText.literal("Uploaded as VolmitSoftware - ").append(prefix)
                    .append(ComponentText.literal(" - v" + arguments.require("version").value() + "."));
        } else {
            body = ComponentText.literal(DirectorTextResolver.ENGLISH.resolve(key, arguments));
        }
        return prefix.append(ComponentText.literal(" › ")).append(body);
    }

    private String visibleMessages() {
        return messages.stream().map(message -> ComponentText.component(MiniMessage.miniMessage().deserialize(message)).plain())
                .collect(Collectors.joining("\n"));
    }

    private List<Component> renderedComponents() {
        List<Component> components = new ArrayList<>();
        for (String message : messages) {
            for (Component component : MiniMessage.miniMessage().deserialize(message)
                    .iterable(ComponentIteratorType.DEPTH_FIRST)) {
                components.add(component);
            }
        }
        return components;
    }

    private boolean hasColor(List<Component> components, TextColor color) {
        for (Component component : components) {
            if (color.equals(component.color())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRunCommand(List<Component> components, String command) {
        ClickEvent expected = ClickEvent.runCommand(command);
        for (Component component : components) {
            if (expected.equals(component.clickEvent())) {
                return true;
            }
        }
        return false;
    }

    private List<Path> savedReports() throws IOException {
        try (Stream<Path> files = Files.list(directory.resolve("debug"))) {
            return files.toList();
        }
    }

    public interface TestPlayer extends Player, CommandSender {
        @Override
        Player.Spigot spigot();

        @Override
        void resetTitle();
    }
}

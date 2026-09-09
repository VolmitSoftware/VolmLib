package art.arcane.volmlib.util.localization;

import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.SimpleServicesManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.MockedConstruction;
import org.mockito.invocation.Invocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

public class BukkitLanguageSwitcherTest {
    private final UUID playerId = UUID.randomUUID();
    private Server server;
    private SimpleServicesManager services;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<FoliaScheduler> scheduler;
    private MockedConstruction<BukkitVolmitCommand> commands;
    private MockedConstruction<BukkitLanguageEditor> editors;
    private TestPlayer player;
    private PluginLanguageService languages;
    private BukkitLanguageSwitcher switcher;

    @Before
    public void setup() {
        commands = mockConstruction(BukkitVolmitCommand.class);
        editors = mockConstruction(BukkitLanguageEditor.class);
        scheduler = mockStatic(FoliaScheduler.class);
        scheduler.when(() -> FoliaScheduler.runEntity(any(Plugin.class), any(), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(2, Runnable.class).run();
            return true;
        });
        scheduler.when(() -> FoliaScheduler.runGlobal(any(Plugin.class), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return true;
        });
        server = mock(Server.class);
        services = new SimpleServicesManager();
        when(server.getServicesManager()).thenReturn(services);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        player = mock(TestPlayer.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.hasPermission("volmit.language.self")).thenReturn(true);
        languages = languageService();
        switcher = register("Adapt", "adapt", languages);
    }

    @After
    public void cleanup() {
        bukkit.close();
        scheduler.close();
        commands.close();
        editors.close();
    }

    @Test
    public void pickerUsesThePluginDirectorThemeAndCommandForEveryControl() {
        switcher.command(player, new String[]{"self"});
        String rendered = String.join("\n", richMessages());

        assertTrue(rendered.contains("<gradient:#8b0000:#ff4d4d>"));
        assertTrue(rendered.contains("/adapt language self"));
        assertTrue(rendered.contains("<click:run_command:'/adapt language self fr_FR'>"));
        assertTrue(rendered.contains("/adapt language self page=2"));
        assertTrue(rendered.contains("</strikethrough>"));
        assertFalse(rendered.contains("/adapt language server"));
        assertFalse(rendered.contains("Use server default"));
        assertTrue(rendered.contains("<font:minecraft:uniform>"));
        assertTrue(rendered.contains("Command: /adapt language self fr_FR"));
    }

    @Test
    public void pickerAndEditorThemeCanRefreshWithoutReregistration() {
        DirectorMiniMenu.Theme updated = DirectorMiniMenu.Theme.reactBlue();

        switcher.updateTheme(updated);
        switcher.command(player, new String[]{"self"});

        String rendered = String.join("\n", richMessages());
        assertTrue(rendered.contains("<gradient:#003366:#00BFFF>"));
        assertFalse(rendered.contains("<gradient:#8b0000:#ff4d4d>"));
        verify(editors.constructed().get(0)).updateTheme(updated);
    }

    @Test
    public void rootLanguageMenuShowsCurrentScopesResetAndEditor() {
        when(player.hasPermission("adapt.admin")).thenReturn(true);
        when(languages.playerLocale(playerId)).thenReturn(Optional.of("fr_FR"));
        when(languages.effectiveLocale(playerId)).thenReturn("fr_FR");

        switcher.command(player, new String[0]);

        String rendered = String.join("\n", richMessages());
        assertTrue(rendered.contains("<click:run_command:'/adapt language self'>"));
        assertTrue(rendered.contains("Current: fr_FR"));
        assertTrue(rendered.contains("<click:run_command:'/adapt language self reset'>"));
        assertTrue(rendered.contains("Reset your language"));
        assertTrue(rendered.contains("<click:run_command:'/adapt language server'>"));
        assertTrue(rendered.contains("Current: en_US (Yours: fr_FR)"));
        assertTrue(rendered.contains("<click:run_command:'/adapt language server edit'>"));
        assertTrue(rendered.indexOf("Your language") < rendered.indexOf("Server default"));
        assertTrue(rendered.indexOf("Server default") < rendered.indexOf("Reset your language"));
        assertTrue(rendered.indexOf("Reset your language") < rendered.indexOf("Edit language messages"));
    }

    @Test
    public void pluginPickerKeepsScopeControlsOnTheFirstPage() {
        switcher.command(player, new String[]{"self", "page=2"});

        String rendered = String.join("\n", richMessages());
        assertFalse(rendered.contains("Your language"));
        assertFalse(rendered.contains("Use server default"));
        assertTrue(rendered.contains("/adapt language self page=1"));
    }

    @Test
    public void languageHomeAlwaysOffersThePersonalResetCommand() {

        switcher.command(player, new String[0]);

        assertTrue(String.join("\n", richMessages()).contains("Reset your language"));
    }

    @Test
    public void languageHomeOmitsPersonalNoteWhenItMatchesTheServerDefault() {
        when(player.hasPermission("adapt.admin")).thenReturn(true);

        switcher.command(player, new String[0]);

        String rendered = String.join("\n", richMessages());
        assertTrue(rendered.contains("Current: en_US"));
        assertFalse(rendered.contains("Yours:"));
    }

    @Test
    public void personalSelectionOnlyChangesTheCurrentPlugin() {
        PluginLanguageService other = languageService();
        register("Iris", "iris", other);

        switcher.command(player, new String[]{"self", "fr_FR"});

        verify(languages).selectPlayer(playerId, "fr_FR");
        verify(other, never()).selectPlayer(any(UUID.class), anyString());
    }

    @Test
    public void centralSelectionChangesEveryServerDefaultWithoutPersonalPreferences() {
        PluginLanguageService other = languageService();
        register("Iris", "iris", other);
        when(player.hasPermission("volmit.language.admin")).thenReturn(true);

        switcher.commandVolmit(player, new String[]{"plugins", "languages", "fr_FR"});

        verify(languages).selectDefault("fr_FR");
        verify(other).selectDefault("fr_FR");
        verify(languages, never()).selectPlayer(any(UUID.class), anyString());
        verify(other, never()).selectPlayer(any(UUID.class), anyString());
    }

    @Test
    public void centralSelectionChecksEveryPermissionBeforeChangingAnyProvider() {
        PluginLanguageService other = languageService();
        register("Iris", "iris", other);
        when(player.hasPermission("adapt.admin")).thenReturn(true);

        switcher.commandVolmit(player, new String[]{"plugins", "languages", "fr_FR"});

        verify(languages, never()).selectDefault(anyString());
        verify(other, never()).selectDefault(anyString());
        assertTrue(switcher.completeVolmit(player, new String[]{"plugins", "languages", ""}).isEmpty());
        assertTrue(switcher.complete(player, new String[]{""}).contains("server"));

        when(player.hasPermission("iris.admin")).thenReturn(true);
        switcher.commandVolmit(player, new String[]{"plugins", "languages", "fr_FR"});
        verify(languages).selectDefault("fr_FR");
        verify(other).selectDefault("fr_FR");
    }

    @Test
    public void pluginCompletionOnlyExposesSelfAndServerScopes() {
        assertEquals(List.of("self"), switcher.complete(player, new String[]{""}));
        when(player.hasPermission("adapt.admin")).thenReturn(true);
        assertEquals(List.of("self", "server"), switcher.complete(player, new String[]{""}));
        assertEquals(List.of("fr_FR"), switcher.complete(player, new String[]{"self", "fr"}));
        assertEquals(List.of("reset"), switcher.complete(player, new String[]{"self", "re"}));
        assertEquals(List.of("fr_FR"), switcher.complete(player, new String[]{"server", "fr"}));
        assertEquals(List.of(), switcher.complete(player, new String[]{"self", "fr_FR", ""}));
    }

    @Test
    public void centralPickerAndCompletionsUseTheGlobalServerCommand() {
        PluginLanguageService other = languageService();
        when(other.availableLocales()).thenReturn(List.of("en_US", "fr_FR"));
        register("Iris", "iris", other);
        when(player.hasPermission("volmit.language.admin")).thenReturn(true);

        switcher.commandVolmit(player, new String[]{"plugins", "languages"});

        String rendered = String.join("\n", richMessages());
        assertTrue(rendered.contains("<click:run_command:'/volmit plugins languages fr_FR'>"));
        assertFalse(rendered.contains("/adapt language"));
        assertFalse(rendered.contains("Your preference"));
        assertEquals(List.of("plugins"), switcher.completeVolmit(player, new String[]{""}));
        assertEquals(List.of("languages"), switcher.completeVolmit(player, new String[]{"plugins", ""}));
        assertEquals(List.of("en_US", "fr_FR"), switcher.completeVolmit(player, new String[]{"plugins", "languages", ""}));
        assertEquals(List.of(), switcher.completeVolmit(player, new String[]{"plugins", "languages", "fr_FR", ""}));
    }

    @Test
    public void sharedPluginMenuDiscoversDebugProvidersAndUsesDirectorControls() {
        List<String> requests = new ArrayList<>();
        registerDebug("Adapt", "1.2.3", "adapt.debug",
                (sender, upload) -> requests.add("Adapt:" + upload),
                (sender, upload) -> {
                    requests.add("Adapt:aggregate:" + upload);
                    return CompletableFuture.completedFuture(Map.of(
                            "status", "success", "name", "Adapt", "version", "1.2.3",
                            "path", "C:\\reports\\adapt.txt", "url", "https://mclo.gs/Cd34"));
                });
        registerDebug("BileTools", "4.5.6", "biletools.debugdump", (sender, upload) -> {
            requests.add("BileTools:" + upload);
        }, (sender, upload) -> {
            requests.add("BileTools:aggregate:" + upload);
            return CompletableFuture.completedFuture(Map.of(
                    "status", "success", "name", "BileTools", "version", "4.5.6",
                    "path", "C:\\reports\\biletools.txt", "url", "https://mclo.gs/Ab12"));
        });
        when(player.hasPermission("adapt.debug")).thenReturn(true);
        when(player.hasPermission("biletools.debugdump")).thenReturn(true);
        when(player.hasPermission("volmit.language.admin")).thenReturn(true);

        switcher.commandVolmit(player, new String[]{"plugins"});
        switcher.commandVolmit(player, new String[]{"plugins", "debug"});
        switcher.commandVolmit(player, new String[]{"plugins", "debug", "Adapt", "upload=false"});
        switcher.commandVolmit(player, new String[]{"plugins", "debug", "all", "upload=false"});

        String rendered = String.join("\n", richMessages());
        assertTrue(rendered.contains("/volmit plugins languages"));
        assertTrue(rendered.contains("/volmit plugins debug"));
        assertTrue(rendered.contains("<click:run_command:'/volmit plugins debug Adapt'>"));
        assertTrue(rendered.contains("Command: /volmit plugins debug Adapt"));
        assertTrue(rendered.contains("BileTools - Debug"));
        assertEquals(List.of("debug", "languages"),
                switcher.completeVolmit(player, new String[]{"plugins", ""}).stream().sorted().toList());
        assertEquals(List.of("all", "Adapt", "BileTools"),
                switcher.completeVolmit(player, new String[]{"plugins", "debug", ""}));
        assertEquals(List.of("upload=true", "upload=false"),
                switcher.completeVolmit(player, new String[]{"plugins", "debug", "Adapt", "upload="}));
        assertEquals(List.of("Adapt:false", "Adapt:aggregate:false", "BileTools:aggregate:false"), requests);
        assertTrue(rendered.contains(DirectorMiniMenu.escapeText("C:\\reports\\adapt.txt")));
        assertTrue(rendered.contains(DirectorMiniMenu.escapeText("C:\\reports\\biletools.txt")));
        assertTrue(rendered.indexOf(DirectorMiniMenu.escapeText("C:\\reports\\adapt.txt"))
                < rendered.indexOf("click:open_url:'https://mclo.gs/Cd34'"));
        assertTrue(rendered.indexOf(DirectorMiniMenu.escapeText("C:\\reports\\biletools.txt"))
                < rendered.indexOf("click:open_url:'https://mclo.gs/Cd34'"));
        assertTrue(rendered.indexOf("click:open_url:'https://mclo.gs/Cd34'")
                < rendered.indexOf("click:open_url:'https://mclo.gs/Ab12'"));
        assertTrue(rendered.contains("click:open_url:'https://mclo.gs/Ab12'"));
        assertTrue(rendered.contains("Copy all mclo.gs links"));
        assertTrue(rendered.contains("Copy all local paths"));
        assertTrue(rendered.contains("Adapt - https://mclo.gs/Cd34"));
        assertTrue(rendered.contains("BileTools - https://mclo.gs/Ab12"));
        assertTrue(rendered.indexOf("click:open_url:'https://mclo.gs/Ab12'")
                < rendered.indexOf("Copy all mclo.gs links"));
        assertTrue(rendered.indexOf("Copy all mclo.gs links")
                < rendered.indexOf("Copy all local paths"));
        assertEquals(4, Pattern.compile("click:copy_to_clipboard:").matcher(rendered).results().count());
        assertFalse(rendered.contains("Command: https://mclo.gs/Ab12"));
        assertFalse(rendered.contains("Started 2 debug reports."));
    }

    @Test
    public void allDebugReportsContinueWhenOneProviderFailsToStart() {
        List<String> requests = new ArrayList<>();
        registerDebug("Adapt", "1.2.3", "adapt.debug", (sender, upload) -> requests.add("legacy"),
                (sender, upload) -> {
                    throw new IllegalStateException("broken provider");
                });
        registerDebug("BileTools", "4.5.6", "biletools.debugdump",
                (sender, upload) -> requests.add("legacy"), (sender, upload) -> {
                    requests.add("BileTools:" + upload);
                    return CompletableFuture.completedFuture(Map.of(
                            "status", "success", "name", "BileTools", "version", "4.5.6",
                            "path", "C:\\reports\\biletools.txt"));
                });
        when(player.hasPermission("adapt.debug")).thenReturn(true);
        when(player.hasPermission("biletools.debugdump")).thenReturn(true);

        switcher.commandVolmit(player, new String[]{"plugins", "debug", "all", "upload=false"});

        String rendered = String.join("\n", richMessages());
        assertEquals(List.of("BileTools:false"), requests);
        assertEquals(1, richMessages().stream()
                .filter(message -> message.contains("/volmit plugins debug all"))
                .count());
        assertTrue(rendered.contains("Adapt v1.2.3 - Unable to start the debug report"));
        assertTrue(rendered.contains(DirectorMiniMenu.escapeText("C:\\reports\\biletools.txt")));
        assertFalse(rendered.contains("Copy all mclo.gs links"));
        assertTrue(rendered.contains("Copy all local paths"));
    }

    @Test
    public void sharedMenusExposeTheirImmediateParentRoutes() {
        registerDebug("Adapt", "1.2.3", "adapt.debug", (sender, upload) -> {
        });
        when(player.hasPermission("adapt.debug")).thenReturn(true);
        when(player.hasPermission("volmit.language.admin")).thenReturn(true);

        switcher.commandVolmit(player, new String[0]);
        String root = String.join("\n", richMessages());
        assertTrue(root.contains("<click:run_command:'/volmit plugins'>"));
        assertFalse(root.contains("〈 Back"));

        clearInvocations(player);
        switcher.commandVolmit(player, new String[]{"plugins"});
        assertTrue(String.join("\n", richMessages()).contains("<click:run_command:/volmit>"));

        clearInvocations(player);
        switcher.commandVolmit(player, new String[]{"plugins", "languages"});
        assertTrue(String.join("\n", richMessages()).contains("<click:run_command:/volmit plugins>"));

        clearInvocations(player);
        switcher.commandVolmit(player, new String[]{"plugins", "debug"});
        assertTrue(String.join("\n", richMessages()).contains("<click:run_command:/volmit plugins>"));
    }

    @Test
    public void debugOnlyProvidersRemainAvailableWithoutLanguageProviders() {
        services = new SimpleServicesManager();
        when(server.getServicesManager()).thenReturn(services);
        List<Boolean> requests = new ArrayList<>();
        registerDebug("DebugOnly", "1.0.0", "debugonly.debug", (sender, upload) -> requests.add(upload),
                (sender, upload) -> {
                    requests.add(upload);
                    return CompletableFuture.completedFuture(Map.of(
                            "status", "success", "name", "DebugOnly", "version", "1.0.0",
                            "path", "C:\\reports\\debugonly.txt"));
                });
        when(player.hasPermission("debugonly.debug")).thenReturn(true);

        switcher.commandVolmit(player, new String[]{"plugins"});
        switcher.commandVolmit(player, new String[]{"plugins", "debug", "all", "upload=false"});

        String rendered = String.join("\n", richMessages());
        assertTrue(rendered.contains("/volmit plugins debug"));
        assertEquals(List.of(false), requests);
    }

    @Test
    public void debugCompletionDoesNotOfferAllWithoutAPermittedProvider() {
        registerDebug("Adapt", "1.2.3", "adapt.debug", (sender, upload) -> {
        });

        assertTrue(switcher.completeVolmit(player, new String[]{"plugins", "debug", ""}).isEmpty());
    }

    @Test
    public void sharedMenuTextFallsBackWhenAConsumerCatalogHasNoSharedKeys() {
        DirectorTextResolver incomplete = (key, arguments) -> {
            if (key.id().startsWith("language.menu.")) {
                throw new IllegalArgumentException("Unknown message key: " + key.id());
            }
            return DirectorTextResolver.ENGLISH.resolve(key, arguments);
        };
        BukkitLanguageSwitcher fallback = register("Fallback", "fallback", languages, incomplete);

        fallback.command(player, new String[0]);

        assertTrue(String.join("\n", richMessages()).contains("Your language"));
    }

    @Test
    public void serverSelectionOnlyChangesTheCurrentPlugin() {
        PluginLanguageService other = languageService();
        register("Iris", "iris", other);
        when(player.hasPermission("volmit.language.admin")).thenReturn(true);

        switcher.command(player, new String[]{"server", "fr_FR"});

        verify(languages).selectDefault("fr_FR");
        verify(other, never()).selectDefault(anyString());
    }

    @Test
    public void unsupportedScopesAndExtraArgumentsDoNotSelectLanguages() {
        when(player.hasPermission("volmit.language.admin")).thenReturn(true);
        switcher.command(player, new String[]{"all", "server", "fr_FR"});
        switcher.command(player, new String[]{"fr_FR"});
        switcher.command(player, new String[]{"self", "fr_FR", "extra"});
        switcher.commandVolmit(player, new String[]{"plugins", "languages", "fr_FR", "extra"});

        verify(languages, never()).selectDefault(anyString());
        verify(languages, never()).selectPlayer(any(UUID.class), anyString());
    }

    @Test
    public void englishFallbackReportsActualLanguageInsteadOfRequestedLocale() {
        when(player.hasPermission("volmit.language.admin")).thenReturn(true);
        when(languages.selectDefault("de_DE")).thenReturn(CompletableFuture.completedFuture(null));
        when(languages.selectPlayer(playerId, "de_DE")).thenReturn(CompletableFuture.completedFuture(null));

        switcher.command(player, new String[]{"self", "de_DE"});
        switcher.commandVolmit(player, new String[]{"plugins", "languages", "de_DE"});

        String rendered = String.join("\n", richMessages());
        assertTrue(rendered.contains("de_DE is unavailable; using English (en_US)."));
        assertFalse(rendered.contains("is now de_DE"));
    }

    @Test
    public void asynchronousSelectionFeedbackRestoresThePlayerLanguageAudience() {
        PluginLanguageService localizedService = languageService();
        CompletableFuture<Void> pending = new CompletableFuture<>();
        when(localizedService.selectPlayer(playerId, "fr_FR")).thenReturn(pending);
        when(localizedService.effectiveLocale(playerId)).thenReturn("fr_FR");
        DirectorTextResolver resolver = (key, arguments) -> {
            if (!key.id().equals(BukkitLanguageMessages.PERSONAL_SELECTED.id())) {
                return DirectorTextResolver.ENGLISH.resolve(key, arguments);
            }
            String prefix = playerId.equals(LanguageAudience.current()) ? "Audience restored" : "Wrong audience";
            return prefix + " for " + arguments.require("plugin").value()
                    + ": " + arguments.require("locale").value() + ".";
        };
        BukkitLanguageSwitcher localized = register("Localized", "localized", localizedService, resolver);

        localized.command(player, new String[]{"self", "fr_FR"});
        clearInvocations(player);
        pending.complete(null);

        String rendered = String.join("\n", richMessages());
        assertTrue(rendered.contains("Audience restored for Localized: fr_FR."));
        assertFalse(rendered.contains("Wrong audience"));
    }

    @Test
    public void closingRemovesTheProviderAndOffersCommandOwnershipToRemainingPlugins() {
        register("Iris", "iris", languageService());
        switcher.close();

        assertEquals(1, server.getServicesManager().getRegistrations(Map.class).size());
        verify(commands.constructed().get(0)).release();
        verify(commands.constructed().get(1), times(2)).claim();
    }

    @Test
    public void pluginPermissionGatesPickerSelectionResetAndCompletions() {
        when(player.hasPermission("adapt.language.self")).thenReturn(false);

        switcher.open(player);
        switcher.command(player, new String[]{"self", "fr_FR"});
        switcher.command(player, new String[]{"self", "reset"});

        verify(languages, never()).selectPlayer(any(UUID.class), anyString());
        verify(languages, never()).clearPlayer(any(UUID.class));
        assertTrue(switcher.complete(player, new String[]{""}).isEmpty());
        assertTrue(switcher.complete(player, new String[]{"self", ""}).isEmpty());
        assertFalse(String.join("\n", richMessages()).contains("<click:run_command:'/adapt language self"));
    }

    @Test
    public void sharedPermissionStillGatesPersonalSelection() {
        when(player.hasPermission("volmit.language.self")).thenReturn(false);

        switcher.command(player, new String[]{"self", "fr_FR"});

        verify(languages, never()).selectPlayer(any(UUID.class), anyString());
        assertTrue(switcher.complete(player, new String[]{"self", ""}).isEmpty());
    }

    @Test
    public void serverSelectionDoesNotRequirePersonalLanguagePermission() {
        when(player.hasPermission("adapt.language.self")).thenReturn(false);
        when(player.hasPermission("adapt.admin")).thenReturn(true);
        when(languages.selectDefault("fr_FR")).thenReturn(new CompletableFuture<>());

        switcher.command(player, new String[]{"server", "fr_FR"});

        verify(languages).selectDefault("fr_FR");
        assertTrue(switcher.complete(player, new String[]{""}).contains("server"));
        assertFalse(switcher.complete(player, new String[]{""}).contains("self"));
    }

    @Test
    public void serverEditorRequiresAdministrationAndDoesNotSelectALanguage() {
        switcher.command(player, new String[]{"server", "edit", "fr_FR"});
        verify(editors.constructed().get(0), never()).open(any(Player.class), anyString());

        when(player.hasPermission("adapt.admin")).thenReturn(true);
        switcher.command(player, new String[]{"server", "edit", "fr_FR"});

        verify(editors.constructed().get(0)).open(player, "fr_FR");
        verify(languages, never()).selectDefault(anyString());
        verify(languages, never()).selectPlayer(any(UUID.class), anyString());
        assertEquals(List.of("self", "server"), switcher.complete(player, new String[]{""}));
        assertTrue(switcher.complete(player, new String[]{"server", ""}).contains("edit"));
        assertEquals(List.of("fr_FR"), switcher.complete(player, new String[]{"server", "edit", "fr"}));
    }

    @Test
    public void serverPickerLeavesMessageEditingInTheConfigEditor() {
        when(player.hasPermission("adapt.admin")).thenReturn(true);

        switcher.command(player, new String[]{"server"});

        String rendered = String.join("\n", richMessages());
        assertFalse(rendered.contains("/adapt language server edit"));
        assertTrue(rendered.contains("<click:run_command:'/adapt language server fr_FR'>"));
    }

    private BukkitLanguageSwitcher register(String name, String command, PluginLanguageService service) {
        return register(name, command, service, DirectorTextResolver.ENGLISH);
    }

    private BukkitLanguageSwitcher register(String name, String command, PluginLanguageService service,
                                              DirectorTextResolver resolver) {
        when(player.hasPermission(name.toLowerCase(Locale.ROOT) + ".language.self")).thenReturn(true);
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn(name);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getLogger()).thenReturn(Logger.getLogger(name));
        return BukkitLanguageSwitcher.register(plugin, service, new BukkitLanguageSwitcher.Options(
                command, command + ".admin", DirectorMiniMenu.Theme.adaptRed(), resolver,
                new PluginLanguageEditor.Options(locale -> mock(LocalizationSnapshot.class), edit -> mock(LocalizationSnapshot.class))));
    }

    private void registerDebug(String name, String version, String permission,
                               BiConsumer<CommandSender, Boolean> request) {
        registerDebug(name, version, permission, request, null);
    }

    private void registerDebug(String name, String version, String permission,
                               BiConsumer<CommandSender, Boolean> request,
                               BiFunction<CommandSender, Boolean, CompletableFuture<Map<String, String>>> aggregate) {
        Plugin owner = mock(Plugin.class);
        when(owner.isEnabled()).thenReturn(true);
        Map<String, Object> provider = new HashMap<>();
        provider.put("volmit.debug.protocol", "1");
        provider.put("name", name);
        provider.put("version", version);
        provider.put("permission", permission);
        provider.put("request", request);
        if (aggregate != null) {
            provider.put("request.aggregate", aggregate);
        }
        services.register(Map.class, provider, owner, ServicePriority.Normal);
    }

    private PluginLanguageService languageService() {
        PluginLanguageService service = mock(PluginLanguageService.class);
        when(service.availableLocales()).thenReturn(List.of(
                "de_DE", "en_US", "es_ES", "fi_FI", "fr_FR", "he_IL", "it_IT", "ja_JP", "ko_KR", "pt_BR"));
        when(service.defaultLocale()).thenReturn("en_US");
        when(service.effectiveLocale(playerId)).thenReturn("en_US");
        when(service.selectPlayer(any(UUID.class), anyString())).thenReturn(new CompletableFuture<>());
        when(service.selectDefault(anyString())).thenReturn(new CompletableFuture<>());
        return service;
    }

    private List<String> richMessages() {
        List<String> messages = new ArrayList<>();
        for (Invocation invocation : mockingDetails(player).getInvocations()) {
            if (invocation.getMethod().getName().equals("sendRichMessage")) {
                messages.add((String) invocation.getArgument(0));
            }
        }
        return messages;
    }

    public interface TestPlayer extends Player, CommandSender {
        @Override
        Player.Spigot spigot();

        void sendRichMessage(String message);
    }
}

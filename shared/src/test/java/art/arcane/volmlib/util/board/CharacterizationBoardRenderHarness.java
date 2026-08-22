package art.arcane.volmlib.util.board;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Test-only harness that drives the real {@code Board} class inside an isolated classloader.
 *
 * <p>The shared test source set ships a stub {@code org.bukkit.entity.Player} (used by the hud
 * tests) that shadows the paper-api interface on the normal test classpath and lacks the methods
 * Board calls ({@code isOnline}, {@code getScoreboard}, {@code setScoreboard}). To exercise the
 * board render pipeline without touching any existing file, this harness rebuilds the test
 * classpath minus the test-classes directory in a child classloader, where the real paper-api
 * Player is available for JDK-proxy fakes. Each harness instance gets fresh Board statics, so
 * runtime detection (Canvas name probe, forced Folia threading) can vary per scenario with zero
 * cross-test pollution.
 *
 * <p>The fake scoreboard models {@code Scoreboard.getEntries()} as the set of score holders
 * (entries currently carrying a score); team-only membership does not keep an entry alive.
 * That is the semantic Board's count-compare wipe logic depends on.
 */
public final class CharacterizationBoardRenderHarness implements AutoCloseable {
    private static final String PKG = "art.arcane.volmlib.util.board.";

    private final URLClassLoader loader;
    private final String serverName;
    private final boolean hasScoreboardManager;
    private final List<ScoreboardModel> createdScoreboards = new ArrayList<>();
    private final AtomicInteger newScoreboardCalls = new AtomicInteger();
    private Object scoreboardManagerProxy;
    private final Class<?> boardClass;

    public static CharacterizationBoardRenderHarness normal() {
        return new CharacterizationBoardRenderHarness("CharacterizationServer", true, false);
    }

    public static CharacterizationBoardRenderHarness withoutScoreboardManager() {
        return new CharacterizationBoardRenderHarness("CharacterizationServer", false, false);
    }

    public static CharacterizationBoardRenderHarness folia() {
        return new CharacterizationBoardRenderHarness("CharacterizationServer", true, true);
    }

    public static CharacterizationBoardRenderHarness canvas() {
        return new CharacterizationBoardRenderHarness("CanvasMC", true, false);
    }

    private CharacterizationBoardRenderHarness(String serverName, boolean hasScoreboardManager, boolean forceFolia) {
        this.serverName = serverName;
        this.hasScoreboardManager = hasScoreboardManager;
        this.loader = buildLoader();
        try {
            Class<?> playerClass = cls("org.bukkit.entity.Player");
            playerClass.getMethod("isOnline");
        } catch (NoSuchMethodException stubLeak) {
            throw new IllegalStateException("Isolated classpath still resolves the stub Player; "
                    + "check the test-classes exclusion in buildLoader()", stubLeak);
        }

        Object serverProxy = proxyIso("org.bukkit.Server", this::serverInvocation);
        try {
            Field serverField = cls("org.bukkit.Bukkit").getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(null, serverProxy);
            if (forceFolia) {
                Class<?> foliaScheduler = cls("art.arcane.volmlib.util.scheduling.FoliaScheduler");
                foliaScheduler.getMethod("forceFoliaThreading", cls("org.bukkit.Server")).invoke(null, serverProxy);
            }
            this.boardClass = cls(PKG + "Board");
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Failed to initialize the isolated Bukkit runtime", failure);
        }
    }

    // ------------------------------------------------------------------ classpath

    private static URLClassLoader buildLoader() {
        LinkedHashSet<URL> urls = new LinkedHashSet<>();
        String classPath = System.getProperty("java.class.path", "");
        for (String entry : classPath.split(File.pathSeparator)) {
            if (entry.isBlank()) {
                continue;
            }
            // java.class.path can arrive with doubled separators on Windows, which turns the
            // exclusions below into a silent no-op and leaks the stub Player into the isolated
            // loader. Collapse the repeats before matching; each URL is still built from the
            // untouched entry, so a UNC prefix is unaffected.
            String normalized = entry.replace('\\', '/').replaceAll("/{2,}", "/");
            if (normalized.contains("build/classes/java/test") || normalized.contains("build/resources/test")) {
                continue;
            }
            try {
                urls.add(new File(entry).toURI().toURL());
            } catch (Exception ignored) {
            }
        }
        addLocation(urls, Board.class);
        addLocationByName(urls, "org.bukkit.Server");
        addLocationByName(urls, "net.kyori.adventure.text.Component");
        addLocationByName(urls, "net.kyori.adventure.key.Key");
        addLocationByName(urls, "net.kyori.examination.Examinable");
        addLocationByName(urls, "net.md_5.bungee.api.chat.BaseComponent");
        return new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
    }

    private static void addLocationByName(LinkedHashSet<URL> urls, String className) {
        try {
            addLocation(urls, Class.forName(className));
        } catch (Throwable ignored) {
        }
    }

    private static void addLocation(LinkedHashSet<URL> urls, Class<?> type) {
        try {
            URL location = type.getProtectionDomain().getCodeSource().getLocation();
            if (location != null) {
                urls.add(location);
            }
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------ server + manager fakes

    private Object serverInvocation(Object proxy, Method method, Object[] arguments) {
        return switch (method.getName()) {
            case "getLogger" -> Logger.getLogger("CharacterizationBoard");
            case "getName" -> serverName;
            case "getVersion", "getBukkitVersion" -> "1.20.1-characterization";
            case "getScoreboardManager" -> hasScoreboardManager ? scoreboardManager() : null;
            case "isPrimaryThread" -> true;
            case "getOnlinePlayers" -> List.of();
            case "equals" -> proxy == arguments[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "CharacterizationServer";
            default -> defaultValue(method.getReturnType());
        };
    }

    private Object scoreboardManager() {
        if (scoreboardManagerProxy == null) {
            scoreboardManagerProxy = proxyIso("org.bukkit.scoreboard.ScoreboardManager", (proxy, method, arguments) -> switch (method.getName()) {
                case "getNewScoreboard" -> {
                    newScoreboardCalls.incrementAndGet();
                    ScoreboardModel model = newScoreboardModel();
                    createdScoreboards.add(model);
                    yield model.proxy;
                }
                case "getMainScoreboard" -> newScoreboardModel().proxy;
                case "equals" -> proxy == arguments[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> defaultValue(method.getReturnType());
            });
        }
        return scoreboardManagerProxy;
    }

    public int newScoreboardCalls() {
        return newScoreboardCalls.get();
    }

    public List<ScoreboardModel> createdScoreboards() {
        return createdScoreboards;
    }

    public ScoreboardModel ownedScoreboard() {
        if (createdScoreboards.isEmpty()) {
            throw new IllegalStateException("No scoreboard was leased from the manager");
        }
        return createdScoreboards.get(0);
    }

    // ------------------------------------------------------------------ scoreboard model

    public static final class TeamState {
        public String prefix;
        public String suffix;
        public final List<String> entries = new ArrayList<>();
        public Boolean allowFriendlyFire;
        public Boolean canSeeFriendlyInvisibles;
        public boolean throwUnsupportedOnPrefix;
        /** Write counts, so a diff-gated render can be shown to have applied nothing. */
        public int prefixWrites;
        public int suffixWrites;
    }

    public final class ScoreboardModel {
        public final List<String> registeredObjectives = new ArrayList<>();
        public final List<String> unregisteredObjectives = new ArrayList<>();
        public String objectiveInitialDisplayName;
        public String objectiveDisplayName;
        public String displaySlot;
        public final LinkedHashMap<String, TeamState> teams = new LinkedHashMap<>();
        public final LinkedHashMap<String, Integer> scores = new LinkedHashMap<>();
        public int displayNameWrites;
        public int scoreWrites;
        public int resetScoreCalls;
        public final Object proxy;

        private ScoreboardModel() {
            this.proxy = proxyIso("org.bukkit.scoreboard.Scoreboard", this::invoke);
        }

        private Object invoke(Object proxy, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "registerNewObjective" -> {
                    String name = (String) arguments[0];
                    registeredObjectives.add(name);
                    Object display = arguments.length > 2 ? arguments[2] : name;
                    if (objectiveInitialDisplayName == null && display instanceof String value) {
                        objectiveInitialDisplayName = value;
                    }
                    yield objectiveProxy(name);
                }
                case "getObjective" -> {
                    String name = String.valueOf(arguments[0]);
                    yield registeredObjectives.contains(name) && !unregisteredObjectives.contains(name)
                            ? objectiveProxy(name)
                            : null;
                }
                case "registerNewTeam" -> {
                    String name = (String) arguments[0];
                    teams.put(name, new TeamState());
                    yield teamProxy(name);
                }
                case "getTeam" -> teams.containsKey(String.valueOf(arguments[0]))
                        ? teamProxy((String) arguments[0])
                        : null;
                case "getEntries" -> new LinkedHashSet<>(scores.keySet());
                case "resetScores" -> {
                    resetScoreCalls++;
                    scores.remove(String.valueOf(arguments[0]));
                    yield null;
                }
                case "equals" -> proxy == arguments[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "FakeScoreboard";
                default -> defaultValue(method.getReturnType());
            };
        }

        private Object objectiveProxy(String name) {
            return proxyIso("org.bukkit.scoreboard.Objective", (proxy, method, arguments) -> switch (method.getName()) {
                case "setDisplaySlot" -> {
                    displaySlot = arguments[0] instanceof Enum<?> slot ? slot.name() : String.valueOf(arguments[0]);
                    yield null;
                }
                case "setDisplayName" -> {
                    displayNameWrites++;
                    objectiveDisplayName = (String) arguments[0];
                    yield null;
                }
                case "getScore" -> scoreProxy(String.valueOf(arguments[0]));
                case "unregister" -> {
                    unregisteredObjectives.add(name);
                    yield null;
                }
                case "getName" -> name;
                case "equals" -> proxy == arguments[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> defaultValue(method.getReturnType());
            });
        }

        private Object scoreProxy(String entry) {
            return proxyIso("org.bukkit.scoreboard.Score", (proxy, method, arguments) -> switch (method.getName()) {
                case "setScore" -> {
                    scoreWrites++;
                    scores.put(entry, (Integer) arguments[0]);
                    yield null;
                }
                case "getScore" -> scores.getOrDefault(entry, 0);
                case "getEntry" -> entry;
                case "equals" -> proxy == arguments[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> defaultValue(method.getReturnType());
            });
        }

        private Object teamProxy(String name) {
            TeamState state = teams.get(name);
            return proxyIso("org.bukkit.scoreboard.Team", (proxy, method, arguments) -> switch (method.getName()) {
                case "getName" -> name;
                case "addEntry" -> {
                    state.entries.add((String) arguments[0]);
                    yield null;
                }
                case "setPrefix" -> {
                    if (state.throwUnsupportedOnPrefix) {
                        throw new UnsupportedOperationException("characterization: prefix rejected");
                    }
                    state.prefixWrites++;
                    state.prefix = (String) arguments[0];
                    yield null;
                }
                case "setSuffix" -> {
                    state.suffixWrites++;
                    state.suffix = (String) arguments[0];
                    yield null;
                }
                case "setAllowFriendlyFire" -> {
                    state.allowFriendlyFire = (Boolean) arguments[0];
                    yield null;
                }
                case "setCanSeeFriendlyInvisibles" -> {
                    state.canSeeFriendlyInvisibles = (Boolean) arguments[0];
                    yield null;
                }
                case "equals" -> proxy == arguments[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> defaultValue(method.getReturnType());
            });
        }

        /** Immutable deep view of everything a client would observe, for state-equality pins. */
        public Map<String, Object> stateView() {
            Map<String, Object> teamsView = new LinkedHashMap<>();
            teams.forEach((name, team) -> teamsView.put(name, List.of(
                    String.valueOf(team.prefix),
                    String.valueOf(team.suffix),
                    List.copyOf(team.entries),
                    String.valueOf(team.allowFriendlyFire),
                    String.valueOf(team.canSeeFriendlyInvisibles))));
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("registered", List.copyOf(registeredObjectives));
            view.put("unregistered", List.copyOf(unregisteredObjectives));
            view.put("initialDisplayName", String.valueOf(objectiveInitialDisplayName));
            view.put("displayName", String.valueOf(objectiveDisplayName));
            view.put("displaySlot", String.valueOf(displaySlot));
            view.put("teams", teamsView);
            view.put("scores", new LinkedHashMap<>(scores));
            return view;
        }

        /** Every client-visible application performed on this scoreboard since the last reset. */
        public int writes() {
            int total = displayNameWrites + scoreWrites + resetScoreCalls;
            for (TeamState team : teams.values()) {
                total += team.prefixWrites + team.suffixWrites;
            }
            return total;
        }

        public void resetWriteCounters() {
            displayNameWrites = 0;
            scoreWrites = 0;
            resetScoreCalls = 0;
            for (TeamState team : teams.values()) {
                team.prefixWrites = 0;
                team.suffixWrites = 0;
            }
        }
    }

    public ScoreboardModel newScoreboardModel() {
        return new ScoreboardModel();
    }

    // ------------------------------------------------------------------ player fake

    public final class PlayerHandle {
        public final UUID id = UUID.randomUUID();
        public boolean online = true;
        public final Object previousScoreboard;
        public Object currentScoreboard;
        public final List<Object> assignments = new ArrayList<>();
        public final Object proxy;

        private PlayerHandle() {
            this.previousScoreboard = newScoreboardModel().proxy;
            this.currentScoreboard = previousScoreboard;
            this.proxy = proxyIso("org.bukkit.entity.Player", (proxy, method, arguments) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "isOnline" -> online;
                case "getScoreboard" -> currentScoreboard;
                case "setScoreboard" -> {
                    assignments.add(arguments[0]);
                    currentScoreboard = arguments[0];
                    yield null;
                }
                case "getName" -> "CharacterizationPlayer";
                case "equals" -> proxy == arguments[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "CharacterizationPlayer";
                default -> defaultValue(method.getReturnType());
            });
        }
    }

    public PlayerHandle newPlayer() {
        return new PlayerHandle();
    }

    // ------------------------------------------------------------------ provider + settings

    public final class ProviderHandle {
        public final AtomicInteger titleCalls = new AtomicInteger();
        public final AtomicInteger linesCalls = new AtomicInteger();
        public final AtomicInteger hideScoreNumbersCalls = new AtomicInteger();
        public volatile Supplier<String> title;
        public volatile Supplier<List<String>> lines;
        public volatile boolean hideScoreNumbers;
        public final Object proxy;

        private ProviderHandle(Supplier<String> title, Supplier<List<String>> lines) {
            this(title, lines, true);
        }

        private ProviderHandle(Supplier<String> title, Supplier<List<String>> lines, boolean hideScoreNumbers) {
            this.title = title;
            this.lines = lines;
            this.hideScoreNumbers = hideScoreNumbers;
            this.proxy = proxyIso(PKG + "BoardProvider", (proxy, method, arguments) -> switch (method.getName()) {
                case "getTitle" -> {
                    titleCalls.incrementAndGet();
                    yield this.title.get();
                }
                case "getLines" -> {
                    linesCalls.incrementAndGet();
                    yield new ArrayList<>(this.lines.get());
                }
                case "hideScoreNumbers" -> {
                    hideScoreNumbersCalls.incrementAndGet();
                    yield this.hideScoreNumbers;
                }
                case "equals" -> proxy == arguments[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> defaultValue(method.getReturnType());
            });
        }
    }

    public ProviderHandle provider(String title, List<String> lines) {
        return new ProviderHandle(() -> title, () -> lines);
    }

    public ProviderHandle provider(Supplier<String> title, Supplier<List<String>> lines) {
        return new ProviderHandle(title, lines);
    }

    public ProviderHandle provider(String title, List<String> lines, boolean hideScoreNumbers) {
        return new ProviderHandle(() -> title, () -> lines, hideScoreNumbers);
    }

    public Object settings(ProviderHandle provider, String direction) {
        try {
            Class<?> settingsClass = cls(PKG + "BoardSettings");
            Class<?> providerClass = cls(PKG + "BoardProvider");
            Class<?> directionClass = cls(PKG + "ScoreDirection");
            Object directionValue = null;
            for (Object constant : directionClass.getEnumConstants()) {
                if (String.valueOf(constant).equals(direction)) {
                    directionValue = constant;
                }
            }
            return settingsClass.getConstructor(providerClass, directionClass)
                    .newInstance(provider.proxy, directionValue);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Failed to build isolated BoardSettings", failure);
        }
    }

    // ------------------------------------------------------------------ board driving

    public Object newBoard(PlayerHandle player, Object settings) {
        try {
            return boardClass
                    .getConstructor(cls("org.bukkit.entity.Player"), cls(PKG + "BoardSettings"))
                    .newInstance(player.proxy, settings);
        } catch (InvocationTargetException failure) {
            throw rethrow(failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Failed to construct isolated Board", failure);
        }
    }

    public void update(Object board) {
        call(board, "update");
    }

    public void remove(Object board) {
        call(board, "remove");
    }

    public void setBoardSettings(Object board, Object settings) {
        try {
            boardClass.getMethod("setBoardSettings", cls(PKG + "BoardSettings")).invoke(board, settings);
        } catch (InvocationTargetException failure) {
            throw rethrow(failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Failed to invoke setBoardSettings", failure);
        }
    }

    public Object boardScoreboard(Object board) {
        try {
            return boardClass.getMethod("getScoreboard").invoke(board);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Failed to invoke getScoreboard", failure);
        }
    }

    private void call(Object board, String method) {
        try {
            boardClass.getMethod(method).invoke(board);
        } catch (InvocationTargetException failure) {
            throw rethrow(failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Failed to invoke Board." + method, failure);
        }
    }

    // ------------------------------------------------------------------ plumbing

    private Class<?> cls(String name) {
        try {
            return Class.forName(name, true, loader);
        } catch (ClassNotFoundException missing) {
            throw new IllegalStateException("Isolated classpath is missing " + name, missing);
        }
    }

    private Object proxyIso(String interfaceName, InvocationHandler handler) {
        Class<?> type = cls(interfaceName);
        return Proxy.newProxyInstance(loader, new Class<?>[]{type}, handler);
    }

    private static RuntimeException rethrow(Throwable cause) {
        if (cause instanceof RuntimeException runtime) {
            return runtime;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(cause);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        return 0;
    }

    @Override
    public void close() {
        try {
            loader.close();
        } catch (Exception ignored) {
        }
    }
}

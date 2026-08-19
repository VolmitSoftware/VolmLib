package art.arcane.volmlib.util.board;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.IntStream;

public class Board {
    private static final int MAX_LINES = 15;
    private static final int MAX_TITLE_LENGTH = 32;
    private static final int UNSET_SCORE = Integer.MIN_VALUE;
    private static final String[] CACHED_ENTRIES = new String[ChatColor.values().length];
    private static final boolean CANVAS_RUNTIME = detectCanvasRuntime();
    private static final ScoreNumberFormatBridge NUMBER_FORMATS = new ScoreNumberFormatBridge();

    static {
        IntStream.range(0, MAX_LINES).forEach(i -> CACHED_ENTRIES[i] = ChatColor.values()[i].toString() + ChatColor.RESET);
    }

    private final Player player;
    private final Objective objective;
    private final PacketSidebar packetSidebar;
    private final Scoreboard previousScoreboard;
    private final Scoreboard ownedScoreboard;
    private final String[] appliedLines = new String[MAX_LINES];
    private final int[] appliedScores = new int[MAX_LINES];
    private String appliedTitle;
    private Boolean appliedHideScores;
    private int appliedScoredRows;
    private boolean normalObjectiveDisplayed;
    private BoardSettings boardSettings;
    private boolean useNormalBackend;
    private boolean ready;
    private boolean removed;

    public Board(@NonNull Player player, BoardSettings boardSettings) {
        this.player = player;
        this.boardSettings = boardSettings;
        this.packetSidebar = new PacketSidebar(player);
        Arrays.fill(this.appliedScores, UNSET_SCORE);

        Scoreboard previous = currentScoreboard();
        BoardLease lease = shouldAttemptNormalBackend()
                ? acquireNormalBackend(previous)
                : BoardLease.empty();

        this.previousScoreboard = previous;
        this.ownedScoreboard = lease.scoreboard();
        this.objective = lease.objective();
        this.useNormalBackend = lease.active();
        this.ready = lease.active() || packetSidebar.isSupported();
        this.removed = false;
        this.normalObjectiveDisplayed = false;
    }

    public Scoreboard getScoreboard() {
        return ownedScoreboard;
    }

    public void setBoardSettings(BoardSettings boardSettings) {
        this.boardSettings = boardSettings;
    }

    public void remove() {
        if (removed) {
            return;
        }
        removed = true;
        ready = false;
        removeNormalObjective();
        packetSidebar.reset();
        forgetAppliedState();
        restorePreviousScoreboard();
    }

    public void update() {
        if (!ready) {
            return;
        }

        if (!player.isOnline()) {
            remove();
            return;
        }

        if (boardSettings == null) {
            return;
        }

        // One array, filled in provider order and truncated to MAX_LINES before any reversal, so
        // the UP direction still drops the sixteenth line rather than the first.
        List<String> provided = boardSettings.getBoardProvider().getLines(player);
        String[] entries = new String[Math.min(provided.size(), MAX_LINES)];
        int index = 0;
        for (String line : provided) {
            if (index == entries.length) {
                break;
            }
            entries[index++] = normalizeLine(line);
        }

        if (boardSettings.getScoreDirection() == ScoreDirection.UP) {
            reverse(entries);
        }

        String title = normalizeTitle(boardSettings.getBoardProvider().getTitle(player));
        boolean hideScores = boardSettings.getBoardProvider().hideScoreNumbers(player);

        if (useNormalBackend) {
            if (!updateNormal(title, entries, hideScores)) {
                useNormalBackend = false;
                removeNormalObjective();
                boolean switchedToPacket = packetSidebar.isSupported()
                        && packetSidebar.render(title, entries, boardSettings.getScoreDirection(), hideScores);
                if (!switchedToPacket && !packetSidebar.isSupported()) {
                    ready = false;
                }
            }
            return;
        }

        if (!packetSidebar.render(title, entries, boardSettings.getScoreDirection(), hideScores)
                && !packetSidebar.isSupported()) {
            ready = false;
        }
    }

    private boolean updateNormal(String title, String[] entries, boolean hideScores) {
        if (objective == null) {
            return false;
        }

        Scoreboard scoreboard = getScoreboard();
        if (scoreboard == null) {
            return false;
        }

        boolean scoreUp = boardSettings.getScoreDirection() == ScoreDirection.UP;
        boolean effectiveHideScores = effectiveHideScoreNumbers(hideScores, NUMBER_FORMATS.isSupported());

        try {
            if (!Objects.equals(appliedHideScores, effectiveHideScores)) {
                NUMBER_FORMATS.apply(objective, effectiveHideScores);
                appliedHideScores = effectiveHideScores;
            }
            if (!title.equals(appliedTitle)) {
                objective.setDisplayName(title);
                appliedTitle = title;
            }

            // appliedScoredRows mirrors scoreboard.getEntries().size() for the objective this Board
            // owns exclusively, so the row-count compare no longer materializes the entry set twice
            // per update.
            if (appliedScoredRows != entries.length) {
                scoreboard.getEntries().forEach(this::removeEntry);
                appliedScoredRows = 0;
                Arrays.fill(appliedScores, UNSET_SCORE);
            }

            for (int i = 0; i < entries.length; i++) {
                String line = entries[i];
                Team team = scoreboard.getTeam(CACHED_ENTRIES[i]);

                if (team == null) {
                    team = scoreboard.registerNewTeam(CACHED_ENTRIES[i]);
                    team.addEntry(team.getName());
                    appliedLines[i] = null;
                }

                if (!line.equals(appliedLines[i])) {
                    BoardEntry entry = BoardEntry.translateToEntry(line);
                    team.setPrefix(entry.getPrefix());
                    team.setSuffix(entry.getSuffix());
                    appliedLines[i] = line;
                }

                int score = scoreUp ? 1 + i : MAX_LINES - i;
                if (appliedScores[i] != score) {
                    objective.getScore(team.getName()).setScore(score);
                    appliedScores[i] = score;
                }
            }

            appliedScoredRows = entries.length;
            if (!normalObjectiveDisplayed) {
                objective.setDisplaySlot(DisplaySlot.SIDEBAR);
                normalObjectiveDisplayed = true;
            }
            return true;
        } catch (UnsupportedOperationException ignored) {
            forgetAppliedState();
            return false;
        }
    }

    /** Drops every last-applied cache so the next render re-sends the full board. */
    private void forgetAppliedState() {
        appliedTitle = null;
        appliedHideScores = null;
        appliedScoredRows = 0;
        Arrays.fill(appliedLines, null);
        Arrays.fill(appliedScores, UNSET_SCORE);
    }

    private static String normalizeTitle(String value) {
        return BoardEntry.normalizeSingleLine(translateColors(value), MAX_TITLE_LENGTH);
    }

    private static String normalizeLine(String value) {
        return BoardEntry.normalizeSingleLine(translateColors(value), BoardEntry.MAX_LINE_LENGTH);
    }

    private static String translateColors(String value) {
        if (value == null || value.indexOf('&') < 0) {
            return value == null ? "" : value;
        }
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    private static void reverse(String[] values) {
        for (int head = 0, tail = values.length - 1; head < tail; head++, tail--) {
            String swap = values[head];
            values[head] = values[tail];
            values[tail] = swap;
        }
    }

    static boolean effectiveHideScoreNumbers(boolean requested, boolean supported) {
        return requested && supported;
    }

    public void removeEntry(String id) {
        if (!useNormalBackend) {
            return;
        }
        Scoreboard scoreboard = this.getScoreboard();
        if (scoreboard == null) {
            return;
        }
        scoreboard.resetScores(id);
    }

    public void resetScoreboard() {
        remove();
    }

    public boolean ownsScoreboardAssignment() {
        return ownsScoreboardAssignment(currentScoreboard(), ownedScoreboard, previousScoreboard);
    }

    private Objective initializeNormalBackend(Scoreboard scoreboard, String initialTitle) {
        Objective existingObjective = scoreboard.getObjective("board");
        Objective resolvedObjective = existingObjective == null
                ? scoreboard.registerNewObjective("board", Criteria.DUMMY, initialTitle)
                : existingObjective;
        Team team = scoreboard.getTeam("board");
        if (team == null) {
            team = scoreboard.registerNewTeam("board");
        }
        team.setAllowFriendlyFire(true);
        team.setCanSeeFriendlyInvisibles(false);
        team.setPrefix("");
        team.setSuffix("");
        return resolvedObjective;
    }

    private boolean shouldAttemptNormalBackend() {
        return !FoliaScheduler.isFolia(Bukkit.getServer()) && !CANVAS_RUNTIME;
    }

    private Scoreboard createOwnedScoreboard() {
        if (Bukkit.getScoreboardManager() == null) {
            return null;
        }
        try {
            return Bukkit.getScoreboardManager().getNewScoreboard();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void removeNormalObjective() {
        removeObjective(objective);
    }

    private void restorePreviousScoreboard() {
        if (ownedScoreboard == null || !player.isOnline()) {
            return;
        }
        try {
            Scoreboard active = currentScoreboard();
            Scoreboard restore = selectScoreboardToRestore(active, ownedScoreboard, previousScoreboard);
            if (restore != null && !Objects.equals(active, restore)) {
                player.setScoreboard(restore);
            }
        } catch (Throwable ignored) {
        }
    }

    static Scoreboard selectScoreboardToRestore(Scoreboard active, Scoreboard owned, Scoreboard previous) {
        return Objects.equals(active, owned) ? previous : active;
    }

    static boolean ownsScoreboardAssignment(Scoreboard active, Scoreboard owned, Scoreboard previous) {
        Scoreboard expected = owned == null ? previous : owned;
        return active != null && Objects.equals(active, expected);
    }

    private BoardLease acquireNormalBackend(Scoreboard previous) {
        Scoreboard candidate = createOwnedScoreboard();
        if (candidate == null) {
            return BoardLease.empty();
        }

        Objective candidateObjective = null;
        try {
            String initialTitle = boardSettings == null || boardSettings.getBoardProvider() == null
                    ? ""
                    : normalizeTitle(boardSettings.getBoardProvider().getTitle(player));
            candidateObjective = initializeNormalBackend(candidate, initialTitle);
            player.setScoreboard(candidate);
            if (Objects.equals(currentScoreboard(), candidate)) {
                return new BoardLease(candidate, candidateObjective);
            }
        } catch (Throwable ignored) {
        }

        removeObjective(candidateObjective);
        restoreCandidateScoreboard(candidate, previous);
        return BoardLease.empty();
    }

    private void restoreCandidateScoreboard(Scoreboard candidate, Scoreboard previous) {
        try {
            Scoreboard active = currentScoreboard();
            Scoreboard restore = selectScoreboardToRestore(active, candidate, previous);
            if (restore != null && !Objects.equals(active, restore)) {
                player.setScoreboard(restore);
            }
        } catch (Throwable ignored) {
        }
    }

    private Scoreboard currentScoreboard() {
        try {
            return player.getScoreboard();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void removeObjective(Objective objective) {
        if (objective == null) {
            return;
        }
        try {
            objective.unregister();
        } catch (Throwable ignored) {
        }
    }

    private static boolean detectCanvasRuntime() {
        String serverName = "";
        try {
            if (Bukkit.getServer() != null && Bukkit.getServer().getName() != null) {
                serverName = Bukkit.getServer().getName();
            }
        } catch (Throwable ignored) {
            serverName = "";
        }
        if (serverName.toLowerCase(Locale.ROOT).contains("canvas")) {
            return true;
        }

        ClassLoader loader = Board.class.getClassLoader();
        try {
            if (Bukkit.getServer() != null) {
                loader = Bukkit.getServer().getClass().getClassLoader();
            }
        } catch (Throwable ignored) {
            loader = Board.class.getClassLoader();
        }

        try {
            Class.forName("io.canvasmc.canvas.region.WorldRegionizer", false, loader);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private record BoardLease(Scoreboard scoreboard, Objective objective) {
        private static BoardLease empty() {
            return new BoardLease(null, null);
        }

        private boolean active() {
            return scoreboard != null && objective != null;
        }
    }

    private static final class ScoreNumberFormatBridge {
        private final Method objectiveNumberFormat;
        private final Object blankFormat;

        private ScoreNumberFormatBridge() {
            Method foundObjectiveNumberFormat = null;
            Object foundBlankFormat = null;
            try {
                Class<?> numberFormatClass = Class.forName("io.papermc.paper.scoreboard.numbers.NumberFormat");
                foundObjectiveNumberFormat = Objective.class.getMethod("numberFormat", numberFormatClass);
                foundBlankFormat = numberFormatClass.getMethod("blank").invoke(null);
            } catch (ReflectiveOperationException ignored) {
                foundObjectiveNumberFormat = null;
                foundBlankFormat = null;
            }
            this.objectiveNumberFormat = foundObjectiveNumberFormat;
            this.blankFormat = foundBlankFormat;
        }

        private void apply(Objective objective, boolean hideScores) {
            if (objectiveNumberFormat == null) {
                return;
            }
            try {
                objectiveNumberFormat.invoke(objective, new Object[]{hideScores ? blankFormat : null});
            } catch (ReflectiveOperationException ignored) {
            }
        }

        private boolean isSupported() {
            return objectiveNumberFormat != null && blankFormat != null;
        }
    }

    private static final class PacketSidebar {
        private static final String OWNERSHIP_KEY = "volmlib-board-sidebar-owner-v1";
        private static final PacketBridge BRIDGE = new PacketBridge();

        private final Player player;
        private final boolean supported;
        private final Object scoreboard;
        private final Object objective;
        private final String objectiveName;
        private final String[] teamNames;
        private final String ownershipToken;
        private final Plugin ownerPlugin;
        private final boolean ownershipRegistered;
        private final String[] appliedLines = new String[MAX_LINES];
        private final int[] appliedScores = new int[MAX_LINES];
        private String appliedTitle;
        private Boolean appliedHideScores;
        private boolean createdObjective;
        private boolean displayedObjective;
        private int visibleLines;
        private long lastFailureLogMillis;
        private Throwable initializationFailure;

        private PacketSidebar(Player player) {
            this.player = player;
            Arrays.fill(this.appliedScores, UNSET_SCORE);

            UUID sidebarId = UUID.randomUUID();
            String token = createToken(sidebarId);
            this.objectiveName = clip("ib" + token, 16);
            this.teamNames = new String[MAX_LINES];
            for (int i = 0; i < MAX_LINES; i++) {
                this.teamNames[i] = clip("it" + i + token, 16);
            }

            boolean setupSupported = BRIDGE.supported;
            Object builtScoreboard = null;
            Object builtObjective = null;
            Throwable setupFailure = null;
            if (setupSupported) {
                try {
                    builtScoreboard = BRIDGE.newScoreboard();
                    builtObjective = BRIDGE.newObjective(builtScoreboard, this.objectiveName, "");
                } catch (Throwable throwable) {
                    setupSupported = false;
                    setupFailure = throwable;
                }
            }

            this.supported = setupSupported;
            this.scoreboard = builtScoreboard;
            this.objective = builtObjective;
            this.createdObjective = false;
            this.displayedObjective = false;
            this.visibleLines = 0;
            this.lastFailureLogMillis = 0L;
            this.initializationFailure = setupFailure;
            this.ownershipToken = BoardSidebarClaim.create(System.nanoTime(), sidebarId);
            this.ownerPlugin = this.supported ? findOwnerPlugin() : null;
            this.ownershipRegistered = this.supported && registerOwnership();
            if (!this.supported) {
                logFailure(initializationFailure == null
                        ? new IllegalStateException("Packet scoreboard bridge initialization failed.")
                        : initializationFailure, "init");
            }
        }

        private boolean isSupported() {
            return supported;
        }

        private boolean render(String title, String[] lines, ScoreDirection direction, boolean hideScores) {
            if (!supported || !player.isOnline()) {
                return false;
            }
            if (!ownsPacketSidebar()) {
                displayedObjective = false;
                return true;
            }

            boolean effectiveHideScores = effectiveHideScoreNumbers(hideScores, BRIDGE.supportsNumberFormats());
            try {
                if (!createdObjective) {
                    BRIDGE.setObjectiveDisplayName(objective, title);
                    BRIDGE.setObjectiveNumberFormat(objective, effectiveHideScores);
                    BRIDGE.sendObjectivePacket(player, objective, BRIDGE.objectiveMethodAdd);
                    createdObjective = true;
                    forgetApplied();
                    appliedTitle = title;
                    appliedHideScores = effectiveHideScores;
                } else if (!Objects.equals(appliedHideScores, effectiveHideScores)) {
                    BRIDGE.setObjectiveNumberFormat(objective, effectiveHideScores);
                    BRIDGE.sendObjectivePacket(player, objective, BRIDGE.objectiveMethodChange);
                    appliedHideScores = effectiveHideScores;
                    Arrays.fill(appliedScores, UNSET_SCORE);
                }

                if (!title.equals(appliedTitle)) {
                    BRIDGE.setObjectiveDisplayName(objective, title);
                    BRIDGE.sendObjectivePacket(player, objective, BRIDGE.objectiveMethodChange);
                    appliedTitle = title;
                }

                int size = Math.min(lines.length, MAX_LINES);
                for (int i = 0; i < size; i++) {
                    String line = lines[i];
                    String entryKey = CACHED_ENTRIES[i];

                    if (!line.equals(appliedLines[i])) {
                        BoardEntry entry = BoardEntry.translateToEntry(line);
                        BRIDGE.sendTeamPacket(player, scoreboard, teamNames[i], entryKey, entry.getPrefix(), entry.getSuffix());
                        appliedLines[i] = line;
                    }

                    int score = direction == ScoreDirection.UP ? (1 + i) : (MAX_LINES - i);
                    if (appliedScores[i] != score) {
                        BRIDGE.sendScorePacket(player, entryKey, objectiveName, score, effectiveHideScores);
                        appliedScores[i] = score;
                    }
                }

                for (int i = size; i < visibleLines; i++) {
                    String entryKey = CACHED_ENTRIES[i];
                    String teamName = teamNames[i];
                    BRIDGE.sendResetScorePacket(player, entryKey, objectiveName);
                    BRIDGE.sendTeamRemovePacket(player, scoreboard, teamName);
                    appliedLines[i] = null;
                    appliedScores[i] = UNSET_SCORE;
                }

                visibleLines = size;
                if (!displayedObjective || ownershipRegistered) {
                    BRIDGE.sendDisplayObjectivePacket(player, objective);
                    displayedObjective = true;
                }
                return true;
            } catch (Throwable throwable) {
                logFailure(throwable, "render");
                // A partial send leaves the client in an unknown state: re-send everything next pass.
                forgetApplied();
                return false;
            }
        }

        private void reset() {
            if (!supported) {
                releaseOwnership();
                return;
            }

            try {
                for (int i = 0; i < visibleLines; i++) {
                    BRIDGE.sendResetScorePacket(player, CACHED_ENTRIES[i], objectiveName);
                    BRIDGE.sendTeamRemovePacket(player, scoreboard, teamNames[i]);
                }
                visibleLines = 0;

                if (createdObjective) {
                    BRIDGE.sendObjectivePacket(player, objective, BRIDGE.objectiveMethodRemove);
                    createdObjective = false;
                }
                displayedObjective = false;
            } catch (Throwable throwable) {
                logFailure(throwable, "reset");
                createdObjective = false;
                displayedObjective = false;
                visibleLines = 0;
            } finally {
                forgetApplied();
                releaseOwnership();
            }
        }

        private Plugin findOwnerPlugin() {
            try {
                return JavaPlugin.getProvidingPlugin(Board.class);
            } catch (IllegalArgumentException | IllegalStateException ignored) {
                return null;
            }
        }

        private boolean registerOwnership() {
            if (ownerPlugin == null) {
                return false;
            }
            try {
                player.setMetadata(OWNERSHIP_KEY, new FixedMetadataValue(ownerPlugin, ownershipToken));
                return true;
            } catch (RuntimeException failure) {
                logFailure(failure, "ownership registration");
                return false;
            }
        }

        private boolean ownsPacketSidebar() {
            if (!ownershipRegistered || ownerPlugin == null) {
                return true;
            }
            try {
                List<BoardSidebarClaim.Value> claims = new ArrayList<>();
                for (MetadataValue metadata : player.getMetadata(OWNERSHIP_KEY)) {
                    Plugin plugin = metadata.getOwningPlugin();
                    claims.add(new BoardSidebarClaim.Value(metadata.asString(), plugin != null && plugin.isEnabled()));
                }
                return BoardSidebarClaim.isWinner(ownershipToken, claims);
            } catch (RuntimeException failure) {
                logFailure(failure, "ownership check");
                return true;
            }
        }

        private void releaseOwnership() {
            if (!ownershipRegistered || ownerPlugin == null) {
                return;
            }
            try {
                for (MetadataValue metadata : player.getMetadata(OWNERSHIP_KEY)) {
                    if (ownerPlugin.equals(metadata.getOwningPlugin()) && ownershipToken.equals(metadata.asString())) {
                        player.removeMetadata(OWNERSHIP_KEY, ownerPlugin);
                        return;
                    }
                }
            } catch (RuntimeException failure) {
                logFailure(failure, "ownership release");
            }
        }

        private void forgetApplied() {
            appliedTitle = null;
            appliedHideScores = null;
            Arrays.fill(appliedLines, null);
            Arrays.fill(appliedScores, UNSET_SCORE);
        }

        private void logFailure(Throwable throwable, String phase) {
            long now = System.currentTimeMillis();
            if (now - lastFailureLogMillis < 5000L) {
                return;
            }
            lastFailureLogMillis = now;
            Bukkit.getLogger().log(Level.WARNING, "[VolmLib/Board] Packet sidebar " + phase + " failure for "
                    + player.getName() + " (" + player.getUniqueId() + ").", throwable);
        }

        private static String createToken(UUID uuid) {
            long mixedBits = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
            String token = Long.toUnsignedString(mixedBits, 36);
            if (token.length() > 10) {
                return token.substring(0, 10);
            }
            return token;
        }

        private static String clip(String value, int maxLength) {
            if (value.length() <= maxLength) {
                return value;
            }
            return value.substring(0, maxLength);
        }
    }

    private static final class PacketBridge {
        private final boolean supported;
        private final boolean modernScorePackets;
        private final Method craftPlayerGetHandle;
        private final Field serverPlayerConnection;
        private final Method connectionSendPacket;
        private final Constructor<?> scoreboardConstructor;
        private final Method scoreboardAddObjective;
        private final Method scoreboardAddPlayerToTeam;
        private final Method objectiveSetDisplayName;
        private final Method objectiveSetNumberFormat;
        private final Constructor<?> objectivePacketConstructor;
        private final Constructor<?> displayObjectivePacketConstructor;
        private final Constructor<?> playerTeamConstructor;
        private final Method playerTeamSetPrefix;
        private final Method playerTeamSetSuffix;
        private final Method playerTeamCreateAddOrModifyPacket;
        private final Method playerTeamCreateRemovePacket;
        private final Constructor<?> scorePacketConstructor;
        private final Constructor<?> resetScorePacketConstructor;
        private final Object legacyScoreChange;
        private final Object legacyScoreRemove;
        private final Method craftChatMessageFromStringOrNull;
        private final Object objectiveCriteriaDummy;
        private final Object renderTypeInteger;
        private final Object sidebarDisplaySlot;
        private final Object blankNumberFormat;
        private final int objectiveMethodAdd;
        private final int objectiveMethodRemove;
        private final int objectiveMethodChange;

        private PacketBridge() {
            boolean reflectionReady = false;
            boolean foundModernScorePackets = false;
            Method foundCraftPlayerGetHandle = null;
            Field foundServerPlayerConnection = null;
            Method foundConnectionSendPacket = null;
            Constructor<?> foundScoreboardConstructor = null;
            Method foundScoreboardAddObjective = null;
            Method foundScoreboardAddPlayerToTeam = null;
            Method foundObjectiveSetDisplayName = null;
            Method foundObjectiveSetNumberFormat = null;
            Constructor<?> foundObjectivePacketConstructor = null;
            Constructor<?> foundDisplayObjectivePacketConstructor = null;
            Constructor<?> foundPlayerTeamConstructor = null;
            Method foundPlayerTeamSetPrefix = null;
            Method foundPlayerTeamSetSuffix = null;
            Method foundPlayerTeamCreateAddOrModifyPacket = null;
            Method foundPlayerTeamCreateRemovePacket = null;
            Constructor<?> foundScorePacketConstructor = null;
            Constructor<?> foundResetScorePacketConstructor = null;
            Object foundLegacyScoreChange = null;
            Object foundLegacyScoreRemove = null;
            Method foundCraftChatMessageFromStringOrNull = null;
            Object foundObjectiveCriteriaDummy = null;
            Object foundRenderTypeInteger = null;
            Object foundSidebarDisplaySlot = null;
            Object foundBlankNumberFormat = null;
            int foundObjectiveMethodAdd = 0;
            int foundObjectiveMethodRemove = 1;
            int foundObjectiveMethodChange = 2;

            try {
                Class<?> craftPlayerClass = craftClass("entity.CraftPlayer");
                foundCraftPlayerGetHandle = craftPlayerClass.getMethod("getHandle");

                Class<?> packetClass = Class.forName("net.minecraft.network.protocol.Packet");
                Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
                foundServerPlayerConnection = serverPlayerClass.getField("connection");
                foundConnectionSendPacket = foundServerPlayerConnection.getType().getMethod("send", packetClass);

                Class<?> nmsScoreboardClass = Class.forName("net.minecraft.world.scores.Scoreboard");
                Class<?> objectiveClass = Class.forName("net.minecraft.world.scores.Objective");
                Class<?> objectiveCriteriaClass = Class.forName("net.minecraft.world.scores.criteria.ObjectiveCriteria");
                Class<?> objectiveRenderTypeClass = Class.forName("net.minecraft.world.scores.criteria.ObjectiveCriteria$RenderType");
                Class<?> nmsComponentClass = Class.forName("net.minecraft.network.chat.Component");
                Class<?> numberFormatClass = optionalClass("net.minecraft.network.chat.numbers.NumberFormat");
                Class<?> displaySlotClass = Class.forName("net.minecraft.world.scores.DisplaySlot");
                Class<?> playerTeamClass = Class.forName("net.minecraft.world.scores.PlayerTeam");
                Class<?> craftChatMessageClass = craftClass("util.CraftChatMessage");

                foundScoreboardConstructor = nmsScoreboardClass.getConstructor();
                foundObjectiveCriteriaDummy = objectiveCriteriaClass.getField("DUMMY").get(null);
                foundRenderTypeInteger = Enum.valueOf((Class<Enum>) objectiveRenderTypeClass, "INTEGER");
                foundSidebarDisplaySlot = Enum.valueOf((Class<Enum>) displaySlotClass, "SIDEBAR");

                foundScoreboardAddObjective = findAddObjectiveMethod(
                        nmsScoreboardClass,
                        objectiveCriteriaClass,
                        nmsComponentClass,
                        objectiveRenderTypeClass,
                        numberFormatClass
                );
                foundScoreboardAddPlayerToTeam = nmsScoreboardClass.getMethod("addPlayerToTeam", String.class, playerTeamClass);
                foundObjectiveSetDisplayName = objectiveClass.getMethod("setDisplayName", nmsComponentClass);

                if (numberFormatClass != null) {
                    try {
                        Class<?> blankFormatClass = Class.forName("net.minecraft.network.chat.numbers.BlankFormat");
                        foundObjectiveSetNumberFormat = objectiveClass.getMethod("setNumberFormat", numberFormatClass);
                        foundBlankNumberFormat = blankFormatClass.getField("INSTANCE").get(null);
                    } catch (ReflectiveOperationException ignored) {
                        foundObjectiveSetNumberFormat = null;
                        foundBlankNumberFormat = null;
                    }
                }

                Class<?> objectivePacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetObjectivePacket");
                foundObjectivePacketConstructor = objectivePacketClass.getConstructor(objectiveClass, int.class);
                foundObjectiveMethodAdd = objectivePacketClass.getField("METHOD_ADD").getInt(null);
                foundObjectiveMethodRemove = objectivePacketClass.getField("METHOD_REMOVE").getInt(null);
                foundObjectiveMethodChange = objectivePacketClass.getField("METHOD_CHANGE").getInt(null);

                Class<?> displayObjectivePacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket");
                foundDisplayObjectivePacketConstructor = displayObjectivePacketClass.getConstructor(displaySlotClass, objectiveClass);

                foundPlayerTeamConstructor = playerTeamClass.getConstructor(nmsScoreboardClass, String.class);
                foundPlayerTeamSetPrefix = playerTeamClass.getMethod("setPlayerPrefix", nmsComponentClass);
                foundPlayerTeamSetSuffix = playerTeamClass.getMethod("setPlayerSuffix", nmsComponentClass);

                Class<?> setPlayerTeamPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket");
                foundPlayerTeamCreateAddOrModifyPacket = setPlayerTeamPacketClass.getMethod("createAddOrModifyPacket", playerTeamClass, boolean.class);
                foundPlayerTeamCreateRemovePacket = setPlayerTeamPacketClass.getMethod("createRemovePacket", playerTeamClass);

                Class<?> scorePacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetScorePacket");
                try {
                    foundScorePacketConstructor = scorePacketClass.getConstructor(
                            String.class,
                            String.class,
                            int.class,
                            Optional.class,
                            Optional.class
                    );
                    Class<?> resetScorePacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundResetScorePacket");
                    foundResetScorePacketConstructor = resetScorePacketClass.getConstructor(String.class, String.class);
                    foundModernScorePackets = true;
                } catch (ReflectiveOperationException modernPacketsUnavailable) {
                    Class<?> scoreMethodClass = Class.forName("net.minecraft.server.ServerScoreboard$Method");
                    foundScorePacketConstructor = scorePacketClass.getConstructor(
                            scoreMethodClass,
                            String.class,
                            String.class,
                            int.class
                    );
                    foundLegacyScoreChange = Enum.valueOf((Class<Enum>) scoreMethodClass, "CHANGE");
                    foundLegacyScoreRemove = Enum.valueOf((Class<Enum>) scoreMethodClass, "REMOVE");
                }

                foundCraftChatMessageFromStringOrNull = craftChatMessageClass.getMethod("fromStringOrNull", String.class);

                reflectionReady = true;
            } catch (Throwable ignored) {
                reflectionReady = false;
            }

            this.supported = reflectionReady;
            this.modernScorePackets = foundModernScorePackets;
            this.craftPlayerGetHandle = foundCraftPlayerGetHandle;
            this.serverPlayerConnection = foundServerPlayerConnection;
            this.connectionSendPacket = foundConnectionSendPacket;
            this.scoreboardConstructor = foundScoreboardConstructor;
            this.scoreboardAddObjective = foundScoreboardAddObjective;
            this.scoreboardAddPlayerToTeam = foundScoreboardAddPlayerToTeam;
            this.objectiveSetDisplayName = foundObjectiveSetDisplayName;
            this.objectiveSetNumberFormat = foundObjectiveSetNumberFormat;
            this.objectivePacketConstructor = foundObjectivePacketConstructor;
            this.displayObjectivePacketConstructor = foundDisplayObjectivePacketConstructor;
            this.playerTeamConstructor = foundPlayerTeamConstructor;
            this.playerTeamSetPrefix = foundPlayerTeamSetPrefix;
            this.playerTeamSetSuffix = foundPlayerTeamSetSuffix;
            this.playerTeamCreateAddOrModifyPacket = foundPlayerTeamCreateAddOrModifyPacket;
            this.playerTeamCreateRemovePacket = foundPlayerTeamCreateRemovePacket;
            this.scorePacketConstructor = foundScorePacketConstructor;
            this.resetScorePacketConstructor = foundResetScorePacketConstructor;
            this.legacyScoreChange = foundLegacyScoreChange;
            this.legacyScoreRemove = foundLegacyScoreRemove;
            this.craftChatMessageFromStringOrNull = foundCraftChatMessageFromStringOrNull;
            this.objectiveCriteriaDummy = foundObjectiveCriteriaDummy;
            this.renderTypeInteger = foundRenderTypeInteger;
            this.sidebarDisplaySlot = foundSidebarDisplaySlot;
            this.blankNumberFormat = foundBlankNumberFormat;
            this.objectiveMethodAdd = foundObjectiveMethodAdd;
            this.objectiveMethodRemove = foundObjectiveMethodRemove;
            this.objectiveMethodChange = foundObjectiveMethodChange;
        }

        private Object newScoreboard() throws Exception {
            return scoreboardConstructor.newInstance();
        }

        private Object newObjective(Object scoreboard, String name, String displayName) throws Exception {
            Object component = toVanillaComponent(displayName);
            if (scoreboardAddObjective.getParameterCount() == 4) {
                return scoreboardAddObjective.invoke(
                        scoreboard,
                        name,
                        objectiveCriteriaDummy,
                        component,
                        renderTypeInteger
                );
            }
            return scoreboardAddObjective.invoke(
                    scoreboard,
                    name,
                    objectiveCriteriaDummy,
                    component,
                    renderTypeInteger,
                    Boolean.TRUE,
                    null
            );
        }

        private void setObjectiveDisplayName(Object objective, String displayName) throws Exception {
            Object component = toVanillaComponent(displayName);
            objectiveSetDisplayName.invoke(objective, component);
        }

        private void setObjectiveNumberFormat(Object objective, boolean hideScores) throws Exception {
            if (!supportsNumberFormats()) {
                return;
            }
            objectiveSetNumberFormat.invoke(objective, new Object[]{hideScores ? blankNumberFormat : null});
        }

        private boolean supportsNumberFormats() {
            return modernScorePackets && objectiveSetNumberFormat != null && blankNumberFormat != null;
        }

        private void sendObjectivePacket(Player player, Object objective, int method) throws Exception {
            Object packet = objectivePacketConstructor.newInstance(objective, method);
            sendPacket(player, packet);
        }

        private void sendDisplayObjectivePacket(Player player, Object objective) throws Exception {
            Object packet = displayObjectivePacketConstructor.newInstance(sidebarDisplaySlot, objective);
            sendPacket(player, packet);
        }

        private void sendTeamPacket(Player player, Object scoreboard, String teamName, String entryName, String prefix, String suffix) throws Exception {
            Object team = playerTeamConstructor.newInstance(scoreboard, teamName);
            Object prefixComponent = toVanillaComponent(prefix == null ? "" : prefix);
            Object suffixComponent = toVanillaComponent(suffix == null ? "" : suffix);
            playerTeamSetPrefix.invoke(team, prefixComponent);
            playerTeamSetSuffix.invoke(team, suffixComponent);
            scoreboardAddPlayerToTeam.invoke(scoreboard, entryName, team);
            Object packet = playerTeamCreateAddOrModifyPacket.invoke(null, team, Boolean.TRUE);
            sendPacket(player, packet);
        }

        private void sendTeamRemovePacket(Player player, Object scoreboard, String teamName) throws Exception {
            Object team = playerTeamConstructor.newInstance(scoreboard, teamName);
            Object packet = playerTeamCreateRemovePacket.invoke(null, team);
            sendPacket(player, packet);
        }

        private void sendScorePacket(Player player, String owner, String objectiveName, int score,
                                     boolean hideScores) throws Exception {
            Object packet;
            if (modernScorePackets) {
                Optional<Object> numberFormat = hideScores && blankNumberFormat != null
                        ? Optional.of(blankNumberFormat)
                        : Optional.empty();
                packet = scorePacketConstructor.newInstance(owner, objectiveName, score, Optional.empty(), numberFormat);
            } else {
                packet = scorePacketConstructor.newInstance(legacyScoreChange, owner, objectiveName, score);
            }
            sendPacket(player, packet);
        }

        private void sendResetScorePacket(Player player, String owner, String objectiveName) throws Exception {
            Object packet = modernScorePackets
                    ? resetScorePacketConstructor.newInstance(owner, objectiveName)
                    : scorePacketConstructor.newInstance(legacyScoreRemove, owner, objectiveName, 0);
            sendPacket(player, packet);
        }

        private Object toVanillaComponent(String text) throws Exception {
            String value = text == null ? "" : text;
            // Line text arrives already translated from update(); only titles still carry '&'.
            String legacyText = translateColors(value);
            Object component = craftChatMessageFromStringOrNull.invoke(null, legacyText);
            if (component != null) {
                return component;
            }
            return craftChatMessageFromStringOrNull.invoke(null, " ");
        }

        private void sendPacket(Player player, Object packet) throws Exception {
            Object handle = craftPlayerGetHandle.invoke(player);
            Object connection = serverPlayerConnection.get(handle);
            connectionSendPacket.invoke(connection, packet);
        }

        private static Class<?> optionalClass(String className) {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException ignored) {
                return null;
            }
        }

        private static Class<?> craftClass(String suffix) throws ClassNotFoundException {
            try {
                return Class.forName("org.bukkit.craftbukkit." + suffix);
            } catch (ClassNotFoundException unversionedMissing) {
                if (Bukkit.getServer() == null) {
                    throw unversionedMissing;
                }
                Package serverPackage = Bukkit.getServer().getClass().getPackage();
                if (serverPackage == null || !serverPackage.getName().startsWith("org.bukkit.craftbukkit")) {
                    throw unversionedMissing;
                }
                return Class.forName(serverPackage.getName() + "." + suffix);
            }
        }

        private static Method findAddObjectiveMethod(Class<?> scoreboardClass, Class<?> criteriaClass,
                                                     Class<?> componentClass, Class<?> renderTypeClass,
                                                     Class<?> numberFormatClass) throws NoSuchMethodException {
            if (numberFormatClass != null) {
                try {
                    return scoreboardClass.getMethod(
                            "addObjective",
                            String.class,
                            criteriaClass,
                            componentClass,
                            renderTypeClass,
                            boolean.class,
                            numberFormatClass
                    );
                } catch (NoSuchMethodException ignored) {
                }
            }
            return scoreboardClass.getMethod(
                    "addObjective",
                    String.class,
                    criteriaClass,
                    componentClass,
                    renderTypeClass
            );
        }
    }
}

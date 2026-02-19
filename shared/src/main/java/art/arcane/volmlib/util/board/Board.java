package art.arcane.volmlib.util.board;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

public class Board {
    private static final int MAX_LINES = 15;
    private static final String[] CACHED_ENTRIES = new String[ChatColor.values().length];
    private static final UnaryOperator<String> APPLY_COLOR_TRANSLATION =
            s -> ChatColor.translateAlternateColorCodes('&', s);
    private static final boolean CANVAS_RUNTIME = detectCanvasRuntime();

    static {
        IntStream.range(0, MAX_LINES).forEach(i -> CACHED_ENTRIES[i] = ChatColor.values()[i].toString() + ChatColor.RESET);
    }

    private final Player player;
    private final Objective objective;
    private final PacketSidebar packetSidebar;
    private BoardSettings boardSettings;
    private boolean useNormalBackend;
    private boolean ready;

    public Board(@NonNull Player player, BoardSettings boardSettings) {
        this.player = player;
        this.boardSettings = boardSettings;
        this.packetSidebar = new PacketSidebar(player);

        Scoreboard scoreboard = getScoreboard();
        Objective createdObjective = null;
        boolean initializedNormalBackend = false;

        if (scoreboard != null && shouldAttemptNormalBackend()) {
            try {
                createdObjective = initializeNormalBackend(scoreboard);
                initializedNormalBackend = createdObjective != null;
            } catch (UnsupportedOperationException ignored) {
                initializedNormalBackend = false;
            }
        }

        this.objective = createdObjective;
        this.useNormalBackend = initializedNormalBackend;
        this.ready = initializedNormalBackend || packetSidebar.isSupported();
    }

    public Scoreboard getScoreboard() {
        return player != null ? player.getScoreboard() : null;
    }

    public void setBoardSettings(BoardSettings boardSettings) {
        this.boardSettings = boardSettings;
    }

    public void remove() {
        if (useNormalBackend) {
            resetScoreboard();
            return;
        }
        ready = false;
        packetSidebar.reset();
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

        List<String> entries = new ArrayList<>(boardSettings.getBoardProvider().getLines(player));
        entries.replaceAll(APPLY_COLOR_TRANSLATION);

        if (entries.size() > MAX_LINES) {
            entries = new ArrayList<>(entries.subList(0, MAX_LINES));
        }

        if (boardSettings.getScoreDirection() == ScoreDirection.UP) {
            Collections.reverse(entries);
        }

        String title = boardSettings.getBoardProvider().getTitle(player);
        if (title == null) {
            title = "";
        }
        if (title.length() > 32) {
            Bukkit.getLogger().warning("The title " + title + " is over 32 characters in length, substringing to prevent errors.");
            title = title.substring(0, 32);
        }

        if (useNormalBackend) {
            if (!updateNormal(title, entries)) {
                useNormalBackend = false;
                if (Bukkit.getScoreboardManager() != null) {
                    player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                }
                boolean switchedToPacket = packetSidebar.isSupported()
                        && packetSidebar.render(title, entries, boardSettings.getScoreDirection());
                if (!switchedToPacket && !packetSidebar.isSupported()) {
                    ready = false;
                }
            }
            return;
        }

        if (!packetSidebar.render(title, entries, boardSettings.getScoreDirection()) && !packetSidebar.isSupported()) {
            ready = false;
        }
    }

    private boolean updateNormal(String title, List<String> entries) {
        if (objective == null) {
            return false;
        }

        Scoreboard scoreboard = getScoreboard();
        if (scoreboard == null) {
            return false;
        }

        try {
            objective.setDisplayName(ChatColor.translateAlternateColorCodes('&', title));

            if (scoreboard.getEntries().size() != entries.size()) {
                scoreboard.getEntries().forEach(this::removeEntry);
            }

            for (int i = 0; i < entries.size(); i++) {
                String str = entries.get(i);
                BoardEntry entry = BoardEntry.translateToEntry(str);
                Team team = scoreboard.getTeam(CACHED_ENTRIES[i]);

                if (team == null) {
                    team = scoreboard.registerNewTeam(CACHED_ENTRIES[i]);
                    team.addEntry(team.getName());
                }

                team.setPrefix(entry.getPrefix());
                team.setSuffix(entry.getSuffix());

                switch (boardSettings.getScoreDirection()) {
                    case UP -> objective.getScore(team.getName()).setScore(1 + i);
                    case DOWN -> objective.getScore(team.getName()).setScore(15 - i);
                }
            }
            return true;
        } catch (UnsupportedOperationException ignored) {
            return false;
        }
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
        ready = false;
        if (useNormalBackend) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            return;
        }
        packetSidebar.reset();
    }

    private Objective initializeNormalBackend(Scoreboard scoreboard) {
        Objective existingObjective = scoreboard.getObjective("board");
        Objective resolvedObjective = existingObjective == null
                ? scoreboard.registerNewObjective("board", Criteria.DUMMY, "Iris")
                : existingObjective;
        resolvedObjective.setDisplaySlot(DisplaySlot.SIDEBAR);
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

    private static final class PacketSidebar {
        private static final PacketBridge BRIDGE = new PacketBridge();

        private final Player player;
        private final boolean supported;
        private final Object scoreboard;
        private final Object objective;
        private final String objectiveName;
        private final String[] teamNames;
        private boolean createdObjective;
        private int visibleLines;
        private long lastFailureLogMillis;
        private Throwable initializationFailure;

        private PacketSidebar(Player player) {
            this.player = player;

            String token = createToken(player.getUniqueId());
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
                    builtObjective = BRIDGE.newObjective(builtScoreboard, this.objectiveName, "Iris");
                } catch (Throwable throwable) {
                    setupSupported = false;
                    setupFailure = throwable;
                }
            }

            this.supported = setupSupported;
            this.scoreboard = builtScoreboard;
            this.objective = builtObjective;
            this.createdObjective = false;
            this.visibleLines = 0;
            this.lastFailureLogMillis = 0L;
            this.initializationFailure = setupFailure;
            if (!this.supported) {
                logFailure(initializationFailure == null
                        ? new IllegalStateException("Packet scoreboard bridge initialization failed.")
                        : initializationFailure, "init");
            }
        }

        private boolean isSupported() {
            return supported;
        }

        private boolean render(String title, List<String> lines, ScoreDirection direction) {
            if (!supported || !player.isOnline()) {
                return false;
            }

            try {
                if (!createdObjective) {
                    BRIDGE.sendObjectivePacket(player, objective, BRIDGE.objectiveMethodAdd);
                    BRIDGE.sendDisplayObjectivePacket(player, objective);
                    createdObjective = true;
                }

                BRIDGE.setObjectiveDisplayName(objective, title);
                BRIDGE.sendObjectivePacket(player, objective, BRIDGE.objectiveMethodChange);

                int size = Math.min(lines.size(), MAX_LINES);
                for (int i = 0; i < size; i++) {
                    String line = lines.get(i);
                    BoardEntry entry = BoardEntry.translateToEntry(line);
                    String entryKey = CACHED_ENTRIES[i];
                    String teamName = teamNames[i];
                    BRIDGE.sendTeamPacket(player, scoreboard, teamName, entryKey, entry.getPrefix(), entry.getSuffix());
                    int score = direction == ScoreDirection.UP ? (1 + i) : (MAX_LINES - i);
                    BRIDGE.sendScorePacket(player, entryKey, objectiveName, score);
                }

                for (int i = size; i < visibleLines; i++) {
                    String entryKey = CACHED_ENTRIES[i];
                    String teamName = teamNames[i];
                    BRIDGE.sendResetScorePacket(player, entryKey, objectiveName);
                    BRIDGE.sendTeamRemovePacket(player, scoreboard, teamName);
                }

                visibleLines = size;
                return true;
            } catch (Throwable throwable) {
                logFailure(throwable, "render");
                return false;
            }
        }

        private void reset() {
            if (!supported) {
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
            } catch (Throwable throwable) {
                logFailure(throwable, "reset");
                createdObjective = false;
                visibleLines = 0;
            }
        }

        private void logFailure(Throwable throwable, String phase) {
            long now = System.currentTimeMillis();
            if (now - lastFailureLogMillis < 5000L) {
                return;
            }
            lastFailureLogMillis = now;
            String message = throwable.getClass().getSimpleName();
            String detail = throwable.getMessage();
            if (detail != null && !detail.isBlank()) {
                message = message + ": " + detail;
            }
            Bukkit.getLogger().warning("[VolmLib/Board] Packet sidebar " + phase + " failure for "
                    + player.getName() + " (" + player.getUniqueId() + "): " + message);
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
        private final Method craftPlayerGetHandle;
        private final Field serverPlayerConnection;
        private final Method connectionSendPacket;
        private final Constructor<?> scoreboardConstructor;
        private final Method scoreboardAddObjective;
        private final Method scoreboardAddPlayerToTeam;
        private final Method objectiveSetDisplayName;
        private final Constructor<?> objectivePacketConstructor;
        private final Constructor<?> displayObjectivePacketConstructor;
        private final Constructor<?> playerTeamConstructor;
        private final Method playerTeamSetPrefix;
        private final Method playerTeamSetSuffix;
        private final Method playerTeamCreateAddOrModifyPacket;
        private final Method playerTeamCreateRemovePacket;
        private final Constructor<?> scorePacketConstructor;
        private final Constructor<?> resetScorePacketConstructor;
        private final Method craftChatMessageFromStringOrNull;
        private final Object objectiveCriteriaDummy;
        private final Object renderTypeInteger;
        private final Object sidebarDisplaySlot;
        private final int objectiveMethodAdd;
        private final int objectiveMethodRemove;
        private final int objectiveMethodChange;

        private PacketBridge() {
            boolean reflectionReady = false;
            Method foundCraftPlayerGetHandle = null;
            Field foundServerPlayerConnection = null;
            Method foundConnectionSendPacket = null;
            Constructor<?> foundScoreboardConstructor = null;
            Method foundScoreboardAddObjective = null;
            Method foundScoreboardAddPlayerToTeam = null;
            Method foundObjectiveSetDisplayName = null;
            Constructor<?> foundObjectivePacketConstructor = null;
            Constructor<?> foundDisplayObjectivePacketConstructor = null;
            Constructor<?> foundPlayerTeamConstructor = null;
            Method foundPlayerTeamSetPrefix = null;
            Method foundPlayerTeamSetSuffix = null;
            Method foundPlayerTeamCreateAddOrModifyPacket = null;
            Method foundPlayerTeamCreateRemovePacket = null;
            Constructor<?> foundScorePacketConstructor = null;
            Constructor<?> foundResetScorePacketConstructor = null;
            Method foundCraftChatMessageFromStringOrNull = null;
            Object foundObjectiveCriteriaDummy = null;
            Object foundRenderTypeInteger = null;
            Object foundSidebarDisplaySlot = null;
            int foundObjectiveMethodAdd = 0;
            int foundObjectiveMethodRemove = 1;
            int foundObjectiveMethodChange = 2;

            try {
                Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
                foundCraftPlayerGetHandle = craftPlayerClass.getMethod("getHandle");

                Class<?> packetClass = Class.forName("net.minecraft.network.protocol.Packet");
                Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
                Class<?> serverCommonPacketListenerClass = Class.forName("net.minecraft.server.network.ServerCommonPacketListenerImpl");
                foundServerPlayerConnection = serverPlayerClass.getField("connection");
                foundConnectionSendPacket = serverCommonPacketListenerClass.getMethod("send", packetClass);

                Class<?> nmsScoreboardClass = Class.forName("net.minecraft.world.scores.Scoreboard");
                Class<?> objectiveClass = Class.forName("net.minecraft.world.scores.Objective");
                Class<?> objectiveCriteriaClass = Class.forName("net.minecraft.world.scores.criteria.ObjectiveCriteria");
                Class<?> objectiveRenderTypeClass = Class.forName("net.minecraft.world.scores.criteria.ObjectiveCriteria$RenderType");
                Class<?> nmsComponentClass = Class.forName("net.minecraft.network.chat.Component");
                Class<?> numberFormatClass = Class.forName("net.minecraft.network.chat.numbers.NumberFormat");
                Class<?> displaySlotClass = Class.forName("net.minecraft.world.scores.DisplaySlot");
                Class<?> playerTeamClass = Class.forName("net.minecraft.world.scores.PlayerTeam");
                Class<?> craftChatMessageClass = Class.forName("org.bukkit.craftbukkit.util.CraftChatMessage");

                foundScoreboardConstructor = nmsScoreboardClass.getConstructor();
                foundObjectiveCriteriaDummy = objectiveCriteriaClass.getField("DUMMY").get(null);
                foundRenderTypeInteger = Enum.valueOf((Class<Enum>) objectiveRenderTypeClass, "INTEGER");
                foundSidebarDisplaySlot = Enum.valueOf((Class<Enum>) displaySlotClass, "SIDEBAR");

                foundScoreboardAddObjective = nmsScoreboardClass.getMethod(
                        "addObjective",
                        String.class,
                        objectiveCriteriaClass,
                        nmsComponentClass,
                        objectiveRenderTypeClass,
                        boolean.class,
                        numberFormatClass
                );
                foundScoreboardAddPlayerToTeam = nmsScoreboardClass.getMethod("addPlayerToTeam", String.class, playerTeamClass);
                foundObjectiveSetDisplayName = objectiveClass.getMethod("setDisplayName", nmsComponentClass);

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
                foundScorePacketConstructor = scorePacketClass.getConstructor(String.class, String.class, int.class, Optional.class, Optional.class);

                Class<?> resetScorePacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundResetScorePacket");
                foundResetScorePacketConstructor = resetScorePacketClass.getConstructor(String.class, String.class);

                foundCraftChatMessageFromStringOrNull = craftChatMessageClass.getMethod("fromStringOrNull", String.class);

                reflectionReady = true;
            } catch (Throwable ignored) {
                reflectionReady = false;
            }

            this.supported = reflectionReady;
            this.craftPlayerGetHandle = foundCraftPlayerGetHandle;
            this.serverPlayerConnection = foundServerPlayerConnection;
            this.connectionSendPacket = foundConnectionSendPacket;
            this.scoreboardConstructor = foundScoreboardConstructor;
            this.scoreboardAddObjective = foundScoreboardAddObjective;
            this.scoreboardAddPlayerToTeam = foundScoreboardAddPlayerToTeam;
            this.objectiveSetDisplayName = foundObjectiveSetDisplayName;
            this.objectivePacketConstructor = foundObjectivePacketConstructor;
            this.displayObjectivePacketConstructor = foundDisplayObjectivePacketConstructor;
            this.playerTeamConstructor = foundPlayerTeamConstructor;
            this.playerTeamSetPrefix = foundPlayerTeamSetPrefix;
            this.playerTeamSetSuffix = foundPlayerTeamSetSuffix;
            this.playerTeamCreateAddOrModifyPacket = foundPlayerTeamCreateAddOrModifyPacket;
            this.playerTeamCreateRemovePacket = foundPlayerTeamCreateRemovePacket;
            this.scorePacketConstructor = foundScorePacketConstructor;
            this.resetScorePacketConstructor = foundResetScorePacketConstructor;
            this.craftChatMessageFromStringOrNull = foundCraftChatMessageFromStringOrNull;
            this.objectiveCriteriaDummy = foundObjectiveCriteriaDummy;
            this.renderTypeInteger = foundRenderTypeInteger;
            this.sidebarDisplaySlot = foundSidebarDisplaySlot;
            this.objectiveMethodAdd = foundObjectiveMethodAdd;
            this.objectiveMethodRemove = foundObjectiveMethodRemove;
            this.objectiveMethodChange = foundObjectiveMethodChange;
        }

        private Object newScoreboard() throws Exception {
            return scoreboardConstructor.newInstance();
        }

        private Object newObjective(Object scoreboard, String name, String displayName) throws Exception {
            Object component = toVanillaComponent(displayName);
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

        private void sendScorePacket(Player player, String owner, String objectiveName, int score) throws Exception {
            Object packet = scorePacketConstructor.newInstance(owner, objectiveName, score, Optional.empty(), Optional.empty());
            sendPacket(player, packet);
        }

        private void sendResetScorePacket(Player player, String owner, String objectiveName) throws Exception {
            Object packet = resetScorePacketConstructor.newInstance(owner, objectiveName);
            sendPacket(player, packet);
        }

        private Object toVanillaComponent(String text) throws Exception {
            String value = text == null ? "" : text;
            String legacyText = ChatColor.translateAlternateColorCodes('&', value);
            Object component = craftChatMessageFromStringOrNull.invoke(null, legacyText);
            if (component != null) {
                return component;
            }
            return craftChatMessageFromStringOrNull.invoke(null, "");
        }

        private void sendPacket(Player player, Object packet) throws Exception {
            Object handle = craftPlayerGetHandle.invoke(player);
            Object connection = serverPlayerConnection.get(handle);
            connectionSendPacket.invoke(connection, packet);
        }
    }
}

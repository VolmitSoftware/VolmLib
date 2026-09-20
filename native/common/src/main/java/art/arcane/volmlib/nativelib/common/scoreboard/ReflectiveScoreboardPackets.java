package art.arcane.volmlib.nativelib.common.scoreboard;

import art.arcane.volmlib.nativelib.scoreboard.ScoreboardPackets;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

public class ReflectiveScoreboardPackets implements ScoreboardPackets {
    private final boolean supported;
    private final boolean modernScorePackets;
    private final Method craftPlayerGetHandle;
    private final Field serverPlayerConnection;
    private final Method connectionSendPacket;
    private final Constructor<?> scoreboardConstructor;
    private final Method scoreboardAddPlayerToTeam;
    private final Constructor<?> objectiveConstructor;
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

    public ReflectiveScoreboardPackets() {
        boolean reflectionReady = false;
        boolean foundModernScorePackets = false;
        Method foundCraftPlayerGetHandle = null;
        Field foundServerPlayerConnection = null;
        Method foundConnectionSendPacket = null;
        Constructor<?> foundScoreboardConstructor = null;
        Method foundScoreboardAddPlayerToTeam = null;
        Constructor<?> foundObjectiveConstructor = null;
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

            foundObjectiveConstructor = findObjectiveConstructor(
                    nmsScoreboardClass,
                    objectiveClass,
                    objectiveCriteriaClass,
                    nmsComponentClass,
                    objectiveRenderTypeClass,
                    numberFormatClass
            );
            foundScoreboardAddPlayerToTeam = nmsScoreboardClass.getMethod("addPlayerToTeam", String.class, playerTeamClass);

            if (numberFormatClass != null) {
                try {
                    Class<?> blankFormatClass = Class.forName("net.minecraft.network.chat.numbers.BlankFormat");
                    foundBlankNumberFormat = blankFormatClass.getField("INSTANCE").get(null);
                } catch (ReflectiveOperationException ignored) {
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
        this.scoreboardAddPlayerToTeam = foundScoreboardAddPlayerToTeam;
        this.objectiveConstructor = foundObjectiveConstructor;
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

    @Override
    public boolean supported() {
        return supported;
    }

    @Override
    public ScoreboardHandle newScoreboard() throws Exception {
        return new NativeScoreboard(scoreboardConstructor.newInstance());
    }

    public ObjectiveHandle newObjective(ScoreboardHandle scoreboard, String name, String displayName, boolean hideScores) throws Exception {
        Object component = toVanillaComponent(displayName);
        Object numberFormat = hideScores && supportsNumberFormats() ? blankNumberFormat : null;
        return new NativeObjective(objectiveConstructor.newInstance(objectiveConstructorArguments(
                objectiveConstructor.getParameterCount(),
                ((NativeScoreboard) scoreboard).value(),
                name,
                objectiveCriteriaDummy,
                component,
                renderTypeInteger,
                numberFormat
        )));
    }

    public boolean supportsNumberFormats() {
        return modernScorePackets && objectiveConstructor.getParameterCount() == 7 && blankNumberFormat != null;
    }

    public void sendObjectivePacket(Player player, ObjectiveHandle objective, ObjectiveOperation operation) throws Exception {
        int method = switch (operation) {
            case ADD -> objectiveMethodAdd;
            case REMOVE -> objectiveMethodRemove;
            case CHANGE -> objectiveMethodChange;
        };
        Object packet = objectivePacketConstructor.newInstance(((NativeObjective) objective).value(), method);
        sendPacket(player, packet);
    }

    public void sendDisplayObjectivePacket(Player player, ObjectiveHandle objective) throws Exception {
        Object packet = displayObjectivePacketConstructor.newInstance(sidebarDisplaySlot, ((NativeObjective) objective).value());
        sendPacket(player, packet);
    }

    public void sendTeamPacket(Player player, ScoreboardHandle scoreboard, String teamName, String entryName, String prefix, String suffix) throws Exception {
        Object team = playerTeamConstructor.newInstance(((NativeScoreboard) scoreboard).value(), teamName);
        Object prefixComponent = toVanillaComponent(prefix == null ? "" : prefix);
        Object suffixComponent = toVanillaComponent(suffix == null ? "" : suffix);
        playerTeamSetPrefix.invoke(team, prefixComponent);
        playerTeamSetSuffix.invoke(team, suffixComponent);
        scoreboardAddPlayerToTeam.invoke(((NativeScoreboard) scoreboard).value(), entryName, team);
        Object packet = playerTeamCreateAddOrModifyPacket.invoke(null, team, Boolean.TRUE);
        sendPacket(player, packet);
    }

    public void sendTeamRemovePacket(Player player, ScoreboardHandle scoreboard, String teamName) throws Exception {
        Object team = playerTeamConstructor.newInstance(((NativeScoreboard) scoreboard).value(), teamName);
        Object packet = playerTeamCreateRemovePacket.invoke(null, team);
        sendPacket(player, packet);
    }

    public void sendScorePacket(Player player, String owner, String objectiveName, int score,
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

    public void sendResetScorePacket(Player player, String owner, String objectiveName) throws Exception {
        Object packet = modernScorePackets
                ? resetScorePacketConstructor.newInstance(owner, objectiveName)
                : scorePacketConstructor.newInstance(legacyScoreRemove, owner, objectiveName, 0);
        sendPacket(player, packet);
    }

    private Object toVanillaComponent(String text) throws Exception {
        String value = text == null ? "" : text;
        // Line text arrives already translated from update(); only titles still carry '&'.
        String legacyText = value.indexOf('&') < 0 ? value : ChatColor.translateAlternateColorCodes('&', value);
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

    private static Constructor<?> findObjectiveConstructor(Class<?> scoreboardClass, Class<?> objectiveClass,
                                                           Class<?> criteriaClass, Class<?> componentClass,
                                                           Class<?> renderTypeClass, Class<?> numberFormatClass)
            throws NoSuchMethodException {
        if (numberFormatClass != null) {
            try {
                return objectiveClass.getConstructor(
                        scoreboardClass,
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
        return objectiveClass.getConstructor(
                scoreboardClass,
                String.class,
                criteriaClass,
                componentClass,
                renderTypeClass
        );
    }

    private static Object[] objectiveConstructorArguments(int parameterCount, Object scoreboard, String name,
                                                          Object criteria, Object component, Object renderType,
                                                          Object numberFormat) {
        if (parameterCount == 5) {
            return new Object[]{scoreboard, name, criteria, component, renderType};
        }
        return new Object[]{scoreboard, name, criteria, component, renderType, Boolean.TRUE, numberFormat};
    }
    private record NativeScoreboard(Object value) implements ScoreboardHandle {
    }

    private record NativeObjective(Object value) implements ObjectiveHandle {
    }
}

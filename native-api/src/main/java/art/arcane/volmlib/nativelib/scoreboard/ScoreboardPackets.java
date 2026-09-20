package art.arcane.volmlib.nativelib.scoreboard;

import art.arcane.volmlib.nativelib.NativeBinding;
import org.bukkit.entity.Player;

@NativeBinding("scoreboard.NativeScoreboardPackets")
public interface ScoreboardPackets {
    boolean supported();
    boolean supportsNumberFormats();
    ScoreboardHandle newScoreboard() throws Exception;
    ObjectiveHandle newObjective(ScoreboardHandle scoreboard, String name, String displayName, boolean hideScores) throws Exception;
    void sendObjectivePacket(Player player, ObjectiveHandle objective, ObjectiveOperation operation) throws Exception;
    void sendDisplayObjectivePacket(Player player, ObjectiveHandle objective) throws Exception;
    void sendTeamPacket(Player player, ScoreboardHandle scoreboard, String teamName, String entryName, String prefix, String suffix) throws Exception;
    void sendTeamRemovePacket(Player player, ScoreboardHandle scoreboard, String teamName) throws Exception;
    void sendScorePacket(Player player, String owner, String objectiveName, int score, boolean hideScores) throws Exception;
    void sendResetScorePacket(Player player, String owner, String objectiveName) throws Exception;

    interface ScoreboardHandle {
    }

    interface ObjectiveHandle {
    }

    enum ObjectiveOperation {
        ADD, REMOVE, CHANGE
    }
}

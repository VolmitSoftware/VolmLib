package art.arcane.volmlib.util.director.runtime;

public interface DirectorSender {
    String getName();

    boolean isPlayer();

    default void sendMessage(String message) {
    }
}

package art.arcane.volmlib.util.director.runtime;

public interface DirectorSender {
    String getName();

    boolean isPlayer();

    void sendMessage(String message);
}

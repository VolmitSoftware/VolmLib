package art.arcane.volmlib.util.director;

import art.arcane.volmlib.util.director.help.DirectorHelpMessages;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeMessages;
import art.arcane.volmlib.util.localization.MessageKey;

import java.util.ArrayList;
import java.util.List;

public final class DirectorMessages {
    private static final List<MessageKey> KEYS = createKeys();

    private DirectorMessages() {
    }

    public static List<MessageKey> keys() {
        return KEYS;
    }

    private static List<MessageKey> createKeys() {
        List<MessageKey> keys = new ArrayList<>(DirectorHelpMessages.keys());
        keys.addAll(DirectorRuntimeMessages.keys());
        return List.copyOf(keys);
    }
}

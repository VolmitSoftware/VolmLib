package art.arcane.volmlib.nativelib.client;

import java.util.function.Function;
import java.util.function.Supplier;

public record ClientWorldPolicy(String namespace, Class<?> generatorType,
                                Function<String, String> presetLabel,
                                boolean structuresRequired, boolean skipExperimentalWarning,
                                Supplier<String> structuresRequiredTitle,
                                Supplier<String> structuresRequiredBody) {
}

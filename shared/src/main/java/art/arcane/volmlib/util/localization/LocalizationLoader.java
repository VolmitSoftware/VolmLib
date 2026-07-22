package art.arcane.volmlib.util.localization;

@FunctionalInterface
public interface LocalizationLoader {
    LocalizationCandidate load() throws Exception;
}

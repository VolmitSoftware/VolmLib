package art.arcane.volmlib.util.director.parse;

public final class DirectorValue<T> {
    private final T value;
    private final DirectorConfidence confidence;

    private DirectorValue(T value, DirectorConfidence confidence) {
        this.value = value;
        this.confidence = confidence;
    }

    public static <T> DirectorValue<T> of(T value, DirectorConfidence confidence) {
        return new DirectorValue<>(value, confidence);
    }

    public static <T> DirectorValue<T> high(T value) {
        return new DirectorValue<>(value, DirectorConfidence.HIGH);
    }

    public static <T> DirectorValue<T> low(T value) {
        return new DirectorValue<>(value, DirectorConfidence.LOW);
    }

    public static <T> DirectorValue<T> invalid(T value) {
        return new DirectorValue<>(value, DirectorConfidence.INVALID);
    }

    public T getValue() {
        return value;
    }

    public DirectorConfidence getConfidence() {
        return confidence;
    }
}

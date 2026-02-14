package art.arcane.volmlib.integration;

public record IntegrationMetricSample(
        IntegrationMetricDescriptor descriptor,
        Double numericValue,
        boolean available,
        long sampledAtMs,
        String message
) {
    public IntegrationMetricSample {
        if (descriptor == null) {
            throw new IllegalArgumentException("Descriptor cannot be null");
        }

        if (!available) {
            numericValue = null;
        }

        message = message == null ? "" : message;
    }

    public static IntegrationMetricSample available(IntegrationMetricDescriptor descriptor, double value, long sampledAtMs) {
        return new IntegrationMetricSample(descriptor, value, true, sampledAtMs, "");
    }

    public static IntegrationMetricSample unavailable(IntegrationMetricDescriptor descriptor, String reason, long sampledAtMs) {
        return new IntegrationMetricSample(descriptor, null, false, sampledAtMs, reason == null ? "unavailable" : reason);
    }

    public double valueOr(double fallback) {
        return available && numericValue != null ? numericValue : fallback;
    }
}

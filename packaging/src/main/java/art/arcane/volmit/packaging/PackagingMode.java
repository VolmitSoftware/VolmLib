package art.arcane.volmit.packaging;

import org.gradle.api.GradleException;
import org.gradle.api.provider.ProviderFactory;

import java.util.Locale;

public record PackagingMode(boolean development, String source) {
    public static final String PROPERTY = "volmitPackaging";
    public static final String VARIABLE = "VOLMIT_PACKAGING";
    private static final String DEVELOPMENT_VALUE = "dev";
    private static final String RELEASE_VALUE = "release";
    private static final PackagingMode RELEASE = new PackagingMode(false, RELEASE_VALUE);

    public static PackagingMode resolve(ProviderFactory providers) {
        PackagingMode property = read(PROPERTY, providers.gradleProperty(PROPERTY).getOrElse(""));
        if (property != null) {
            return property;
        }
        PackagingMode variable = read(VARIABLE, providers.environmentVariable(VARIABLE).getOrElse(""));
        if (variable != null) {
            return variable;
        }
        return RELEASE;
    }

    public String label() {
        return development ? DEVELOPMENT_VALUE : RELEASE_VALUE;
    }

    private static PackagingMode read(String origin, String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "" -> null;
            case DEVELOPMENT_VALUE -> new PackagingMode(true, origin + "=" + DEVELOPMENT_VALUE);
            case RELEASE_VALUE -> new PackagingMode(false, origin + "=" + RELEASE_VALUE);
            default -> throw new GradleException("Unknown packaging mode " + origin + "=" + value
                    + "; expected " + DEVELOPMENT_VALUE + " or " + RELEASE_VALUE);
        };
    }
}

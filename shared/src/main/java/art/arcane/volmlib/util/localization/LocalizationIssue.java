package art.arcane.volmlib.util.localization;

import java.util.Objects;

public record LocalizationIssue(
        LocalizationSeverity severity,
        LocalizationIssueCode code,
        String source,
        String key,
        String detail
) {
    public LocalizationIssue {
        severity = Objects.requireNonNull(severity, "Localization issue severity cannot be null");
        code = Objects.requireNonNull(code, "Localization issue code cannot be null");
        source = source == null ? "" : source;
        key = key == null ? "" : key;
        detail = detail == null ? "" : detail;
    }
}

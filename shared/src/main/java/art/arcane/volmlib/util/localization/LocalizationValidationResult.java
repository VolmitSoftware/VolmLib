package art.arcane.volmlib.util.localization;

import java.util.ArrayList;
import java.util.List;

public final class LocalizationValidationResult {
    private static final LocalizationValidationResult EMPTY = new LocalizationValidationResult(List.of());

    private final List<LocalizationIssue> issues;
    private final List<LocalizationIssue> errors;
    private final List<LocalizationIssue> warnings;

    public LocalizationValidationResult(List<LocalizationIssue> issues) {
        this.issues = List.copyOf(issues);
        List<LocalizationIssue> foundErrors = new ArrayList<>();
        List<LocalizationIssue> foundWarnings = new ArrayList<>();
        for (LocalizationIssue issue : this.issues) {
            if (issue.severity() == LocalizationSeverity.ERROR) {
                foundErrors.add(issue);
            } else {
                foundWarnings.add(issue);
            }
        }
        errors = List.copyOf(foundErrors);
        warnings = List.copyOf(foundWarnings);
    }

    public static LocalizationValidationResult empty() {
        return EMPTY;
    }

    public List<LocalizationIssue> issues() {
        return issues;
    }

    public List<LocalizationIssue> errors() {
        return errors;
    }

    public List<LocalizationIssue> warnings() {
        return warnings;
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public void throwIfInvalid() {
        if (!isValid()) {
            throw new LocalizationValidationException(this);
        }
    }
}

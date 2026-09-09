package art.arcane.volmlib.util.diagnostics;

import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;

public final class BukkitDebugMessages {
    public static final TextKey MISSING_PERMISSION = TextKey.of(
            "debug.error.missing-permission", "Missing permission: {permission}");
    public static final TextKey ALREADY_PREPARING = TextKey.of(
            "debug.error.already-preparing", "A debug dump is already being prepared for {plugin}.");
    public static final TextKey ALREADY_PREPARING_SHORT = TextKey.of(
            "debug.error.already-preparing-short", "A debug dump is already being prepared.");
    public static final TextKey PREPARING = TextKey.of(
            "debug.preparing", "Preparing {plugin} debug dump...");
    public static final TextKey SCHEDULE_FAILED = TextKey.of(
            "debug.error.schedule-failed", "Unable to schedule the debug dump.");
    public static final TextKey PROVIDER_CLOSED = TextKey.of(
            "debug.error.provider-closed", "The debug provider is closed.");
    public static final TextKey CLOSED_BEFORE_CAPTURE = TextKey.of(
            "debug.error.closed-before-capture", "The debug provider closed before the report was captured.");
    public static final TextKey WRITER_SCHEDULE_FAILED = TextKey.of(
            "debug.error.writer-schedule-failed", "Unable to schedule the debug dump writer.");
    public static final TextKey CAPTURE_FAILED = TextKey.of(
            "debug.error.capture-failed", "Unable to capture the debug dump; check the server console.");
    public static final TextKey CLOSED_BEFORE_WRITE = TextKey.of(
            "debug.error.closed-before-write", "The debug provider closed before the report was written.");
    public static final TextKey WRITE_FAILED = TextKey.of(
            "debug.error.write-failed", "Unable to write the debug dump; check the server console.");
    public static final TextKey UPLOAD_INTERRUPTED = TextKey.of(
            "debug.upload.interrupted", "Debug dump upload was interrupted; the local report is saved.");
    public static final TextKey UPLOAD_FAILED = TextKey.of(
            "debug.upload.failed", "Debug dump upload failed; the local report is saved.");
    public static final TextKey UPLOADED_AS = TextKey.of(
            "debug.upload.completed", "Uploaded as VolmitSoftware - {plugin} - v{version}.");
    public static final TextKey OPEN_LABEL = TextKey.of(
            "debug.action.open", "Open: {url}");
    public static final TextKey OPEN_DESCRIPTION = TextKey.of(
            "debug.action.open-description", "Open the uploaded diagnostic report.");
    public static final TextKey SAVED = TextKey.of(
            "debug.saved", "Saved {plugin} debug dump to {path}.");
    public static final TextKey COPY_PATH = TextKey.of(
            "debug.action.copy-path", "Copy local path");
    public static final TextKey COPY_PATH_DESCRIPTION = TextKey.of(
            "debug.action.copy-path-description", "Copy the local report path.");

    private static final List<MessageKey> KEYS = List.of(
            MISSING_PERMISSION,
            ALREADY_PREPARING,
            ALREADY_PREPARING_SHORT,
            PREPARING,
            SCHEDULE_FAILED,
            PROVIDER_CLOSED,
            CLOSED_BEFORE_CAPTURE,
            WRITER_SCHEDULE_FAILED,
            CAPTURE_FAILED,
            CLOSED_BEFORE_WRITE,
            WRITE_FAILED,
            UPLOAD_INTERRUPTED,
            UPLOAD_FAILED,
            UPLOADED_AS,
            OPEN_LABEL,
            OPEN_DESCRIPTION,
            SAVED,
            COPY_PATH,
            COPY_PATH_DESCRIPTION
    );

    private BukkitDebugMessages() {
    }

    public static List<MessageKey> keys() {
        return KEYS;
    }
}

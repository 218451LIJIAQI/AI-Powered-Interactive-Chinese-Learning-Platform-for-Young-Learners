package com.example.aichinese.util;

/**
 * Produces concise, log-safe summaries for best-effort failure paths.
 *
 * <p>Use this when the application deliberately degrades and continues
 * after a secondary side effect fails (for example email, audit, or
 * notification delivery). WARN logs should identify the failure without
 * flooding production logs with full stack traces; callers can still log
 * the throwable at DEBUG when deep diagnostics are needed.</p>
 */
public final class ExceptionLogUtils {

    private static final int MAX_EXCEPTION_SUMMARY_LENGTH = 220;

    private ExceptionLogUtils() {
    }

    public static String exceptionType(Throwable exception) {
        return exception == null ? "UnknownException" : exception.getClass().getSimpleName();
    }

    /** The exception type plus its redacted message, if any, cut to a log-friendly length. */
    public static String safeSummary(Throwable exception) {
        String type = exceptionType(exception);
        String message = exception == null ? null : TextUtils.trimToNull(exception.getMessage());
        return TextUtils.truncateWithEllipsis(
                message == null ? type : type + ": " + SensitiveDataRedactor.redactText(message), MAX_EXCEPTION_SUMMARY_LENGTH);
    }
}

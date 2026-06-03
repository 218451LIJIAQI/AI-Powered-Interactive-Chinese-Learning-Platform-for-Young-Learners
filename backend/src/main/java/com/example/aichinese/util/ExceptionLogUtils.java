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
        throw new AssertionError("ExceptionLogUtils is a utility class and must not be instantiated.");
    }

    public static String exceptionType(Throwable exception) {
        return exception == null ? "UnknownException" : exception.getClass().getSimpleName();
    }

    private static String safeMessage(Throwable exception) {
        if (exception == null) {
            return null;
        }
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return null;
        }
        String redacted = SensitiveDataRedactor.redactText(message);
        return TextUtils.truncateWithEllipsis(redacted, MAX_EXCEPTION_SUMMARY_LENGTH);
    }

    public static String safeSummary(Throwable exception) {
        String type = exceptionType(exception);
        String message = safeMessage(exception);
        return TextUtils.truncateWithEllipsis(message == null ? type : type + ": " + message, MAX_EXCEPTION_SUMMARY_LENGTH);
    }

}

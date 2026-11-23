package com.example.aichinese.util;

import org.slf4j.Logger;
import org.springframework.core.NestedExceptionUtils;

/** Redacted diagnostics for failures in email, notifications and other best-effort work. */
public final class ExceptionLogUtils {

    private static final int MAX_EXCEPTION_SUMMARY_LENGTH = 220;
    private static final int MAX_DEBUG_FRAMES = 10;

    private ExceptionLogUtils() {
    }

    /** The exception type plus its redacted message, if any, cut to a log-friendly length. */
    public static String safeSummary(Throwable exception) {
        String type = exception.getClass().getSimpleName();
        String message = TextUtils.trimToNull(exception.getMessage());
        return TextUtils.truncateWithEllipsis(
                message == null ? type : type + ": " + SensitiveDataRedactor.redactText(message), MAX_EXCEPTION_SUMMARY_LENGTH);
    }

    /** Keep the failing class and top stack frames at DEBUG. Provider and database messages can contain private data. */
    public static void debugOrigin(Logger logger, Throwable exception) {
        if (logger.isDebugEnabled()) {
            Throwable cause = NestedExceptionUtils.getMostSpecificCause(exception);
            StackTraceElement[] frames = cause.getStackTrace();
            int limit = Math.min(frames.length, MAX_DEBUG_FRAMES);
            StringBuilder summary = new StringBuilder();
            for (int i = 0; i < limit; i++) {
                if (i > 0) {
                    summary.append(" <- ");
                }
                summary.append(frames[i]);
            }
            if (frames.length > limit) {
                summary.append(" (+").append(frames.length - limit).append(" more)");
            }
            logger.debug("Failure origin {} at [{}]", cause.getClass().getName(), summary);
        }
    }
}

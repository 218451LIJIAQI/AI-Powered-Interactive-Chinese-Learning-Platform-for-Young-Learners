package com.example.aichinese.util;

import org.slf4j.MDC;

/**
 * Holds the per-request correlation id ({@value #HEADER_NAME}) in the logging MDC for log lines,
 * audit records and {@code ApiResponse.requestId}.
 *
 * <p>{@code RequestIdFilter} binds it (the sanitised inbound header or a fresh UUID), writes it back on
 * the response and unbinds it in a {@code finally} block, because servlet worker threads are reused.
 * {@code @Async} workers do not inherit it; {@code AiChatStreamRunner.stream} re-binds it on its stream worker.</p>
 */
public final class RequestIdHolder {

    /** The correlation header: read from the request and written back on the response. */
    public static final String HEADER_NAME = "X-Request-Id";

    private static final String MDC_KEY = "requestId";

    private RequestIdHolder() {
    }

    /** The request id bound to this thread, or {@code null} when none is. */
    public static String get() {
        return MDC.get(MDC_KEY);
    }

    /** The current request id for log lines, or {@code "-"} when none is bound. */
    public static String currentOrDash() {
        String id = get();
        return id == null ? "-" : id;
    }

    /**
     * Binds the id, stripped, to this thread's logging MDC. {@link #unbind()} must follow in a
     * {@code finally} block.
     */
    public static void bind(String requestId) {
        String normalized = TextUtils.trimToNull(requestId);
        if (normalized == null) {
            MDC.remove(MDC_KEY);
        } else {
            MDC.put(MDC_KEY, normalized);
        }
    }

    /** Removes the id bound by {@link #bind(String)} from this thread's logging MDC. */
    public static void unbind() {
        MDC.remove(MDC_KEY);
    }
}

package com.example.aichinese.util;

import org.slf4j.MDC;

/**
 * Thread-local holder for the per-request correlation id ({@value #HEADER_NAME}) used in log lines,
 * audit records and {@code ApiResponse.requestId}.
 *
 * <p>{@code RequestIdFilter} binds it (the sanitised inbound header or a fresh UUID), writes it back on
 * the response and unbinds it in a {@code finally} block, because servlet worker threads are reused.
 * {@code @Async} workers do not inherit it; {@code AiController.chatStream} re-binds it on its stream worker.</p>
 */
public final class RequestIdHolder {

    /**
     * Canonical HTTP header used by the platform for request
     * correlation, both inbound and outbound. Title-case spelling chosen
     * for human readability in log dumps and browser dev-tools.
     */
    public static final String HEADER_NAME = "X-Request-Id";

    private static final String MDC_KEY = "requestId";

    private static final ThreadLocal<String> CURRENT_REQUEST_ID = new ThreadLocal<>();

    private RequestIdHolder() {
    }

    /**
     * @return the request id currently bound to this thread, or
     *         {@code null} when none has been set / the binding was
     *         already cleared
     */
    public static String get() {
        return CURRENT_REQUEST_ID.get();
    }

    /** The current request id for log lines, or {@code "-"} when none is bound. */
    public static String currentOrDash() {
        String id = get();
        return id == null ? "-" : id;
    }

    /**
     * Binds the id to this thread (stripped: the filter's 120-character cut can end in a space) and to the
     * logging MDC; {@link #unbind()} must follow in a {@code finally} block.
     */
    public static void bind(String requestId) {
        CURRENT_REQUEST_ID.set(TextUtils.trimToNull(requestId));
        MDC.put(MDC_KEY, requestId);
    }

    /** Removes the id bound by {@link #bind(String)} from this thread and from the logging MDC. */
    public static void unbind() {
        MDC.remove(MDC_KEY);
        CURRENT_REQUEST_ID.remove();
    }
}

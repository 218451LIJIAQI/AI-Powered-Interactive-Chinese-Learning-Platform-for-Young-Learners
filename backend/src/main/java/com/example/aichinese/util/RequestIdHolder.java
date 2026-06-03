package com.example.aichinese.util;

/**
 * Thread-local holder for the per-request correlation id that ties log
 * lines, audit records, and outbound HTTP calls back to a single inbound
 * HTTP request.
 *
 * <h2>Lifecycle</h2>
 * <p>{@code RequestIdFilter} sets the id at the very start of the
 * servlet filter chain (reading it from the inbound
 * {@value #HEADER_NAME} header when present, generating a fresh UUID
 * otherwise) and is responsible for calling {@link #clear()} in a
 * {@code finally} block. Failing to clear the value would leak the id
 * across HTTP requests because servlet containers reuse worker
 * threads — every consumer of this holder must ensure {@code clear()}
 * runs even on exceptional code paths.</p>
 *
 * <h2>Why not {@link InheritableThreadLocal}</h2>
 * <p>Spring's {@code @Async} executors and the platform's CompletableFuture
 * background tasks deliberately do <em>not</em> inherit the request id:
 * inheriting it would pin the id to a background task long after the
 * inbound request returned, producing log lines that claim to belong to
 * a closed request. Background workers that need correlation ids set
 * them explicitly via {@link #set(String)} at the start of their run.</p>
 *
 * <h2>Wire-format contract</h2>
 * <p>The {@value #HEADER_NAME} header is the canonical name on both the
 * inbound and outbound sides — the front-end, reverse proxies, and
 * outbound HTTP clients (Stripe, OpenAI-compatible AI providers) all
 * agree on this spelling so an id can be threaded end-to-end through a
 * full request fan-out.</p>
 */
public final class RequestIdHolder {

    /**
     * Canonical HTTP header used by the platform for request
     * correlation, both inbound and outbound. Title-case spelling chosen
     * for human readability in log dumps and browser dev-tools.
     */
    public static final String HEADER_NAME = "X-Request-Id";

    private static final ThreadLocal<String> CURRENT_REQUEST_ID = new ThreadLocal<>();

    private RequestIdHolder() {
        throw new AssertionError("RequestIdHolder is a utility class and must not be instantiated.");
    }

    /**
     * Stores the supplied id on the current thread. A {@code null} value
     * is treated as a request to clear the binding (see {@link #clear()})
     * so the underlying {@link ThreadLocal} entry is fully detached from
     * the worker thread rather than retaining a phantom {@code null}
     * mapping that would survive until the worker dies.
     */
    public static void set(String requestId) {
        if (requestId == null) {
            CURRENT_REQUEST_ID.remove();
            return;
        }
        CURRENT_REQUEST_ID.set(requestId);
    }

    /**
     * @return the request id currently bound to this thread, or
     *         {@code null} when none has been set / the binding was
     *         already cleared
     */
    public static String get() {
        return CURRENT_REQUEST_ID.get();
    }

    /**
     * Removes the request id from the current thread. MUST be called
     * from {@code RequestIdFilter}'s {@code finally} block so the
     * binding never leaks across the next HTTP request reusing the same
     * worker thread.
     */
    public static void clear() {
        CURRENT_REQUEST_ID.remove();
    }

}

package com.example.aichinese.dto;

import java.util.List;

import com.example.aichinese.util.RequestIdHolder;

/**
 * Uniform envelope wrapping every JSON response the platform returns.
 *
 * <p>Every {@code RestController} method returns either a raw
 * {@code ApiResponse<T>} or a {@code ResponseEntity<ApiResponse<T>>},
 * which lets the frontend handle success and failure with a single
 * branch on {@link #success}. The {@link #requestId} field carries the
 * per-request correlation id (sourced from {@link RequestIdHolder})
 * back to the caller for support and forensics — a frontend bug
 * report can quote the {@code requestId} and the backend's audit log
 * pinpoints the matching trace.</p>
 *
 * <h2>Wire format</h2>
 * <pre>{@code
 *   { "success": true,  "message": "Login successful.",
 *     "data": { ... },   "errors": [],     "requestId": "abc-123" }
 *   { "success": false, "message": "Validation failed.",
 *     "data": null,      "errors": [...],  "requestId": "abc-456" }
 * }</pre>
 *
 * <p>{@link #errors} is always present (never {@code null}) so frontends
 * can iterate it unconditionally; success responses use an empty list
 * rather than {@code null} for the same reason.</p>
 *
 * <h2>Thread safety</h2>
 * <p>Records are immutable; the embedded {@link #errors} list is wrapped
 * in {@link List#copyOf(java.util.Collection) List.copyOf} on entry so a
 * caller mutating the original list afterwards cannot affect the
 * already-serialised payload.</p>
 *
 * @param <T> the type of the {@link #data} payload
 * @param success    {@code true} for 2xx-style outcomes, {@code false}
 *                   when the request was rejected
 * @param message    short human-readable summary suitable for toast
 *                   display by the frontend
 * @param data       the success payload; {@code null} on failure or for
 *                   void operations
 * @param errors     additional error detail; never {@code null} (empty
 *                   list on success)
 * @param requestId  per-request correlation id; never {@code null} for
 *                   normal request paths but may be {@code null} for
 *                   responses synthesised outside the
 *                   {@link RequestIdHolder} scope
 */
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        List<String> errors,
        String requestId) {

    /**
     * Defensive canonical constructor: copies the {@code errors} list and
     * substitutes an empty list when {@code null} is supplied so the
     * "{@code errors} is never null" wire-format invariant holds even
     * when callers pass {@code null} or a mutable list.
     */
    public ApiResponse {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /**
     * Shared success factory used by every controller path with a payload.
     *
     * @param message human-readable success summary
     * @param data    payload to embed; may be {@code null} for void operations
     * @return a new success envelope tagged with the current request id
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, List.of(), RequestIdHolder.get());
    }

    /**
     * Convenience overload for void operations (logout, delete, mark-as-read,
     * etc.) where there is no domain payload to return. Equivalent to
     * {@code success(message, null)} — the explicit overload makes intent
     * obvious at the call site.
     */
    public static <T> ApiResponse<T> success(String message) {
        return success(message, null);
    }

    /**
     * Shared failure factory. The supplied {@code errors} list is copied
     * defensively (a {@code null} argument is normalised to an empty
     * list) so the wire-format invariant is preserved.
     *
     * @param message human-readable failure summary
     * @param errors  detailed error messages; may be {@code null}
     * @return a new failure envelope with {@link #data} set to
     *         {@code null} and the current request id attached
     */
    public static <T> ApiResponse<T> failure(String message, List<String> errors) {
        return new ApiResponse<>(false, message, null, errors, RequestIdHolder.get());
    }

    /**
     * Convenience overload for a single-error failure response. The
     * {@code error} argument is wrapped into a singleton list; a
     * {@code null} or blank value is dropped so the resulting
     * {@code errors} list is empty rather than containing a confusing
     * {@code null} element.
     */
    public static <T> ApiResponse<T> failure(String message, String error) {
        return failure(message,
                error == null || error.isBlank() ? List.of() : List.of(error));
    }

    /**
     * Convenience overload for a failure response with no detailed
     * errors — only the top-level {@link #message} carries information.
     */
    public static <T> ApiResponse<T> failure(String message) {
        return failure(message, List.of());
    }
}

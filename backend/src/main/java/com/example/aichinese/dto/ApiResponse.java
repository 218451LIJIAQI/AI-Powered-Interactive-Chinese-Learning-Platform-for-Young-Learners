package com.example.aichinese.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 * <p>The component order above is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions (snapshot
 * tests, log fingerprints, etc.). {@link #errors} is always present
 * (never {@code null}) so frontends can iterate it unconditionally and
 * never have to branch on {@code null}; success responses use an empty
 * list rather than {@code null} for the same reason.</p>
 *
 * <h2>Invariants enforced by the canonical constructor</h2>
 * <ol>
 *   <li>{@link #message} is non-{@code null} — every envelope has a
 *       human-readable summary the frontend can display in a toast.</li>
 *   <li>{@link #errors} is non-{@code null} and contains no
 *       {@code null} or blank entries; the canonical constructor
 *       silently drops such entries instead of failing so callers can
 *       safely forward upstream lists they do not fully control.</li>
 *   <li>A {@code success=true} envelope must not carry detailed
 *       {@link #errors}; mixing the two states is a contract violation
 *       and is rejected with {@link IllegalArgumentException}, catching
 *       the bug at the call site rather than letting an inconsistent
 *       payload reach the network.</li>
 * </ol>
 *
 * <h2>Thread safety</h2>
 * <p>Records are immutable; the embedded {@link #errors} list is wrapped
 * in {@link List#copyOf(java.util.Collection) List.copyOf} on entry so a
 * caller mutating the original list afterwards cannot affect the
 * already-serialised payload. {@link List#copyOf} short-circuits when
 * the input is already an unmodifiable list, so the typical hot path
 * (factory methods passing {@link List#of()}) allocates nothing.</p>
 *
 * @param <T> the type of the {@link #data} payload
 * @param success    {@code true} for 2xx-style outcomes, {@code false}
 *                   when the request was rejected
 * @param message    short human-readable summary suitable for toast
 *                   display by the frontend; never {@code null}
 * @param data       the success payload; {@code null} on failure or for
 *                   void operations
 * @param errors     additional error detail; never {@code null} (empty
 *                   list on success), never contains {@code null} or
 *                   blank entries
 * @param requestId  per-request correlation id; never {@code null} for
 *                   normal request paths but may be {@code null} for
 *                   responses synthesised outside the
 *                   {@link RequestIdHolder} scope
 */
@JsonPropertyOrder({ "success", "message", "data", "errors", "requestId" })
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        List<String> errors,
        String requestId) {

    /**
     * Defensive canonical constructor: enforces the three invariants
     * documented on the class Javadoc. Performed in a single pass so
     * the cost is negligible even on hot controller paths.
     */
    public ApiResponse {
        Objects.requireNonNull(message, "ApiResponse message must not be null");
        errors = sanitizeErrors(errors);
        if (success && !errors.isEmpty()) {
            throw new IllegalArgumentException(
                    "ApiResponse.success cannot carry detailed errors; use ApiResponse.failure instead.");
        }
    }

    /**
     * Shared success factory used by every controller path with a payload.
     *
     * @param message human-readable success summary; must not be {@code null}
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
     * Shared failure factory. The supplied {@code errors} list is
     * normalised by the canonical constructor — a {@code null} argument
     * becomes an empty list, and any {@code null} or blank entries are
     * dropped — so the wire-format invariants always hold.
     *
     * @param message human-readable failure summary; must not be {@code null}
     * @param errors  detailed error messages; may be {@code null} or contain
     *                {@code null} / blank entries (both are normalised away)
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

    /**
     * Normalises an arbitrary caller-supplied error list into a
     * deeply-immutable, null-and-blank-free list suitable for direct
     * JSON serialisation.
     *
     * <p>Implements a two-pass strategy biased for the hot path: the
     * first pass scans for {@code null} or blank entries, and only when
     * one is found does the second pass allocate a filtered copy. The
     * common case (every entry is a non-blank message produced by
     * {@code GlobalExceptionHandler}) therefore allocates nothing
     * beyond what {@link List#copyOf} would already short-circuit.</p>
     */
    private static List<String> sanitizeErrors(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        boolean clean = true;
        for (String item : source) {
            if (item == null || item.isBlank()) {
                clean = false;
                break;
            }
        }
        if (clean) {
            return List.copyOf(source);
        }
        List<String> filtered = new ArrayList<>(source.size());
        for (String item : source) {
            if (item != null && !item.isBlank()) {
                filtered.add(item);
            }
        }
        return filtered.isEmpty() ? List.of() : List.copyOf(filtered);
    }
}

package com.example.aichinese.exception;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Translates raw exception messages into safe, user-facing strings.
 *
 * <p>The platform throws {@link org.springframework.web.server.ResponseStatusException}
 * with an internal English {@code reason} string at hundreds of call sites
 * across services, controllers and security filters. Those raw strings are
 * fine for log files but are often too technical, too leaky, or simply
 * inconsistent to surface verbatim to a learner / teacher / admin. This
 * class is the single chokepoint where every error message destined for
 * the client passes through and is either:</p>
 *
 * <ol>
 *   <li><b>Kept verbatim</b> when the raw reason is already a curated,
 *       human-friendly sentence (validation hints, "not found" messages,
 *       known business-rule violations) — always sanitised through
 *       {@link #passthrough(String)} which collapses whitespace and caps
 *       length at {@link #MAX_VERBATIM_LENGTH}.</li>
 *   <li><b>Replaced</b> with one of the canonical messages defined below
 *       (e.g. {@link #permissionDeniedMessage()},
 *       {@link #serverUnavailableMessage()}) when the raw reason would
 *       either leak internals or vary too much to be useful.</li>
 * </ol>
 *
 * <h2>Ordering contract</h2>
 * <p>The cascade in {@link #resolve(HttpStatusCode, String)} is deliberately
 * specific-to-generic so a precise match always wins. The
 * {@code status &gt;= 500} guard is placed <em>before</em> the
 * upload-hint and substring fallbacks so a server-error response that
 * happens to embed a phrase like {@code "file upload"} can never surface
 * the raw reason verbatim — server errors are the most likely source of
 * leaked internals (database error text, JVM-level messages) and must
 * always collapse to {@link #serverUnavailableMessage()}.</p>
 *
 * <h2>Why this is not a Map lookup</h2>
 * <p>The resolution rules combine HTTP-status checks <em>and</em> substring
 * matches on the (lower-cased) raw reason. A {@code Map<String, String>}
 * cannot express "substring matches when status is X but be exact when
 * status is Y", so the linear-if cascade is the simplest correct
 * implementation. Each hint list is declared once as an immutable
 * {@link List} so business-rule phrases live in exactly one place across
 * the entire {@link #resolve} cascade.</p>
 *
 * <h2>Hardening</h2>
 * <p>Verbatim passthrough output is bounded by
 * {@link #MAX_VERBATIM_LENGTH}; multi-line and pathologically long
 * exception messages are collapsed to a single sentence the toast UI can
 * render and never expand the response payload beyond a predictable
 * ceiling.</p>
 */
public final class FriendlyErrorMessageResolver {

    // ===================================================================
    // Configuration constants
    // ===================================================================

    /**
     * Maximum length of a verbatim raw-message passthrough. Anything
     * longer is truncated before being attached to the user-facing
     * response, which protects the platform from pathologically long
     * exception messages (e.g. a stack trace accidentally embedded in a
     * {@code reason} string) blowing up the response payload size or
     * breaking the toast UI on the frontend.
     */
    private static final int MAX_VERBATIM_LENGTH = 400;

    /**
     * HTTP status to assume when the originating exception did not carry
     * one. {@code null} status almost always indicates a programming
     * error inside an interceptor / filter — surfacing it as a server
     * error keeps the contract simple and the client safe.
     */
    private static final int DEFAULT_STATUS = HttpStatus.INTERNAL_SERVER_ERROR.value();

    // ===================================================================
    // Hint lists — every list is a single source of truth, declared once.
    // All entries MUST be lower-cased; matching is performed against the
    // lower-cased normalised message produced by {@link #normalize}.
    // ===================================================================

    /**
     * Role-mismatch sentences that are already user-friendly and explain
     * exactly which role the endpoint requires. These intentionally
     * bypass the generic "permission denied" replacement.
     */
    private static final List<String> ROLE_MISMATCH_HINTS = List.of(
            "only approved teachers can create or submit learning content",
            "only approved teachers can use the teacher assistant",
            "students can only request learning recommendations for their own account",
            "students can only request progress insight for their own account",
            "teacher assistant requires a teacher or admin account",
            "pronunciation feedback requires a student account",
            "writing feedback requires a student account");

    /** Rate-limit / lockout signals from various services. */
    private static final List<String> RATE_LIMIT_HINTS = List.of(
            "too many requests",
            "too many failed login attempts",
            "rate limit");

    /** Existing-email registration signal preserved verbatim. */
    private static final List<String> EMAIL_ALREADY_REGISTERED_HINTS = List.of(
            "email is already registered",
            "email already registered");

    /** Password-confirmation mismatch signal preserved verbatim. */
    private static final List<String> PASSWORD_MISMATCH_HINTS = List.of(
            "confirm password does not match",
            "password mismatch",
            "passwords do not match");

    /** 401 sub-class — wrong credentials, preserved verbatim. */
    private static final List<String> BAD_CREDENTIALS_HINTS = List.of(
            "invalid email or password",
            "bad credentials");

    /**
     * Business-rule "operation cannot be completed in current state"
     * messages. Shared between the explicit-403 branch and the late-stage
     * generic check so each rule appears exactly once.
     */
    private static final List<String> OPERATION_FAILED_HINTS = List.of(
            "already in your cart",
            "already enrolled",
            "already archived",
            "already full",
            "cart is empty",
            "free items do not need",
            "locked until payment is completed",
            "not available to join",
            "cannot receive requester replies",
            "resubmit verification");

    /** Forbidden-with-explanation signals preserved verbatim. */
    private static final List<String> FORBIDDEN_DETAIL_HINTS = List.of(
            "permission to access this resource",
            "access this resource");

    /** Generic 404 sentinel phrases, recognised even when status is not 404. */
    private static final List<String> NOT_FOUND_HINTS = List.of(
            "not found",
            "could not be found",
            "was not found");

    /** Upload-size signals preserved verbatim when status is not yet 413. */
    private static final List<String> UPLOAD_TOO_LARGE_HINTS = List.of(
            "uploaded file exceeds the maximum allowed file size",
            "file is too large",
            "max upload");

    /** Upload-type signals preserved verbatim. */
    private static final List<String> UPLOAD_INVALID_TYPE_HINTS = List.of(
            "file type is not supported",
            "unsupported file type",
            "allowed file types",
            "permitted file format",
            "invalid file extension",
            "supported format");

    /** Generic upload-failure signals preserved verbatim. */
    private static final List<String> UPLOAD_FAILED_HINTS = List.of(
            "uploaded file could not be processed",
            "verification document file is required",
            "uploaded file is required",
            "file upload");

    /** Permission-denied trigger words, used in the late fallback branch. */
    private static final List<String> PERMISSION_HINTS = List.of(
            "permission",
            "forbidden",
            "access denied",
            "can only",
            "only approved",
            "requires a student account",
            "requires a teacher or admin account",
            "requires an admin account",
            "do not have access");

    /** Malformed-request signals preserved verbatim. */
    private static final List<String> MALFORMED_REQUEST_HINTS = List.of(
            "request body",
            "malformed",
            "json payload",
            "query parameter",
            "route or query parameter",
            "invalid value for parameter",
            "invalid numeric",
            "invalid content status filter",
            "invalid teacher status filter",
            "invalid user role filter",
            "invalid moderation target type",
            "invalid feedback type");

    /** Validation-error signals preserved verbatim (already user-friendly). */
    private static final List<String> VALIDATION_HINTS = List.of(
            "validation failed",
            "required",
            "must not",
            "must be",
            "at least one",
            "exactly one",
            "does not match",
            "do not match",
            "not be blank",
            "invalid email",
            "invalid password",
            "missing a correct option",
            "selected learning level does not exist",
            "selected learning category does not exist",
            "between 0 and 100");

    private FriendlyErrorMessageResolver() {
        throw new AssertionError("FriendlyErrorMessageResolver is a utility class and must not be instantiated.");
    }

    /**
     * Resolves a raw exception message into the user-facing string the
     * client should receive.
     *
     * @param statusCode HTTP status code from the originating exception;
     *                   {@code null} is treated as {@link #DEFAULT_STATUS}
     * @param rawMessage raw {@code reason} / {@code message} from the
     *                   exception; may be {@code null} or blank
     * @return a non-{@code null} user-facing string suitable for direct
     *         inclusion in the {@code ApiResponse.message} field
     */
    public static String resolve(HttpStatusCode statusCode, String rawMessage) {
        int status = statusCode == null ? DEFAULT_STATUS : statusCode.value();
        String normalized = normalize(rawMessage);

        if (normalized.isBlank()) {
            return status >= 500 ? serverUnavailableMessage() : operationFailedMessage();
        }

        // ---------- Verbatim-passthrough rules (most specific first) ----------

        if (containsAny(normalized, ROLE_MISMATCH_HINTS)) {
            return passthrough(rawMessage);
        }
        if (containsAny(normalized, RATE_LIMIT_HINTS)) {
            return tooManyRequestsMessage();
        }
        if (containsAny(normalized, EMAIL_ALREADY_REGISTERED_HINTS)) {
            return passthrough(rawMessage);
        }
        if (containsAny(normalized, PASSWORD_MISMATCH_HINTS)) {
            return passthrough(rawMessage);
        }

        // ---------- Status-code-aware rules ----------

        if (status == HttpStatus.UNAUTHORIZED.value()) {
            return containsAny(normalized, BAD_CREDENTIALS_HINTS)
                    ? passthrough(rawMessage)
                    : sessionExpiredMessage();
        }

        if (status == HttpStatus.FORBIDDEN.value()) {
            if (containsAny(normalized, OPERATION_FAILED_HINTS)) {
                return operationFailedMessage();
            }
            if (containsAny(normalized, FORBIDDEN_DETAIL_HINTS)) {
                return passthrough(rawMessage);
            }
            return permissionDeniedMessage();
        }

        if (status == HttpStatus.NOT_FOUND.value() || containsAny(normalized, NOT_FOUND_HINTS)) {
            return passthrough(rawMessage);
        }

        // SECURITY: Server-error messages must never leak technical detail.
        // Place this guard *before* the upload-hint and substring fallbacks so
        // a 5xx response that happens to include a phrase like "file upload"
        // cannot surface the raw reason verbatim.
        if (status >= 500) {
            return serverUnavailableMessage();
        }

        if (status == HttpStatus.PAYLOAD_TOO_LARGE.value()) {
            return uploadTooLargeMessage();
        }
        if (status == HttpStatus.UNPROCESSABLE_ENTITY.value()) {
            return validationFailedMessage();
        }
        if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return tooManyRequestsMessage();
        }

        // ---------- Upload-related rules (4xx with curated reasons) ----------

        if (containsAny(normalized, UPLOAD_TOO_LARGE_HINTS)) {
            return passthrough(rawMessage);
        }
        if (containsAny(normalized, UPLOAD_INVALID_TYPE_HINTS)) {
            return passthrough(rawMessage);
        }
        if (containsAny(normalized, UPLOAD_FAILED_HINTS)) {
            return passthrough(rawMessage);
        }

        // ---------- Final substring-based fallback chain ----------

        if (containsAny(normalized, PERMISSION_HINTS)) {
            return permissionDeniedMessage();
        }
        if (containsAny(normalized, MALFORMED_REQUEST_HINTS)) {
            return passthrough(rawMessage);
        }
        if (containsAny(normalized, VALIDATION_HINTS)) {
            return passthrough(rawMessage);
        }
        if (containsAny(normalized, OPERATION_FAILED_HINTS)) {
            return operationFailedMessage();
        }

        // Anything else: return the trimmed raw message. By this point the
        // status is some 4xx that did not match any hint, so the raw text
        // is treated as already-curated. Service authors are responsible
        // for keeping reasons short and user-readable.
        return passthrough(rawMessage);
    }

    // ===================================================================
    // Canonical message constants (public, used directly by the
    // GlobalExceptionHandler when no raw reason is available).
    // ===================================================================

    public static String sessionExpiredMessage() {
        return "Your session has expired. Please sign in again to continue.";
    }

    public static String invalidCredentialsMessage() {
        return "The email or password is incorrect. Please try again.";
    }

    public static String permissionDeniedMessage() {
        return "You do not have permission to perform this action. Contact an administrator if needed.";
    }

    public static String notFoundMessage() {
        return "We could not find the item you requested. Please refresh and try again.";
    }

    public static String validationFailedMessage() {
        return "The information you entered is incomplete or invalid. Please check it and try again.";
    }

    public static String invalidRequestMessage() {
        return "The request could not be processed. Please check the input and try again.";
    }

    public static String emailAlreadyRegisteredMessage() {
        return "This email is already registered. Please use another email or sign in.";
    }

    public static String passwordMismatchMessage() {
        return "The passwords do not match. Please check them and try again.";
    }

    public static String uploadFailedMessage() {
        return "The file upload did not work. Please check the file type and size, then try again.";
    }

    public static String uploadTooLargeMessage() {
        return "The file is too large. Please choose a smaller file and try again.";
    }

    public static String uploadInvalidTypeMessage() {
        return "This file type is not supported. Please upload a permitted file format.";
    }

    public static String serverUnavailableMessage() {
        return "The service is temporarily unavailable. Please try again later.";
    }

    public static String tooManyRequestsMessage() {
        return "You are doing that too often. Please wait a moment and try again.";
    }

    public static String conflictMessage() {
        return "The request conflicts with the current state. Please refresh and try again.";
    }

    public static String operationFailedMessage() {
        return "We could not complete that action. Please try again shortly.";
    }

    // ===================================================================
    // Internal helpers
    // ===================================================================

    /**
     * Returns {@code true} when {@code normalizedMessage} contains any of
     * the {@code hints} as a substring. The caller is responsible for
     * passing already-normalised (trimmed, whitespace-collapsed,
     * lower-cased) input — see {@link #normalize(String)}.
     */
    private static boolean containsAny(String normalizedMessage, List<String> hints) {
        for (String hint : hints) {
            if (normalizedMessage.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Trims, collapses internal whitespace runs to single spaces, and
     * lower-cases the input so substring matching against the hint lists
     * above is case- and whitespace-insensitive.
     */
    private static String normalize(String rawMessage) {
        if (rawMessage == null) {
            return "";
        }
        return rawMessage.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * Sanitised verbatim passthrough: trims, collapses internal whitespace
     * runs (so multi-line exception messages render as a single sentence
     * in the toast UI), and caps the result at {@link #MAX_VERBATIM_LENGTH}
     * characters. Returns {@link #operationFailedMessage()} for
     * {@code null} / blank inputs as a last-line defence — in normal flow
     * the caller has already verified the normalised message is non-blank.
     */
    private static String passthrough(String rawMessage) {
        if (rawMessage == null) {
            return operationFailedMessage();
        }
        String collapsed = rawMessage.replaceAll("\\s+", " ").trim();
        if (collapsed.isEmpty()) {
            return operationFailedMessage();
        }
        if (collapsed.length() <= MAX_VERBATIM_LENGTH) {
            return collapsed;
        }
        return collapsed.substring(0, MAX_VERBATIM_LENGTH).stripTrailing();
    }
}

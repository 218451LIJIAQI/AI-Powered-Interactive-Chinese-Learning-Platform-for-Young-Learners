package com.example.aichinese.exception;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import com.example.aichinese.util.TextUtils;

public final class FriendlyErrorMessageResolver {

    private static final int MAX_VERBATIM_LENGTH = 400;

    private static final int DEFAULT_STATUS = HttpStatus.INTERNAL_SERVER_ERROR.value();

    private static final List<String> ROLE_MISMATCH_HINTS = List.of(
            "only approved teachers can create or submit learning content",
            "only approved teachers can use this teaching feature",
            "pronunciation feedback requires a student account",
            "writing feedback requires a student account");

    /** Specific, user-actionable W4 messages that must reach the learner verbatim. */
    private static final List<String> EXPLICIT_PASSTHROUGH_HINTS = List.of(
            "account is currently unavailable",
            "hints are not available for independent practice",
            "current password is incorrect",
            "new password must be different",
            "daily ai request limit reached",
            "not available to join");

    private static final List<String> RATE_LIMIT_HINTS = List.of(
            "too many requests",
            "too many failed login attempts",
            "rate limit");

    private static final List<String> EMAIL_ALREADY_REGISTERED_HINTS = List.of(
            "email is already registered");

    private static final List<String> PASSWORD_MISMATCH_HINTS = List.of(
            "confirm password does not match");

    private static final List<String> BAD_CREDENTIALS_HINTS = List.of(
            "invalid email or password");

    private static final List<String> NOT_FOUND_HINTS = List.of(
            "not found",
            "could not be found",
            "was not found");

    private static final List<String> UPLOAD_TOO_LARGE_HINTS = List.of(
            "uploaded file exceeds the maximum allowed file size");

    private static final List<String> UPLOAD_INVALID_TYPE_HINTS = List.of(
            "unsupported file type");

    private static final List<String> UPLOAD_FAILED_HINTS = List.of(
            "verification document file is required",
            "uploaded file is required",
            "file upload");

    private static final List<String> PERMISSION_HINTS = List.of(
            "permission",
            "forbidden",
            "access denied",
            "can only",
            "only approved",
            "requires a student account");

    private static final List<String> MALFORMED_REQUEST_HINTS = List.of(
            "request body",
            "malformed",
            "invalid content status filter",
            "invalid teacher status filter",
            "invalid user role filter",
            "invalid moderation target type",
            "invalid feedback type");

    private static final List<String> VALIDATION_HINTS = List.of(
            "required",
            "must not",
            "must be",
            "at least one",
            "exactly one",
            "does not match",
            "not be blank",
            "invalid email",
            "selected learning level does not exist",
            "selected learning category does not exist",
            "between 0 and 100");

    private FriendlyErrorMessageResolver() {
    }

    public static String resolve(HttpStatusCode statusCode, String rawMessage) {
        int status = statusCode == null ? DEFAULT_STATUS : statusCode.value();
        String normalized = normalize(rawMessage);

        if (normalized.isBlank()) {
            return status >= 500 ? serverUnavailableMessage() : operationFailedMessage();
        }

        if (containsAny(normalized, ROLE_MISMATCH_HINTS) || containsAny(normalized, EXPLICIT_PASSTHROUGH_HINTS)) {
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

        if (status == HttpStatus.UNAUTHORIZED.value()) {
            return containsAny(normalized, BAD_CREDENTIALS_HINTS)
                    ? passthrough(rawMessage)
                    : sessionExpiredMessage();
        }

        if (status == HttpStatus.FORBIDDEN.value()) {
            return permissionDeniedMessage();
        }

        if (status == HttpStatus.NOT_FOUND.value() || containsAny(normalized, NOT_FOUND_HINTS)) {
            return passthrough(rawMessage);
        }

        if (status >= 500) {
            return serverUnavailableMessage();
        }

        if (status == HttpStatus.PAYLOAD_TOO_LARGE.value()) {
            return uploadTooLargeMessage();
        }
        if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return tooManyRequestsMessage();
        }

        if (containsAny(normalized, UPLOAD_TOO_LARGE_HINTS)) {
            return passthrough(rawMessage);
        }
        if (containsAny(normalized, UPLOAD_INVALID_TYPE_HINTS)) {
            return passthrough(rawMessage);
        }
        if (containsAny(normalized, UPLOAD_FAILED_HINTS)) {
            return passthrough(rawMessage);
        }

        if (containsAny(normalized, PERMISSION_HINTS)) {
            return permissionDeniedMessage();
        }
        if (containsAny(normalized, MALFORMED_REQUEST_HINTS)) {
            return passthrough(rawMessage);
        }
        if (containsAny(normalized, VALIDATION_HINTS)) {
            return passthrough(rawMessage);
        }

        return passthrough(rawMessage);
    }

    public static String sessionExpiredMessage() {
        return "Your session has expired. Please sign in again to continue.";
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

    public static String uploadFailedMessage() {
        return "The file upload did not work. Please check the file type and size, then try again.";
    }

    public static String uploadTooLargeMessage() {
        return "The file is too large. Please choose a smaller file and try again.";
    }

    public static String serverUnavailableMessage() {
        return "The service is temporarily unavailable. Please try again later.";
    }

    private static String tooManyRequestsMessage() {
        return "You are doing that too often. Please wait a moment and try again.";
    }

    public static String conflictMessage() {
        return "The request conflicts with the current state. Please refresh and try again.";
    }

    private static String operationFailedMessage() {
        return "We could not complete that action. Please try again shortly.";
    }

    private static boolean containsAny(String normalizedMessage, List<String> hints) {
        for (String hint : hints) {
            if (normalizedMessage.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String rawMessage) {
        String collapsed = TextUtils.collapseWhitespaceToNull(rawMessage);
        if (collapsed == null) {
            return "";
        }
        return collapsed.toLowerCase(Locale.ROOT);
    }

    private static String passthrough(String rawMessage) {
        String collapsed = TextUtils.collapseWhitespaceToNull(rawMessage);
        if (collapsed == null) {
            return operationFailedMessage();
        }
        if (collapsed.length() <= MAX_VERBATIM_LENGTH) {
            return collapsed;
        }
        String truncated = TextUtils.safeTruncate(collapsed, MAX_VERBATIM_LENGTH).stripTrailing();
        return truncated.isEmpty() ? operationFailedMessage() : truncated;
    }
}

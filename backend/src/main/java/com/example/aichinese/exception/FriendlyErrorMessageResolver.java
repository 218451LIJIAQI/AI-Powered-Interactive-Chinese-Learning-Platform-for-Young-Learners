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
            "only approved teachers can use the teacher assistant",
            "students can only request learning recommendations for their own account",
            "students can only request progress insight for their own account",
            "teacher assistant requires a teacher or admin account",
            "pronunciation feedback requires a student account",
            "writing feedback requires a student account");

    private static final List<String> RATE_LIMIT_HINTS = List.of(
            "too many requests",
            "too many failed login attempts",
            "rate limit");

    private static final List<String> EMAIL_ALREADY_REGISTERED_HINTS = List.of(
            "email is already registered",
            "email already registered");

    private static final List<String> PASSWORD_MISMATCH_HINTS = List.of(
            "confirm password does not match",
            "password mismatch",
            "passwords do not match");

    private static final List<String> BAD_CREDENTIALS_HINTS = List.of(
            "invalid email or password",
            "bad credentials");

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

    private static final List<String> FORBIDDEN_DETAIL_HINTS = List.of(
            "permission to access this resource",
            "access this resource");

    private static final List<String> NOT_FOUND_HINTS = List.of(
            "not found",
            "could not be found",
            "was not found");

    private static final List<String> UPLOAD_TOO_LARGE_HINTS = List.of(
            "uploaded file exceeds the maximum allowed file size",
            "file is too large",
            "max upload");

    private static final List<String> UPLOAD_INVALID_TYPE_HINTS = List.of(
            "file type is not supported",
            "unsupported file type",
            "allowed file types",
            "permitted file format",
            "invalid file extension",
            "supported format");

    private static final List<String> UPLOAD_FAILED_HINTS = List.of(
            "uploaded file could not be processed",
            "verification document file is required",
            "uploaded file is required",
            "file upload");

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
    }

    public static String resolve(HttpStatusCode statusCode, String rawMessage) {
        int status = statusCode == null ? DEFAULT_STATUS : statusCode.value();
        String normalized = normalize(rawMessage);

        if (normalized.isBlank()) {
            return status >= 500 ? serverUnavailableMessage() : operationFailedMessage();
        }

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
        if (containsAny(normalized, OPERATION_FAILED_HINTS)) {
            return operationFailedMessage();
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

    public static String tooManyRequestsMessage() {
        return "You are doing that too often. Please wait a moment and try again.";
    }

    public static String conflictMessage() {
        return "The request conflicts with the current state. Please refresh and try again.";
    }

    public static String operationFailedMessage() {
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

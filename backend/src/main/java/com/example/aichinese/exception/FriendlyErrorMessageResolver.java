package com.example.aichinese.exception;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import com.example.aichinese.util.TextUtils;

public final class FriendlyErrorMessageResolver {

    private static final int MAX_VERBATIM_LENGTH = 400;

    /**
     * Specific, user-actionable messages that must reach the user verbatim, whatever their status: role mismatches,
     * account state, hints, the daily AI quota and live-class access.
     */
    private static final List<String> EXPLICIT_PASSTHROUGH_HINTS = List.of(
            "only approved teachers can create or submit learning content",
            "only approved teachers can use this teaching feature",
            "account is currently unavailable",
            "hints are not available for independent practice",
            "daily ai request limit reached",
            "this live class is not available",
            "join window is not open yet");

    private static final List<String> RATE_LIMIT_HINTS = List.of(
            "too many requests",
            "too many failed login attempts",
            "rate limit");

    private static final List<String> BAD_CREDENTIALS_HINTS = List.of(
            "invalid email or password");

    private static final List<String> NOT_FOUND_HINTS = List.of(
            "not found",
            "could not be found",
            "was not found");

    private FriendlyErrorMessageResolver() {
    }

    public static String resolve(HttpStatusCode statusCode, String rawMessage) {
        int status = statusCode.value();
        String normalized = normalize(rawMessage);

        if (normalized.isBlank()) {
            return status >= 500 ? serverUnavailableMessage() : operationFailedMessage();
        }

        if (containsAny(normalized, EXPLICIT_PASSTHROUGH_HINTS)) {
            return passthrough(rawMessage);
        }
        if (containsAny(normalized, RATE_LIMIT_HINTS)) {
            return tooManyRequestsMessage();
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

        // Other 4xx messages (validation, uploads, conflicts) are written for users and shown as they are.
        return passthrough(rawMessage);
    }

    public static String sessionExpiredMessage() {
        return "Your session has expired. Please sign in again to continue.";
    }

    /** A request that carries no credentials at all (nothing expired). */
    public static String signInRequiredMessage() {
        return "Please sign in to continue.";
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
        if (collapsed.length() <= MAX_VERBATIM_LENGTH) {
            return collapsed;
        }
        return TextUtils.safeTruncate(collapsed, MAX_VERBATIM_LENGTH).stripTrailing();
    }
}

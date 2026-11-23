package com.example.aichinese.exception;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import com.example.aichinese.util.TextUtils;

public final class FriendlyErrorMessageResolver {

    private static final int MAX_VERBATIM_LENGTH = 400;

    /**
     * Specific, user-actionable messages that must reach the user verbatim, whatever their status: account state, the
     * learner's own hints-off setting, the daily AI quota and the two limits of "Send the link again" (wait a minute,
     * or until tomorrow).
     */
    private static final List<String> EXPLICIT_PASSTHROUGH_HINTS = List.of(
            "account is currently unavailable",
            "hints are off in your settings",
            "daily ai request limit reached",
            "we just sent a link. please wait a minute before asking for another one",
            "you asked for several links today");

    private static final String BAD_CREDENTIALS_HINT = "invalid email or password";

    private FriendlyErrorMessageResolver() {
    }

    public static String resolve(HttpStatusCode statusCode, String rawMessage) {
        int status = statusCode.value();
        if (status >= 500) {
            return serverUnavailableMessage();
        }

        String message = TextUtils.collapseWhitespaceToNull(rawMessage);
        if (message == null) {
            return switch (status) {
                case 401 -> sessionExpiredMessage();
                case 403 -> permissionDeniedMessage();
                case 404 -> notFoundMessage();
                case 429 -> tooManyRequestsMessage();
                default -> operationFailedMessage();
            };
        }

        String normalized = message.toLowerCase(Locale.ROOT);
        if (EXPLICIT_PASSTHROUGH_HINTS.stream().anyMatch(normalized::contains)) {
            return passthrough(message);
        }

        if (status == HttpStatus.UNAUTHORIZED.value()) {
            return normalized.contains(BAD_CREDENTIALS_HINT) ? passthrough(message) : sessionExpiredMessage();
        }

        if (status == HttpStatus.FORBIDDEN.value()) {
            return permissionDeniedMessage();
        }

        if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return tooManyRequestsMessage();
        }

        // Other 4xx messages (validation, uploads, conflicts, not found) are written for users and shown as they are.
        return passthrough(message);
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

    private static String passthrough(String collapsedMessage) {
        return TextUtils.safeTruncate(collapsedMessage, MAX_VERBATIM_LENGTH).stripTrailing();
    }
}

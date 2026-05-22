package com.example.aichinese.exception;

import java.util.Locale;

import org.springframework.http.HttpStatusCode;

public final class FriendlyErrorMessageResolver {

    private FriendlyErrorMessageResolver() {
    }

    public static String resolve(HttpStatusCode statusCode, String rawMessage) {
        int status = statusCode == null ? 500 : statusCode.value();
        String normalizedMessage = normalize(rawMessage);

        if (normalizedMessage.isBlank()) {
            return status >= 500 ? serverUnavailableMessage() : operationFailedMessage();
        }

        if (containsAny(normalizedMessage,
                "only approved teachers can create or submit learning content",
                "only approved teachers can use the teacher assistant",
                "students can only request learning recommendations for their own account",
                "students can only request progress insight for their own account",
                "teacher assistant requires a teacher or admin account",
                "pronunciation feedback requires a student account",
                "writing feedback requires a student account")) {
            return rawMessage.trim();
        }

        if (containsAny(normalizedMessage, "too many requests", "too many failed login attempts", "rate limit")) {
            return tooManyRequestsMessage();
        }

        if (containsAny(normalizedMessage, "email is already registered", "email already registered")) {
            return rawMessage.trim();
        }

        if (containsAny(normalizedMessage, "confirm password does not match", "password mismatch", "passwords do not match")) {
            return rawMessage.trim();
        }

        if (status == 401) {
            if (containsAny(normalizedMessage, "invalid email or password", "bad credentials")) {
                return rawMessage.trim();
            }
            return sessionExpiredMessage();
        }

        if (status == 403) {
            if (containsAny(normalizedMessage, "already in your cart", "already enrolled", "already archived",
                    "already full", "cart is empty", "free items do not need", "locked until payment is completed",
                    "not available to join", "cannot receive requester replies", "resubmit verification")) {
                return operationFailedMessage();
            }
            if (containsAny(normalizedMessage, "permission to access this resource", "access this resource")) {
                return rawMessage.trim();
            }
            return permissionDeniedMessage();
        }

        if (status == 404 || containsAny(normalizedMessage, "not found", "could not be found", "was not found")) {
            return rawMessage.trim();
        }

        if (status == 413) {
            return uploadTooLargeMessage();
        }

        if (containsAny(normalizedMessage, "uploaded file exceeds the maximum allowed file size", "file is too large", "max upload")) {
            return rawMessage.trim();
        }

        if (containsAny(normalizedMessage, "file type is not supported", "unsupported file type", "allowed file types",
                "permitted file format", "invalid file extension", "supported format")) {
            return rawMessage.trim();
        }

        if (containsAny(normalizedMessage, "uploaded file could not be processed", "verification document file is required",
                "uploaded file is required", "file upload")) {
            return rawMessage.trim();
        }

        if (status == 422) {
            return validationFailedMessage();
        }

        if (status == 429) {
            return tooManyRequestsMessage();
        }

        if (status >= 500) {
            return serverUnavailableMessage();
        }

        if (containsAny(normalizedMessage, "permission", "forbidden", "access denied", "can only", "only approved",
                "requires a student account", "requires a teacher or admin account", "requires an admin account",
                "do not have access")) {
            return permissionDeniedMessage();
        }

        if (containsAny(normalizedMessage, "request body", "malformed", "json payload", "query parameter",
                "route or query parameter", "invalid value for parameter", "invalid numeric", "invalid content status filter",
                "invalid teacher status filter", "invalid user role filter",
                "invalid moderation target type", "invalid feedback type")) {
            return rawMessage.trim();
        }

        if (containsAny(normalizedMessage, "validation failed", "required", "must not", "must be", "at least one",
                "exactly one", "does not match", "do not match", "not be blank", "invalid email", "invalid password",
                "missing a correct option", "selected learning level does not exist", "selected learning category does not exist",
                "between 0 and 100")) {
            return rawMessage.trim();
        }

        if (containsAny(normalizedMessage, "already in your cart", "already enrolled", "already archived", "already full",
                "cart is empty", "free items do not need", "locked until payment is completed", "not available to join",
                "cannot receive requester replies", "resubmit verification")) {
            return operationFailedMessage();
        }

        if (status == 400) {
            return rawMessage.trim();
        }

        return rawMessage.trim();
    }

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

    public static String operationFailedMessage() {
        return "We could not complete that action. Please try again shortly.";
    }

    private static boolean containsAny(String normalizedMessage, String... hints) {
        for (String hint : hints) {
            if (normalizedMessage.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String rawMessage) {
        if (rawMessage == null) {
            return "";
        }
        return rawMessage.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}

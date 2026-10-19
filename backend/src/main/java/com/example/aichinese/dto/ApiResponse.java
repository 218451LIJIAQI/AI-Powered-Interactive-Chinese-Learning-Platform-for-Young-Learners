package com.example.aichinese.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.example.aichinese.util.RequestIdHolder;

@JsonPropertyOrder({ "success", "message", "data", "errors", "fieldErrors", "requestId" })
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        List<String> errors,
        Map<String, List<String>> fieldErrors,
        String requestId) {

    private static final int MAX_FIELD_KEY_LENGTH = 80;

    public ApiResponse {
        message = Objects.requireNonNull(message, "ApiResponse message must not be null").strip();
        if (message.isEmpty()) {
            throw new IllegalArgumentException("ApiResponse message must not be blank");
        }
        errors = sanitizeErrors(errors);
        fieldErrors = sanitizeFieldErrors(fieldErrors);
        requestId = normalizeOptionalText(requestId);
        if (!success) {
            data = null;
        }
        if (success && (!errors.isEmpty() || !fieldErrors.isEmpty())) {
            throw new IllegalArgumentException(
                    "ApiResponse.success cannot carry detailed errors; use ApiResponse.failure instead.");
        }
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, List.of(), Map.of(), RequestIdHolder.get());
    }

    public static <T> ApiResponse<T> failure(String message, List<String> errors) {
        return failure(message, errors, Map.of());
    }

    public static <T> ApiResponse<T> failure(
            String message,
            List<String> errors,
            Map<String, List<String>> fieldErrors) {
        return new ApiResponse<>(false, message, null, errors, fieldErrors, RequestIdHolder.get());
    }

    private static List<String> sanitizeErrors(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        boolean clean = true;
        for (String item : source) {
            if (item == null || item.isBlank() || !item.equals(item.strip())) {
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
                filtered.add(item.strip());
            }
        }
        return filtered.isEmpty() ? List.of() : List.copyOf(filtered);
    }

    private static Map<String, List<String>> sanitizeFieldErrors(Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }

        Map<String, List<String>> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            String key = normalizeOptionalText(entry.getKey());
            if (!isSafeFieldKey(key)) {
                continue;
            }
            List<String> messages = sanitizeErrors(entry.getValue());
            if (!messages.isEmpty()) {
                sanitized.put(key, messages);
            }
        }
        return sanitized.isEmpty() ? Map.of() : Collections.unmodifiableMap(sanitized);
    }

    private static boolean isSafeFieldKey(String key) {
        if (key == null || key.length() > MAX_FIELD_KEY_LENGTH) {
            return false;
        }
        for (int index = 0; index < key.length(); index++) {
            char ch = key.charAt(index);
            if (!(Character.isLetterOrDigit(ch) || ch == '_' || ch == '-' || ch == '.')) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}

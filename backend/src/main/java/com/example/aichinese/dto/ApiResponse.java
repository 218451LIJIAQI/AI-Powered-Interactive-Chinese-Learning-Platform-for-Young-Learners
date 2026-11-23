package com.example.aichinese.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.aichinese.util.RequestIdHolder;

public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Map<String, List<String>> fieldErrors,
        String requestId) {

    public ApiResponse {
        if (fieldErrors == null || fieldErrors.isEmpty()) {
            fieldErrors = Map.of();
        } else {
            Map<String, List<String>> copy = new LinkedHashMap<>(fieldErrors.size());
            fieldErrors.forEach((field, messages) -> copy.put(field, messages == null ? List.of() : List.copyOf(messages)));
            fieldErrors = Collections.unmodifiableMap(copy);
        }
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, Map.of(), RequestIdHolder.get());
    }

    public static <T> ApiResponse<T> failure(String message) {
        return failure(message, Map.of());
    }

    public static <T> ApiResponse<T> failure(String message, Map<String, List<String>> fieldErrors) {
        return new ApiResponse<>(false, message, null, fieldErrors, RequestIdHolder.get());
    }

    public static <T> ApiResponse<T> failure(String message, T data) {
        return new ApiResponse<>(false, message, data, Map.of(), RequestIdHolder.get());
    }
}

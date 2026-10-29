package com.example.aichinese.dto;

import java.util.List;
import java.util.Map;

import com.example.aichinese.util.RequestIdHolder;

public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        List<String> errors,
        Map<String, List<String>> fieldErrors,
        String requestId) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, List.of(), Map.of(), RequestIdHolder.get());
    }

    public static <T> ApiResponse<T> failure(String message) {
        return failure(message, Map.of());
    }

    /** A failure repeats its message as the single {@code errors} entry, which clients use as a fallback. */
    public static <T> ApiResponse<T> failure(String message, Map<String, List<String>> fieldErrors) {
        return new ApiResponse<>(false, message, null, List.of(message), fieldErrors, RequestIdHolder.get());
    }
}

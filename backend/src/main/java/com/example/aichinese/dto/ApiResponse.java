package com.example.aichinese.dto;

import java.util.List;

import com.example.aichinese.util.RequestIdHolder;

public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        List<String> errors,
        String requestId) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, List.of(), RequestIdHolder.get());
    }

    public static <T> ApiResponse<T> failure(String message, List<String> errors) {
        return new ApiResponse<>(false, message, null, errors, RequestIdHolder.get());
    }
}

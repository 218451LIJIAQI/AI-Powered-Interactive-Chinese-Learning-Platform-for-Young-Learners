package com.example.aichinese.service.ai;

import java.util.Objects;

public record AiProviderResult(
        boolean success,
        String providerName,
        String modelName,
        String text,
        Integer tokenCount,
        String errorSummary) {

    public AiProviderResult {
        providerName = requireNonBlank(providerName, "providerName");
        if (modelName != null && modelName.isBlank()) {
            throw new IllegalArgumentException("modelName must not be blank when provided.");
        }
        if (success) {
            validateSuccess(text, tokenCount, errorSummary);
        } else {
            validateFailure(text, tokenCount, errorSummary);
        }
    }

    private static void validateSuccess(String text, Integer tokenCount, String errorSummary) {
        requireNonBlank(text, "Successful provider result text");
        if (tokenCount != null && tokenCount < 0) {
            throw new IllegalArgumentException(
                    "Successful provider result token count must be non-negative.");
        }
        if (errorSummary != null) {
            throw new IllegalArgumentException(
                    "Successful provider result must not carry an error summary.");
        }
    }

    private static void validateFailure(String text, Integer tokenCount, String errorSummary) {
        requireNonBlank(errorSummary, "Failed provider result error summary");
        if (text != null) {
            throw new IllegalArgumentException(
                    "Failed provider result must not carry response text.");
        }
        if (tokenCount != null) {
            throw new IllegalArgumentException(
                    "Failed provider result must not carry a token count.");
        }
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return value;
    }

    public static AiProviderResult success(
            String providerName,
            String modelName,
            String text,
            Integer tokenCount) {
        return new AiProviderResult(true, providerName, modelName, text, tokenCount, null);
    }

    public static AiProviderResult failure(
            String providerName,
            String modelName,
            String errorSummary) {
        return new AiProviderResult(false, providerName, modelName, null, null, errorSummary);
    }
}

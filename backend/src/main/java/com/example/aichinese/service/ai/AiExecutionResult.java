package com.example.aichinese.service.ai;

import java.util.Objects;

import com.example.aichinese.entity.enums.AiRequestStatus;

public record AiExecutionResult(
        AiRequestStatus status,
        String providerName,
        String modelName,
        String responseText,
        Integer tokenCount,
        String userMessage,
        String errorSummary) {

    public AiExecutionResult {
        Objects.requireNonNull(status, "status");
        switch (status) {
            case SUCCESS -> {
                if (responseText == null || responseText.isBlank()) {
                    throw new IllegalArgumentException(
                            "SUCCESS execution must carry non-blank response text.");
                }
                if (userMessage != null) {
                    throw new IllegalArgumentException(
                            "SUCCESS execution must not carry a user message.");
                }
            }
            case UNAVAILABLE, FAILED -> {
                if (userMessage == null || userMessage.isBlank()) {
                    throw new IllegalArgumentException(
                            status + " execution must carry a non-blank user message.");
                }
                if (responseText != null) {
                    throw new IllegalArgumentException(
                            status + " execution must not carry response text.");
                }
                if (tokenCount != null) {
                    throw new IllegalArgumentException(
                            status + " execution must not carry a token count.");
                }
            }
        }
    }

    public static AiExecutionResult success(
            String providerName,
            String modelName,
            String responseText,
            Integer tokenCount) {
        return new AiExecutionResult(
                AiRequestStatus.SUCCESS,
                providerName,
                modelName,
                responseText,
                tokenCount,
                null,
                null);
    }

    public static AiExecutionResult unavailable(
            String providerName,
            String modelName,
            String userMessage,
            String errorSummary) {
        return new AiExecutionResult(
                AiRequestStatus.UNAVAILABLE,
                providerName,
                modelName,
                null,
                null,
                userMessage,
                errorSummary);
    }

    public static AiExecutionResult failed(
            String providerName,
            String modelName,
            String userMessage,
            String errorSummary) {
        return new AiExecutionResult(
                AiRequestStatus.FAILED,
                providerName,
                modelName,
                null,
                null,
                userMessage,
                errorSummary);
    }
}

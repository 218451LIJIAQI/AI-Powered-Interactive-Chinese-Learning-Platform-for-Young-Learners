package com.example.aichinese.service.ai;

import com.example.aichinese.entity.enums.AiRequestStatus;

public record AiExecutionResult(
        AiRequestStatus status,
        String providerName,
        String modelName,
        String responseText,
        Integer tokenCount,
        String userMessage,
        String errorSummary) {

    public static AiExecutionResult success(String providerName, String modelName, String responseText, Integer tokenCount) {
        return new AiExecutionResult(
                AiRequestStatus.SUCCESS,
                providerName,
                modelName,
                responseText,
                tokenCount,
                null,
                null);
    }

    public static AiExecutionResult unavailable(String providerName, String modelName, String userMessage, String errorSummary) {
        return new AiExecutionResult(
                AiRequestStatus.UNAVAILABLE,
                providerName,
                modelName,
                null,
                null,
                userMessage,
                errorSummary);
    }

    public static AiExecutionResult failed(String providerName, String modelName, String userMessage, String errorSummary) {
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

package com.example.aichinese.service.ai;

import java.util.Map;

import com.example.aichinese.entity.enums.AiRequestStatus;

/**
 * Outcome of one AI request as the feature code needs it: the reply text (and, for a JSON feature, the parsed object)
 * on success, otherwise the fixed message the student sees. Provider, model, token count and error summary are
 * recorded in the AI-usage log when the request is made, so they are not carried here.
 */
public record AiExecutionResult(AiRequestStatus status, String responseText, Map<String, Object> json, String userMessage) {

    public static AiExecutionResult success(String responseText, Map<String, Object> json) {
        return new AiExecutionResult(AiRequestStatus.SUCCESS, responseText, json == null ? Map.of() : json, null);
    }

    public static AiExecutionResult unavailable(String userMessage) {
        return new AiExecutionResult(AiRequestStatus.UNAVAILABLE, null, Map.of(), userMessage);
    }

    public static AiExecutionResult failed(String userMessage) {
        return new AiExecutionResult(AiRequestStatus.FAILED, null, Map.of(), userMessage);
    }

    public boolean succeeded() {
        return status == AiRequestStatus.SUCCESS;
    }
}

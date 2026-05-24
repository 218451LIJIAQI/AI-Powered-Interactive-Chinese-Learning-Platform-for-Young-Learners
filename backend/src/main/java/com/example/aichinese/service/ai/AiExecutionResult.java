package com.example.aichinese.service.ai;

import com.example.aichinese.entity.enums.AiRequestStatus;

/**
 * Service-layer result of an end-to-end AI feature execution.
 *
 * <p>Distinct from {@link AiProviderResult} (the raw provider response)
 * because the service layer needs three outcomes — SUCCESS,
 * UNAVAILABLE (provider disabled / mis-configured), and FAILED
 * (provider reachable but returned an error or timed out) — so the
 * caller can decide between "fall back to canned response" (UNAVAILABLE)
 * and "log + return error" (FAILED). Both non-success branches carry
 * a learner-safe {@code userMessage} and a more detailed
 * {@code errorSummary} that goes only into the audit / usage log,
 * never the response body.</p>
 *
 * <p>The static factories {@link #success}, {@link #unavailable}, and
 * {@link #failed} keep the three shapes mutually exclusive — direct
 * use of the canonical constructor should be reserved for tests.</p>
 */
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

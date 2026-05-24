package com.example.aichinese.service.ai;

import java.util.Objects;

import com.example.aichinese.entity.enums.AiRequestStatus;

/**
 * Service-layer result of an end-to-end AI feature execution.
 *
 * <p>Distinct from {@link AiProviderResult} (the raw provider
 * response) because the service layer needs three outcomes —
 * {@link AiRequestStatus#SUCCESS},
 * {@link AiRequestStatus#UNAVAILABLE} (provider disabled or
 * mis-configured), and {@link AiRequestStatus#FAILED} (provider
 * reachable but returned an error or timed out) — so the caller can
 * decide between "fall back to canned response" (UNAVAILABLE) and
 * "log + return error" (FAILED). Both non-success branches carry a
 * learner-safe {@code userMessage} and a more detailed
 * {@code errorSummary} that goes only into the audit / usage log,
 * never the response body.</p>
 *
 * <h2>Construction discipline</h2>
 * <p>The static factories {@link #success}, {@link #unavailable}, and
 * {@link #failed} keep the three shapes mutually exclusive. The
 * compact constructor further enforces the invariant — so a hand-rolled
 * construction in a test (or any future caller) cannot produce a
 * "success-shaped" record carrying a {@code FAILED} status:</p>
 * <ul>
 *   <li>{@code status} is never {@code null}.</li>
 *   <li>{@code SUCCESS} requires a non-blank {@code responseText}
 *       and forbids a {@code userMessage}.</li>
 *   <li>{@code UNAVAILABLE} / {@code FAILED} require a non-blank
 *       {@code userMessage} and forbid {@code responseText} /
 *       {@code tokenCount}.</li>
 * </ul>
 *
 * <h2>Call sites</h2>
 * <p>Use {@code result.status().isSuccess()} (or
 * {@code .isFailure()} / {@code .isUnavailable()}) rather than
 * re-checking {@code == AiRequestStatus.SUCCESS} at every call site —
 * the helpers live on the enum so the record stays a pure value
 * carrier.</p>
 */
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

    /**
     * Builds a {@link AiRequestStatus#SUCCESS} result with the
     * provider's raw response text and (best-effort) usage token count.
     * {@code tokenCount} may be {@code null} when the provider does not
     * report {@code usage.total_tokens}.
     */
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

    /**
     * Builds an {@link AiRequestStatus#UNAVAILABLE} result — provider
     * disabled, mis-configured, or otherwise unreachable before any
     * request was issued. Callers should fall back to a deterministic
     * canned response and surface {@code userMessage} to the learner.
     */
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

    /**
     * Builds an {@link AiRequestStatus#FAILED} result — provider was
     * reached but returned an error / timeout / unusable payload. The
     * {@code errorSummary} is logged but never surfaced to the learner;
     * {@code userMessage} is the safe replacement shown in the UI.
     */
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

package com.example.aichinese.service.ai;

/**
 * Provider-level result returned by every {@link AiProviderClient}.
 *
 * <p>Carries either the generated text on success or a short
 * {@code errorSummary} on failure — the result is always
 * non-{@code null}, so callers can pattern-match on
 * {@link #success()} without null-checking. Token count is best-effort:
 * the provider returns it via the {@code usage.total_tokens} field
 * when available, but callers must treat {@code null} as "unknown
 * usage" rather than zero.</p>
 *
 * <p>Use the static factories {@link #success} / {@link #failure}
 * rather than the canonical constructor so the success and failure
 * shapes stay symmetrical and the {@code success} flag cannot drift
 * out of sync with the populated fields.</p>
 */
public record AiProviderResult(
        boolean success,
        String providerName,
        String modelName,
        String text,
        Integer tokenCount,
        String errorSummary) {

    public static AiProviderResult success(String providerName, String modelName, String text, Integer tokenCount) {
        return new AiProviderResult(true, providerName, modelName, text, tokenCount, null);
    }

    public static AiProviderResult failure(String providerName, String modelName, String errorSummary) {
        return new AiProviderResult(false, providerName, modelName, null, null, errorSummary);
    }
}

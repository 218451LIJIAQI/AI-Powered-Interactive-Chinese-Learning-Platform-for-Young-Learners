package com.example.aichinese.service.ai;

/**
 * Provider-level result returned by every {@link AiProviderClient}.
 *
 * <p>Carries either the generated text on success or a short
 * {@code errorSummary} on failure — the result is always
 * non-{@code null}, so callers can pattern-match on
 * {@link #success()} without null-checking. Token count is
 * best-effort: the provider returns it via the
 * {@code usage.total_tokens} field when available, but callers must
 * treat {@code null} as "unknown usage" rather than zero.</p>
 *
 * <h2>Construction discipline</h2>
 * <p>The compact constructor enforces mutual exclusivity between the
 * success and failure shapes so the {@code success} flag cannot drift
 * out of sync with the populated fields:</p>
 * <ul>
 *   <li>{@code success=true} requires non-blank {@code text} and
 *       forbids {@code errorSummary}.</li>
 *   <li>{@code success=false} requires non-blank {@code errorSummary}
 *       and forbids {@code text} / {@code tokenCount}.</li>
 * </ul>
 * <p>Use the static factories {@link #success(String, String, String, Integer)}
 * / {@link #failure(String, String, String)} to stay on the safe path;
 * direct construction is permitted but subject to the same validation.</p>
 */
public record AiProviderResult(
        boolean success,
        String providerName,
        String modelName,
        String text,
        Integer tokenCount,
        String errorSummary) {

    public AiProviderResult {
        if (success) {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException(
                        "Successful provider result must carry non-blank text.");
            }
            if (errorSummary != null) {
                throw new IllegalArgumentException(
                        "Successful provider result must not carry an error summary.");
            }
        } else {
            if (errorSummary == null || errorSummary.isBlank()) {
                throw new IllegalArgumentException(
                        "Failed provider result must carry a non-blank error summary.");
            }
            if (text != null) {
                throw new IllegalArgumentException(
                        "Failed provider result must not carry response text.");
            }
            if (tokenCount != null) {
                throw new IllegalArgumentException(
                        "Failed provider result must not carry a token count.");
            }
        }
    }

    /**
     * Builds a success result with the provider's raw response text
     * and optional usage token count.
     *
     * @param providerName canonical provider identifier
     * @param modelName    model identifier used for the request
     * @param text         non-blank generated text (trimmed by caller)
     * @param tokenCount   total tokens reported by the provider, or
     *                     {@code null} when the provider omits usage
     */
    public static AiProviderResult success(
            String providerName,
            String modelName,
            String text,
            Integer tokenCount) {
        return new AiProviderResult(true, providerName, modelName, text, tokenCount, null);
    }

    /**
     * Builds a failure result with a diagnostic summary suitable for
     * the audit log (never surfaced to the learner).
     *
     * @param providerName canonical provider identifier
     * @param modelName    model identifier, or {@code null} when the
     *                     failure occurred before model resolution
     * @param errorSummary non-blank description of the failure cause
     */
    public static AiProviderResult failure(
            String providerName,
            String modelName,
            String errorSummary) {
        return new AiProviderResult(false, providerName, modelName, null, null, errorSummary);
    }
}

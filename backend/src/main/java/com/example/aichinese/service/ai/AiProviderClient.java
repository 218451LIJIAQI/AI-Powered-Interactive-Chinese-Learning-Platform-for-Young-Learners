package com.example.aichinese.service.ai;

/**
 * Provider-agnostic abstraction for the chat-style LLM endpoints used
 * by every AI feature on the platform.
 *
 * <p>Two implementations exist:</p>
 * <ul>
 *   <li>{@link OpenAiCompatibleAiProviderClient} — talks to a real
 *       OpenAI-compatible endpoint (DeepSeek, OpenAI, etc.) over
 *       HTTPS.</li>
 *   <li>{@link NoOpAiProviderClient} — always returns failure, used
 *       when {@code app.ai.enable-real-provider=false} so AI features
 *       cleanly degrade to fallback responses without making network
 *       calls.</li>
 * </ul>
 *
 * <p>{@link AiProviderRouter} selects the active implementation at
 * call time based on configuration.</p>
 *
 * <h2>Implementation contract</h2>
 * <ul>
 *   <li><strong>Never throws.</strong> Every error path (missing
 *       credentials, network failure, provider 5xx, parse failure)
 *       must be encoded as an {@link AiProviderResult} with
 *       {@code success=false} so the service layer can fall back to
 *       the deterministic per-feature stub without unwinding the
 *       request.</li>
 *   <li><strong>Non-null in / non-null out.</strong> Callers always
 *       pass a non-null {@link AiProviderPrompt} and implementations
 *       always return a non-null {@link AiProviderResult}.</li>
 *   <li><strong>Thread-safe.</strong> Implementations are Spring
 *       singletons invoked concurrently from request worker threads;
 *       they must hold no per-call mutable state.</li>
 * </ul>
 */
@FunctionalInterface
public interface AiProviderClient {

    /**
     * Sends the supplied prompt to the active LLM provider and returns
     * the raw text response (or a failure result on any error).
     *
     * @param prompt the non-null prompt to send; the implementation
     *               must not mutate its fields
     * @return a non-null result; check {@link AiProviderResult#success()}
     *         before reading the text / token count
     */
    AiProviderResult generateText(AiProviderPrompt prompt);
}

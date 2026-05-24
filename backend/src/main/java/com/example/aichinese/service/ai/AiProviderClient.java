package com.example.aichinese.service.ai;

/**
 * Provider-agnostic abstraction for the chat-style LLM endpoints used
 * by every AI feature on the platform.
 *
 * <p>Two implementations exist:</p>
 * <ul>
 *   <li>{@link OpenAiCompatibleAiProviderClient} — talks to a real
 *       OpenAI-compatible endpoint (DeepSeek, OpenAI, etc.) over HTTPS.</li>
 *   <li>{@link NoOpAiProviderClient} — always returns failure, used
 *       when {@code app.ai.enable-real-provider=false} so AI features
 *       cleanly degrade to fallback responses without making network
 *       calls.</li>
 * </ul>
 *
 * <p>{@link AiProviderRouter} selects the active implementation at
 * call time based on configuration. Implementations must never throw
 * — every error path returns an {@link AiProviderResult} with
 * {@code success=false} so the caller can fall back to the
 * deterministic per-feature stub without unwinding the request.</p>
 */
public interface AiProviderClient {

    /**
     * Sends the supplied prompt to the active LLM provider and returns
     * the raw text response (or a failure result on any error). Never
     * throws.
     */
    AiProviderResult generateText(AiProviderPrompt prompt);
}

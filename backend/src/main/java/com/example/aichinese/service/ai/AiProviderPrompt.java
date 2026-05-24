package com.example.aichinese.service.ai;

import java.util.List;

/**
 * Provider-agnostic shape of a prompt sent to {@link AiProviderClient}.
 *
 * <p>Built by {@link AiPromptBuilder} from the per-feature input and
 * the configured AI properties; consumed by every implementation of
 * {@link AiProviderClient}. Carrying the prompt as a record keeps the
 * provider plumbing free of feature-specific knowledge — a new AI
 * feature only needs to teach {@code AiPromptBuilder} how to build its
 * system instruction; the transport layer is unchanged.</p>
 *
 * @param featureKey         {@code AiFeatureKey.name()} so logs and
 *                           the provider observability dashboard can
 *                           attribute usage to the feature
 * @param systemInstruction  the role / safety / style preamble
 * @param userPrompt         the actual user-supplied request (already
 *                           sanitised by {@code AiInputSanitizer})
 * @param languagePreference normalised language tag — {@code en} /
 *                           {@code ms} / {@code zh}
 * @param maxOutputTokens    cap on generated tokens (drawn from
 *                           {@code AiProperties#getMaxOutputTokens()})
 * @param expectJson         when {@code true}, the provider is asked
 *                           for a strict JSON response (used by
 *                           generators that need a structured output)
 * @param messageHistory     prior conversation turns for chat-style
 *                           features; never {@code null} (use empty
 *                           list when no history)
 */
public record AiProviderPrompt(
        String featureKey,
        String systemInstruction,
        String userPrompt,
        String languagePreference,
        int maxOutputTokens,
        boolean expectJson,
        List<AiMessage> messageHistory) {

    /**
     * One past conversational turn included in the prompt history.
     * {@code role} is one of {@code "user"} / {@code "assistant"}, the
     * naming Spring uses to preserve provider-side compatibility with
     * the OpenAI chat schema.
     */
    public record AiMessage(String role, String content) {
    }
}

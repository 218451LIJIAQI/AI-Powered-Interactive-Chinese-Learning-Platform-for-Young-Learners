package com.example.aichinese.service.ai;

import java.util.List;
import java.util.Objects;

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
 * <h2>Construction discipline</h2>
 * <p>The compact constructor enforces every invariant the downstream
 * provider clients depend on, so a malformed prompt fails fast at
 * construction time rather than translating to a confusing
 * {@code 400 Bad Request} from the upstream LLM:</p>
 * <ul>
 *   <li>{@code featureKey}, {@code systemInstruction}, {@code userPrompt},
 *       and {@code languagePreference} must be non-null and non-blank.</li>
 *   <li>{@code maxOutputTokens} must be strictly positive.</li>
 *   <li>{@code messageHistory} is non-null and defensively wrapped
 *       as an unmodifiable {@link List}, so any caller that bypasses
 *       {@link AiPromptBuilder} cannot mutate the prompt after dispatch.</li>
 * </ul>
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

    public AiProviderPrompt {
        requireNonBlank(featureKey, "featureKey");
        requireNonBlank(systemInstruction, "systemInstruction");
        requireNonBlank(userPrompt, "userPrompt");
        requireNonBlank(languagePreference, "languagePreference");
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be > 0, got " + maxOutputTokens);
        }
        Objects.requireNonNull(messageHistory, "messageHistory");
        // List.copyOf rejects null entries and produces an unmodifiable view,
        // so the prompt stays immutable even if the original list mutates later.
        messageHistory = List.copyOf(messageHistory);
    }

    private static void requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
    }

    /**
     * One past conversational turn included in the prompt history.
     * {@code role} is one of {@code "user"} / {@code "assistant"}, the
     * naming the OpenAI chat schema (and every compatible provider)
     * recognises; {@code content} carries the prior text. Both fields
     * are validated as non-null in the compact constructor and the
     * role is additionally checked for non-blank — the upstream
     * provider would otherwise return an obscure schema error.
     */
    public record AiMessage(String role, String content) {

        public AiMessage {
            Objects.requireNonNull(role, "role");
            if (role.isBlank()) {
                throw new IllegalArgumentException("role must not be blank.");
            }
            Objects.requireNonNull(content, "content");
        }
    }
}

package com.example.aichinese.service.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.entity.enums.AiFeatureKey;

/**
 * Builds {@link AiProviderPrompt} objects with the platform's standard
 * child-safety + Mandarin-learning system instructions.
 *
 * <p>Centralising the system instruction here ensures every AI feature
 * inherits the same age-appropriate tone, language preference rules
 * ({@code en} / {@code ms} / {@code zh}), refusal of professional
 * pronunciation/handwriting claims, and human-review requirement for
 * any official content the LLM generates. Adding a new feature does
 * not require duplicating these guardrails — only a fresh
 * {@code userPrompt} body.</p>
 *
 * <p>The {@code expectJson} flag toggles a single instruction
 * ("Return valid JSON only ...") plus the OpenAI-compatible
 * {@code response_format} hint downstream, so generators that need a
 * structured shape get strict JSON without per-call repetition.</p>
 */
@Component
public class AiPromptBuilder {

    private final AiProperties aiProperties;

    public AiPromptBuilder(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    public AiProviderPrompt buildPrompt(
            AiFeatureKey featureKey,
            String languagePreference,
            String userPrompt,
            boolean expectJson,
            List<AiProviderPrompt.AiMessage> history) {
        return new AiProviderPrompt(
                featureKey.name(),
                buildSystemInstruction(languagePreference, featureKey, expectJson),
                userPrompt,
                normalizeLanguage(languagePreference),
                aiProperties.getMaxOutputTokens(),
                expectJson,
                history == null ? List.of() : history);
    }

    private String buildSystemInstruction(String languagePreference, AiFeatureKey featureKey, boolean expectJson) {
        List<String> instructions = new ArrayList<>();
        instructions.add("You are a friendly Mandarin Chinese learning assistant for children aged 7 to 12.");
        instructions.add("Keep responses short, simple, encouraging, and age-appropriate.");
        instructions.add("Use positive wording and avoid harsh correction.");
        instructions.add("Support beginner Mandarin learners and include Chinese, pinyin, and translation when useful.");
        instructions.add("Avoid adult, unsafe, political, or unrelated content.");
        instructions.add("Do not claim professional pronunciation scoring, handwriting recognition, or stroke-order analysis.");
        instructions.add("Any official learning content generated must still require human review before publication.");
        instructions.add(buildLanguageInstruction(languagePreference));

        if (expectJson) {
            instructions.add("Return valid JSON only with no markdown fences or extra commentary.");
        }

        return String.join(" ", instructions);
    }

    private String buildLanguageInstruction(String languagePreference) {
        return switch (normalizeLanguage(languagePreference)) {
            case "ms" -> "Explain mainly in simple Bahasa Melayu, while keeping Chinese learning content clear.";
            case "zh" -> "Explain mainly in simple Chinese suitable for beginners.";
            default -> "Explain mainly in simple English.";
        };
    }

    private String normalizeLanguage(String languagePreference) {
        if (languagePreference == null || languagePreference.isBlank()) {
            return "en";
        }

        String value = languagePreference.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("ms")) {
            return "ms";
        }
        if (value.startsWith("zh")) {
            return "zh";
        }
        return "en";
    }
}

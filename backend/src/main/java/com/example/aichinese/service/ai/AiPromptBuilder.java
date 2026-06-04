package com.example.aichinese.service.ai;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.entity.enums.AiFeatureKey;

@Component
public class AiPromptBuilder {

    static final String LANG_EN = "en";

    static final String LANG_MS = "ms";

    static final String LANG_ZH = "zh";

    private static final String BASE_INSTRUCTIONS = String.join(" ", List.of(
            "You are a friendly Mandarin Chinese learning assistant for children aged 7 to 12.",
            "Keep responses short, simple, encouraging, and age-appropriate.",
            "Use positive wording and avoid harsh correction.",
            "Support beginner Mandarin learners and include Chinese, pinyin, and translation when useful.",
            "Avoid adult, unsafe, political, or unrelated content.",
            "Do not claim professional pronunciation scoring, handwriting recognition, or stroke-order analysis.",
            "Any official learning content generated must still require human review before publication."));

    private static final String INSTRUCTION_ENGLISH =
            "Explain mainly in simple English.";

    private static final String INSTRUCTION_MALAY =
            "Explain mainly in simple Bahasa Melayu, while keeping Chinese learning content clear.";

    private static final String INSTRUCTION_CHINESE =
            "Explain mainly in simple Chinese suitable for beginners.";

    private static final String INSTRUCTION_JSON =
            "Return valid JSON only with no markdown fences or extra commentary.";

    private final AiProperties aiProperties;

    public AiPromptBuilder(AiProperties aiProperties) {
        this.aiProperties = Objects.requireNonNull(aiProperties, "aiProperties");
    }

    public AiProviderPrompt buildPrompt(
            AiFeatureKey featureKey,
            String languagePreference,
            String userPrompt,
            boolean expectJson,
            List<AiProviderPrompt.AiMessage> history) {
        Objects.requireNonNull(featureKey, "featureKey");
        Objects.requireNonNull(userPrompt, "userPrompt");
        String language = normalizeLanguage(languagePreference);
        return new AiProviderPrompt(
                featureKey.name(),
                buildSystemInstruction(language, expectJson),
                userPrompt,
                language,
                aiProperties.getMaxOutputTokens(),
                expectJson,
                history == null ? List.of() : List.copyOf(history));
    }

    private String buildSystemInstruction(String normalizedLanguage, boolean expectJson) {
        String languageInstruction = switch (normalizedLanguage) {
            case LANG_MS -> INSTRUCTION_MALAY;
            case LANG_ZH -> INSTRUCTION_CHINESE;
            default -> INSTRUCTION_ENGLISH;
        };
        return expectJson
                ? BASE_INSTRUCTIONS + ' ' + languageInstruction + ' ' + INSTRUCTION_JSON
                : BASE_INSTRUCTIONS + ' ' + languageInstruction;
    }

    private static String normalizeLanguage(String languagePreference) {
        if (languagePreference == null) {
            return LANG_EN;
        }
        String value = languagePreference.strip().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return LANG_EN;
        }
        if (value.equals(LANG_MS) || value.startsWith("ms-") || value.startsWith("ms_")) {
            return LANG_MS;
        }
        if (value.equals(LANG_ZH) || value.startsWith("zh-") || value.startsWith("zh_")) {
            return LANG_ZH;
        }
        return LANG_EN;
    }
}

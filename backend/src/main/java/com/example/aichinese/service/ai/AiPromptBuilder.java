package com.example.aichinese.service.ai;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.util.LanguageCodes;
import com.example.aichinese.util.PersonalDataRedactor;

/**
 * Builds the bounded instructions sent to the AI provider.
 *
 * <p>The platform targets young adult Mandarin beginners (18&ndash;25) in Malaysia. Every
 * request carries the same scope rules: beginner level, short answers, Mandarin-learning
 * topics only, a polite refusal for unrelated requests, and no claim to be a teacher or to
 * give official grades. Explanations follow the learner's interface language
 * ({@code en}, {@code ms} or {@code zh-CN}) while Chinese learning content always keeps
 * Simplified Chinese characters and Hanyu Pinyin.</p>
 *
 * <p>Every learner-derived text (the user prompt and the chat history) passes through
 * {@link PersonalDataRedactor} here, so no e-mail address, phone number or NRIC-like
 * number typed by a learner reaches the external provider.</p>
 */
@Component
public class AiPromptBuilder {

    private static final String BASE_INSTRUCTIONS = String.join(" ", List.of(
            "You are MandarinSpark's AI study helper for young adult Mandarin beginners (aged 18 to 25) in Malaysia.",
            "Be concise, clear and respectful; address the learner as an adult.",
            "Keep the Chinese at beginner level (HSK 1-2 vocabulary and simple grammar) unless the learner asks about a specific word.",
            "Keep answers short: no more than about 120 words, or only the requested JSON fields.",
            "Write Chinese in Simplified Chinese characters, add Hanyu Pinyin with tone marks and a short meaning.",
            "Only help with learning Mandarin Chinese (vocabulary, pronunciation, grammar, sentences, reading and everyday communication).",
            "If a request is unrelated to Mandarin learning, unsafe or inappropriate, politely decline in one sentence and suggest a Mandarin-learning alternative.",
            "You are an optional supplementary practice tool: never claim to be a teacher, never give official grades, scores, certificates or formal assessment, and suggest asking the teacher when the learner needs a definitive answer.",
            "Do not claim professional pronunciation scoring, handwriting recognition or stroke-order analysis.",
            "Do not ask for personal information; placeholders such as [EMAIL], [PHONE] or [ID_NUMBER] mean details were removed for privacy.",
            "Generated quizzes and stories are temporary self-practice material, not official course content."));

    private static final String INSTRUCTION_ENGLISH =
            "Write explanations in clear, simple English and keep Chinese characters with Pinyin for the Chinese content.";

    private static final String INSTRUCTION_MALAY =
            "Write explanations in clear, simple Bahasa Melayu and keep Chinese characters with Pinyin for the Chinese content.";

    private static final String INSTRUCTION_CHINESE =
            "Write explanations in simple Simplified Chinese suitable for beginners and add Pinyin for new or difficult words.";

    private static final String INSTRUCTION_JSON =
            "Return valid JSON only with no markdown fences or extra commentary.";

    private static final String INSTRUCTION_IMAGE =
            "The learner attached a photo of their own handwriting. Give limited, encouraging practice feedback only "
                    + "(legibility, proportion and visible stroke issues). It is not a score or an official assessment, "
                    + "and you must say so briefly if you cannot read the image.";

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
        return buildPrompt(featureKey, languagePreference, userPrompt, expectJson, history, null);
    }

    public AiProviderPrompt buildPrompt(
            AiFeatureKey featureKey,
            String languagePreference,
            String userPrompt,
            boolean expectJson,
            List<AiProviderPrompt.AiMessage> history,
            AiProviderPrompt.AiImageInput image) {
        Objects.requireNonNull(featureKey, "featureKey");
        Objects.requireNonNull(userPrompt, "userPrompt");
        String language = LanguageCodes.normalizeOrDefault(languagePreference);
        return new AiProviderPrompt(
                featureKey.name(),
                buildSystemInstruction(language, expectJson, image != null),
                PersonalDataRedactor.redact(userPrompt),
                language,
                aiProperties.getMaxOutputTokens(),
                expectJson,
                redactHistory(history),
                image);
    }

    private static List<AiProviderPrompt.AiMessage> redactHistory(List<AiProviderPrompt.AiMessage> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        return history.stream()
                .map(message -> new AiProviderPrompt.AiMessage(
                        message.role(),
                        PersonalDataRedactor.redact(message.content())))
                .toList();
    }

    private static String buildSystemInstruction(String normalizedLanguage, boolean expectJson, boolean withImage) {
        String languageInstruction = switch (normalizedLanguage) {
            case LanguageCodes.MALAY -> INSTRUCTION_MALAY;
            case LanguageCodes.CHINESE_SIMPLIFIED -> INSTRUCTION_CHINESE;
            default -> INSTRUCTION_ENGLISH;
        };
        StringBuilder instruction = new StringBuilder(BASE_INSTRUCTIONS)
                .append(' ')
                .append(languageInstruction);
        if (withImage) {
            instruction.append(' ').append(INSTRUCTION_IMAGE);
        }
        if (expectJson) {
            instruction.append(' ').append(INSTRUCTION_JSON);
        }
        return instruction.toString();
    }
}

package com.example.aichinese.service.ai;

import java.util.List;

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
 * Simplified Chinese characters and Hanyu Pinyin. Talk with Xiaomei adds her persona; the
 * practice checks add how to score.</p>
 *
 * <p>Every learner-derived text (the user prompt and the conversation history) passes through
 * {@link PersonalDataRedactor} here, so no e-mail address, phone number or NRIC-like
 * number typed by a learner reaches the external provider.</p>
 */
@Component
public class AiPromptBuilder {

    private static final String BASE_INSTRUCTIONS = String.join(" ", List.of(
            "You are MandarinSpark's AI study helper (SparkBot) for young adult Mandarin beginners (aged 18 to 25) in Malaysia.",
            "Be concise, clear and respectful; address the learner as an adult.",
            "Keep the Chinese at beginner level (HSK 1-2 vocabulary and simple grammar) unless the learner asks about a specific word.",
            "Keep answers short: no more than about 120 words, or only the requested JSON fields.",
            "Write Chinese in Simplified Chinese characters, add Hanyu Pinyin with tone marks and a short meaning.",
            "Only help with learning Mandarin Chinese (vocabulary, pronunciation, grammar, sentences, reading and everyday communication).",
            "If a request is unrelated to Mandarin learning, unsafe or inappropriate, politely decline in one sentence and suggest a Mandarin-learning alternative.",
            "You are an optional supplementary practice tool: never claim to be a teacher and never give official grades, certificates or formal assessment; a practice score is given only when a feature asks for one and is for self-practice only.",
            "Suggest asking the teacher when the learner needs a definitive answer.",
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

    private static final String INSTRUCTION_XIAOMEI = String.join(" ", List.of(
            "In this conversation you are Xiaomei (小美), a friendly 21-year-old university student in Kuala Lumpur.",
            "You talk face to face with the learner in natural, spoken Mandarin, warmly and with curiosity, like a real friend.",
            "Keep each reply to one or two short sentences (at most about 25 Chinese characters) with HSK 1-2 words,",
            "react to what the learner said, and usually end with one simple question that keeps the conversation going.",
            "Stay inside the scenario you are given. Never ask for the learner's full name, address, phone number or other",
            "personal details. You are an AI conversation partner and say so if asked."));

    private static final String INSTRUCTION_PRACTICE_CHECK = String.join(" ", List.of(
            "This is a practice check: give a practice score from 0 to 100 and short, specific, encouraging feedback.",
            "Be honest about what you could not judge. The score is for self-practice only and is not an official assessment."));

    private static final String INSTRUCTION_IMAGE =
            "The learner attached a photo of their own handwriting. Judge only what is visible (stroke shape, proportion, "
                    + "balance and missing or extra strokes), and say so briefly if you cannot read the image.";

    private final AiProperties aiProperties;

    public AiPromptBuilder(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    public AiProviderPrompt buildPrompt(
            AiFeatureKey featureKey,
            String languagePreference,
            String userPrompt,
            boolean expectJson,
            List<AiProviderPrompt.AiMessage> history,
            AiProviderPrompt.AiImageInput image) {
        String language = LanguageCodes.normalizeOrDefault(languagePreference);
        return new AiProviderPrompt(
                featureKey.name(),
                buildSystemInstruction(featureKey, language, expectJson, image != null),
                PersonalDataRedactor.redact(userPrompt),
                aiProperties.getMaxOutputTokens(),
                expectJson,
                redactHistory(history),
                image);
    }

    private static List<AiProviderPrompt.AiMessage> redactHistory(List<AiProviderPrompt.AiMessage> history) {
        return history.stream()
                .map(message -> new AiProviderPrompt.AiMessage(
                        message.role(),
                        PersonalDataRedactor.redact(message.content())))
                .toList();
    }

    private static String buildSystemInstruction(
            AiFeatureKey featureKey, String normalizedLanguage, boolean expectJson, boolean withImage) {
        String languageInstruction = switch (normalizedLanguage) {
            case LanguageCodes.MALAY -> INSTRUCTION_MALAY;
            case LanguageCodes.CHINESE_SIMPLIFIED -> INSTRUCTION_CHINESE;
            default -> INSTRUCTION_ENGLISH;
        };
        StringBuilder instruction = new StringBuilder(BASE_INSTRUCTIONS)
                .append(' ')
                .append(languageInstruction);
        if (featureKey == AiFeatureKey.XIAOMEI_CONVERSATION) {
            instruction.append(' ').append(INSTRUCTION_XIAOMEI);
        }
        if (featureKey == AiFeatureKey.PRACTICE_CHECK) {
            instruction.append(' ').append(INSTRUCTION_PRACTICE_CHECK);
        }
        if (withImage) {
            instruction.append(' ').append(INSTRUCTION_IMAGE);
        }
        if (expectJson) {
            instruction.append(' ').append(INSTRUCTION_JSON);
        }
        return instruction.toString();
    }
}

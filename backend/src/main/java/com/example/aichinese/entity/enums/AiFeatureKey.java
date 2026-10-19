package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

public enum AiFeatureKey {

    CHAT_TUTOR,

    VOCABULARY_HELPER,

    SENTENCE_PRACTICE,

    QUIZ_GENERATOR,

    STORY_GENERATOR,

    PRONUNCIATION_FEEDBACK,

    WRITING_FEEDBACK;

    private static final Map<String, AiFeatureKey> LOOKUP =
            EnumLookups.indexByName(AiFeatureKey.class);

    public static Optional<AiFeatureKey> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static AiFeatureKey fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "AI feature key is required.",
                () -> "Invalid AI feature key. Allowed values are CHAT_TUTOR, VOCABULARY_HELPER, "
                        + "SENTENCE_PRACTICE, QUIZ_GENERATOR, STORY_GENERATOR, "
                        + "PRONUNCIATION_FEEDBACK, or WRITING_FEEDBACK.");
    }
}

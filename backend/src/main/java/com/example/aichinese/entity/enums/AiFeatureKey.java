package com.example.aichinese.entity.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public enum AiFeatureKey {

    CHAT_TUTOR,

    VOCABULARY_HELPER,

    SENTENCE_PRACTICE,

    QUIZ_GENERATOR,

    STORY_GENERATOR,

    LEARNING_RECOMMENDATION,

    PRONUNCIATION_FEEDBACK,

    WRITING_FEEDBACK;

    public static final Set<AiFeatureKey> TEXT_GENERATIVE = Collections.unmodifiableSet(EnumSet.of(
            CHAT_TUTOR, VOCABULARY_HELPER, SENTENCE_PRACTICE, QUIZ_GENERATOR,
            STORY_GENERATOR, LEARNING_RECOMMENDATION));

    public static final Set<AiFeatureKey> MEDIA_FEEDBACK = Collections.unmodifiableSet(EnumSet.of(
            PRONUNCIATION_FEEDBACK, WRITING_FEEDBACK));

    static {
        EnumSet<AiFeatureKey> covered = EnumSet.copyOf(TEXT_GENERATIVE);
        covered.addAll(MEDIA_FEEDBACK);
        if (!covered.equals(EnumSet.allOf(AiFeatureKey.class))) {
            EnumSet<AiFeatureKey> missing = EnumSet.complementOf(covered);
            throw new AssertionError(
                    "AiFeatureKey category sets do not cover all constants; uncovered: " + missing);
        }
        EnumSet<AiFeatureKey> overlap = EnumSet.copyOf(TEXT_GENERATIVE);
        overlap.retainAll(MEDIA_FEEDBACK);
        if (!overlap.isEmpty()) {
            throw new AssertionError(
                    "AiFeatureKey TEXT_GENERATIVE and MEDIA_FEEDBACK sets must be disjoint; overlap: "
                            + overlap);
        }
    }

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
                        + "LEARNING_RECOMMENDATION, PRONUNCIATION_FEEDBACK, or WRITING_FEEDBACK.");
    }

    public boolean isTextGenerative() {
        return TEXT_GENERATIVE.contains(this);
    }

    public boolean isMediaFeedback() {
        return MEDIA_FEEDBACK.contains(this);
    }
}

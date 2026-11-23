package com.example.aichinese.entity.enums;

import java.util.Optional;

/** The AI features whose requests are logged and limited; also the feature an AI report is about. */
public enum AiFeatureKey {

    /** Ask SparkBot (chat). */
    CHAT_TUTOR,

    /** Talk with Xiaomei: replies and the end-of-chat summary. */
    XIAOMEI_CONVERSATION,

    QUIZ_GENERATOR,

    STORY_GENERATOR,

    /** Pronunciation and handwriting practice checks. */
    PRACTICE_CHECK,

    /** The AI-assisted quiz draft of the admin lesson editor. */
    QUIZ_DRAFT,

    /** A learning plan SparkBot helps to write. */
    LEARNING_PLAN,

    /**
     * A SparkBot hint in a lesson, a Practice set, the Mistakes review or a class quiz. Hints are built by rules and
     * never call the AI provider: the key only names what a learner reports from the hint card.
     */
    HINT;

    public static Optional<AiFeatureKey> tryFrom(String value) {
        return EnumLookups.tryFrom(AiFeatureKey.class, value);
    }
}

package com.example.aichinese.entity.enums;

import java.util.Optional;

/** The AI features whose requests are logged and limited; also the feature an AI report is about. */
public enum AiFeatureKey {

    /** Ask SparkBot (chat). */
    CHAT_TUTOR,

    /** Talk with Xiaomei: replies, "Help me reply" suggestions and the end-of-chat summary. */
    XIAOMEI_CONVERSATION,

    QUIZ_GENERATOR,

    STORY_GENERATOR,

    /** Pronunciation and handwriting practice checks. */
    PRACTICE_CHECK,

    /** A teacher's AI-assisted quiz or story draft. */
    TEACHER_DRAFT,

    /** Learning plans; the progress module logs these requests. */
    LEARNING_PLAN;

    public static Optional<AiFeatureKey> tryFrom(String value) {
        return EnumLookups.tryFrom(AiFeatureKey.class, value);
    }
}

package com.example.aichinese.entity.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;

public enum QuizQuestionType {

    MULTIPLE_CHOICE,

    TRUE_FALSE,

    FILL_BLANK,

    MATCH_WORD_MEANING,

    MATCH_WORD_IMAGE,

    LISTEN_CHOOSE,

    REARRANGE_SENTENCE,

    CHOOSE_PINYIN,

    CHOOSE_TONE,

    CHARACTER_MEANING;

    private static final EnumSet<QuizQuestionType> OPTION_BASED_TYPES = EnumSet.of(
            MULTIPLE_CHOICE,
            TRUE_FALSE,
            MATCH_WORD_MEANING,
            MATCH_WORD_IMAGE,
            LISTEN_CHOOSE,
            CHOOSE_PINYIN,
            CHOOSE_TONE,
            CHARACTER_MEANING);

    private static final Map<String, QuizQuestionType> LOOKUP =
            EnumLookups.indexByName(QuizQuestionType.class);

    public static Optional<QuizQuestionType> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static QuizQuestionType fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Question type is required.",
                () -> "Invalid question type. Allowed values are MULTIPLE_CHOICE, TRUE_FALSE, "
                        + "FILL_BLANK, MATCH_WORD_MEANING, MATCH_WORD_IMAGE, LISTEN_CHOOSE, "
                        + "REARRANGE_SENTENCE, CHOOSE_PINYIN, CHOOSE_TONE, or CHARACTER_MEANING.");
    }

    public boolean isOptionBased() {
        return OPTION_BASED_TYPES.contains(this);
    }
}

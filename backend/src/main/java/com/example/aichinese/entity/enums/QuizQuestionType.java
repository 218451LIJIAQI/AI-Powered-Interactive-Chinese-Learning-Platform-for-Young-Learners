package com.example.aichinese.entity.enums;

import java.util.Arrays;
import java.util.Set;

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

    private static final Set<QuizQuestionType> OPTION_BASED_TYPES = Set.of(
            MULTIPLE_CHOICE,
            TRUE_FALSE,
            MATCH_WORD_MEANING,
            MATCH_WORD_IMAGE,
            LISTEN_CHOOSE,
            CHOOSE_PINYIN,
            CHOOSE_TONE,
            CHARACTER_MEANING);

    public static QuizQuestionType fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Question type is required.");
        }

        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid question type. Allowed values are MULTIPLE_CHOICE, TRUE_FALSE, FILL_BLANK, MATCH_WORD_MEANING, MATCH_WORD_IMAGE, LISTEN_CHOOSE, REARRANGE_SENTENCE, CHOOSE_PINYIN, CHOOSE_TONE, or CHARACTER_MEANING."));
    }

    public boolean isOptionBased() {
        return OPTION_BASED_TYPES.contains(this);
    }
}

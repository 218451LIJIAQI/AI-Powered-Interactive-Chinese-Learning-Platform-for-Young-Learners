package com.example.aichinese.entity.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
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

    private static final Set<QuizQuestionType> OPTION_BASED_TYPES = Collections.unmodifiableSet(EnumSet.of(
            MULTIPLE_CHOICE,
            TRUE_FALSE,
            MATCH_WORD_MEANING,
            MATCH_WORD_IMAGE,
            LISTEN_CHOOSE,
            CHOOSE_PINYIN,
            CHOOSE_TONE,
            CHARACTER_MEANING));

    private static final Set<QuizQuestionType> FREE_TEXT_TYPES =
            Collections.unmodifiableSet(EnumSet.of(FILL_BLANK));

    private static final Set<QuizQuestionType> STRUCTURED_PAYLOAD_TYPES =
            Collections.unmodifiableSet(EnumSet.of(REARRANGE_SENTENCE));

    static {
        EnumSet<QuizQuestionType> covered = EnumSet.copyOf(OPTION_BASED_TYPES);
        covered.addAll(FREE_TEXT_TYPES);
        covered.addAll(STRUCTURED_PAYLOAD_TYPES);
        if (!covered.equals(EnumSet.allOf(QuizQuestionType.class))) {
            EnumSet<QuizQuestionType> missing = EnumSet.complementOf(covered);
            throw new AssertionError(
                    "QuizQuestionType category sets do not cover all constants; uncovered: " + missing);
        }
        requireDisjoint("OPTION_BASED_TYPES", OPTION_BASED_TYPES, "FREE_TEXT_TYPES", FREE_TEXT_TYPES);
        requireDisjoint("OPTION_BASED_TYPES", OPTION_BASED_TYPES, "STRUCTURED_PAYLOAD_TYPES", STRUCTURED_PAYLOAD_TYPES);
        requireDisjoint("FREE_TEXT_TYPES", FREE_TEXT_TYPES, "STRUCTURED_PAYLOAD_TYPES", STRUCTURED_PAYLOAD_TYPES);
    }

    private static void requireDisjoint(
            String leftName, Set<QuizQuestionType> left,
            String rightName, Set<QuizQuestionType> right) {
        EnumSet<QuizQuestionType> overlap = EnumSet.copyOf(left);
        overlap.retainAll(right);
        if (!overlap.isEmpty()) {
            throw new AssertionError(
                    "QuizQuestionType " + leftName + " and " + rightName
                            + " must be disjoint; overlapping: " + overlap);
        }
    }

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

    public boolean isFreeText() {
        return FREE_TEXT_TYPES.contains(this);
    }

    public boolean hasStructuredPayload() {
        return STRUCTURED_PAYLOAD_TYPES.contains(this);
    }
}

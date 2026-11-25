package com.example.aichinese.entity.enums;

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

    public static final String INVALID_TYPE_MESSAGE =
            "Invalid question type. Allowed values are MULTIPLE_CHOICE, TRUE_FALSE, "
                    + "FILL_BLANK, MATCH_WORD_MEANING, MATCH_WORD_IMAGE, LISTEN_CHOOSE, "
                    + "REARRANGE_SENTENCE, CHOOSE_PINYIN, CHOOSE_TONE, or CHARACTER_MEANING.";

    public boolean isOptionBased() {
        return this != FILL_BLANK && this != REARRANGE_SENTENCE;
    }

    /** A listen-and-choose question needs a recording to play. */
    public boolean needsAudio() {
        return this == LISTEN_CHOOSE;
    }

    /** A word-image matching question needs a picture to show. */
    public boolean needsImage() {
        return this == MATCH_WORD_IMAGE;
    }
}

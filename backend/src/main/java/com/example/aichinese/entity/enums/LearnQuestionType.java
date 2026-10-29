package com.example.aichinese.entity.enums;

/**
 * How a Learn question asks about its word or sentence. The choice types offer options; the build types offer word
 * tiles the student puts in order. The interface writes the instruction ("Select the correct meaning", "Tap what you
 * hear", ...) from the type, so it is shown in the student's language.
 */
public enum LearnQuestionType {

    /** The hanzi (with pinyin) is shown; choose its meaning. */
    MEANING,

    /** The hanzi is shown; choose its pinyin. */
    PINYIN,

    /** The meaning is shown; choose the hanzi. */
    HANZI,

    /** The word is spoken; choose the hanzi. */
    LISTEN_CHOOSE,

    /** The sentence is spoken; build it from tiles. */
    LISTEN_BUILD,

    /** The meaning is shown; build the sentence from tiles. */
    TRANSLATE_BUILD;

    public boolean buildsFromTiles() {
        return this == LISTEN_BUILD || this == TRANSLATE_BUILD;
    }
}

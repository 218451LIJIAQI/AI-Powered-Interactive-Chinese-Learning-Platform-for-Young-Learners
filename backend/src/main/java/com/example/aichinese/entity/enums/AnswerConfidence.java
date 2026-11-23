package com.example.aichinese.entity.enums;

/**
 * How sure a student was when submitting a Practice answer. A wrong answer given as {@code SURE} goes to the top of
 * the Mistakes list; a right answer given as {@code GUESS} comes back once more in the same run. An answer given after
 * a hint always counts as a guess.
 */
public enum AnswerConfidence {

    GUESS,

    SURE
}

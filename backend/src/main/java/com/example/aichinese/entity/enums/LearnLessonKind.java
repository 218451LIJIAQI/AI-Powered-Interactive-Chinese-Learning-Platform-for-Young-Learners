package com.example.aichinese.entity.enums;

/**
 * The kinds of stop on the Learn path. A bonus chest is optional (the next lesson does not wait for it), a review
 * mixes the whole unit, and a checkpoint closes a level: passing it opens the next level.
 */
public enum LearnLessonKind {

    LESSON,

    BONUS,

    REVIEW,

    CHECKPOINT
}

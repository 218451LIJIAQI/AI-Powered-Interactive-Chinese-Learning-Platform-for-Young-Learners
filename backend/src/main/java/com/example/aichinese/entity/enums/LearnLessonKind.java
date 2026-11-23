package com.example.aichinese.entity.enums;

import java.util.List;

/**
 * The kinds of stop on the Learn path. A bonus chest is optional (the next lesson does not wait for it), a review
 * mixes the whole unit, and a checkpoint closes a level: passing it opens the next level.
 */
public enum LearnLessonKind {

    LESSON,

    BONUS,

    REVIEW,

    CHECKPOINT;

    public static final List<LearnLessonKind> COUNTED_AS_LESSONS = List.of(LESSON, REVIEW);

    /**
     * A lesson or the unit review: what every lesson count counts, for students ("Lesson 3 of 7", the lesson badges,
     * the plan) and in the admin editor alike. A bonus chest is an optional extra, a checkpoint the gate of a level.
     */
    public boolean countsAsLesson() {
        return COUNTED_AS_LESSONS.contains(this);
    }
}

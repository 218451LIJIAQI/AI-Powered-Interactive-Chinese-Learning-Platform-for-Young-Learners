package com.example.aichinese.entity.enums;

/**
 * Derived per-lesson mastery indicator (SRS FRPROG02); not persisted. MASTERED = lesson completed and the
 * average of its best quiz scores at least 80 percent (see LessonMasteryCalculator); a lesson without
 * quizzes can be completed but not mastered.
 */
public enum LessonMastery {

    NOT_STARTED,

    IN_PROGRESS,

    COMPLETED,

    MASTERED;
}

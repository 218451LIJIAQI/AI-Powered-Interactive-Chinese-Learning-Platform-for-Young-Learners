package com.example.aichinese.dto.learn;

import com.example.aichinese.entity.enums.LearnLessonKind;

/**
 * The result of a played stop. A checkpoint below its pass mark is not passed: nothing is recorded or unlocked.
 * {@code nextLesson} is the stop to do next (newly unlocked or still open), null at the end of the path.
 */
public record LessonCompleteResponse(
        LearnLessonKind kind,
        boolean passed,
        Integer passPercent,
        int accuracy,
        NextStop nextLesson,
        XpResult xp) {

    public record NextStop(String title, boolean unlockedNow) {
    }
}

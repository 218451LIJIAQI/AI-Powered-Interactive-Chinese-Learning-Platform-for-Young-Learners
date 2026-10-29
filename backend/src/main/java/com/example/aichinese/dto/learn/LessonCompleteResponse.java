package com.example.aichinese.dto.learn;

import com.example.aichinese.entity.enums.LearnLessonKind;

/**
 * The result of a played stop. A checkpoint below its pass mark is not passed: nothing is recorded or unlocked.
 * {@code nextLesson} is the stop to do next (newly unlocked or still open), null at the end of the path.
 */
public record LessonCompleteResponse(
        Long lessonId,
        String title,
        LearnLessonKind kind,
        int unitNumber,
        int position,
        boolean passed,
        Integer passPercent,
        int correctCount,
        int questionCount,
        int accuracy,
        Integer durationSeconds,
        NextStop nextLesson,
        XpResult xp) {

    public record NextStop(Long id, String title, boolean unlockedNow) {
    }
}

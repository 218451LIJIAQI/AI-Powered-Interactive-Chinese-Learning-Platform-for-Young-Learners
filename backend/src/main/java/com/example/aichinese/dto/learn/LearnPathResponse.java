package com.example.aichinese.dto.learn;

import java.util.List;

import com.example.aichinese.entity.enums.LearnLessonKind;
import com.example.aichinese.service.learn.LearnPathModel.StopState;
import com.example.aichinese.service.learn.LearnPathModel.UnitState;

/**
 * The student's Learn path: levels with their units and stops (done / open / locked), each level's checkpoint, the
 * next stop to do, and how many Learn mistakes wait for review. Draft units are not part of it.
 */
public record LearnPathResponse(
        int totalLessons,
        int completedLessons,
        List<Level> levels,
        NextLesson nextLesson,
        long learnMistakesWaiting) {

    public LearnPathResponse {
        levels = levels == null ? List.of() : List.copyOf(levels);
    }

    /** Path level 1 = Beginner 1 (HSK 1), 2 = Beginner 2 (HSK 2). */
    public record Level(int level, int hskLevel, int lessonCount, boolean locked, List<Unit> units, Checkpoint checkpoint) {

        public Level {
            units = units == null ? List.of() : List.copyOf(units);
        }
    }

    /** {@code unlockAfterUnit} is the unit number to finish first while this unit is locked. */
    public record Unit(
            Long id,
            int number,
            String title,
            String titleZh,
            String iconKey,
            int hskLevel,
            UnitState state,
            int lessonCount,
            int completedCount,
            Integer unlockAfterUnit,
            List<Stop> lessons) {

        public Unit {
            lessons = lessons == null ? List.of() : List.copyOf(lessons);
        }
    }

    /** {@code unlockAfter} is the title of the stop to finish first while this one is locked. */
    public record Stop(
            Long id,
            int position,
            String title,
            LearnLessonKind kind,
            int minutes,
            int xpReward,
            StopState state,
            boolean current,
            String unlockAfter) {
    }

    public record Checkpoint(
            Long id,
            String title,
            int questionCount,
            int xpReward,
            int passPercent,
            StopState state,
            String unlockAfter) {
    }

    public record NextLesson(
            Long id,
            String title,
            LearnLessonKind kind,
            Long unitId,
            int unitNumber,
            String unitTitle,
            String unitTitleZh,
            int position,
            int unitLessonCount,
            int minutes,
            int xpReward) {
    }
}

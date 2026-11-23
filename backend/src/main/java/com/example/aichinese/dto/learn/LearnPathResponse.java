package com.example.aichinese.dto.learn;

import java.util.List;

import com.example.aichinese.entity.enums.LearnLessonKind;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The student's Learn path: levels with their units and stops (done / open / locked), each level's checkpoint and the
 * next lesson to do. Draft units are not part of it. Every count is of lessons (a unit's lessons and its review): a
 * unit's {@code lessons} list also holds its optional bonus chests, which are neither counted nor numbered, and a
 * stop's {@code position} is its lesson number (a bonus chest carries the number of the lesson before it).
 */
public record LearnPathResponse(
        int completedLessons,
        List<Level> levels,
        NextLesson nextLesson) {

    /**
     * Path level 1 = Beginner 1 (HSK 1), 2 = Beginner 2 (HSK 2). {@code checkpoint} is the gate of the level: it opens
     * once every lesson of the level is done and, passed, opens the next level.
     */
    @Schema(name = "LearnPathLevel")
    public record Level(int level, int hskLevel, boolean locked, List<Unit> units, Checkpoint checkpoint) {
    }

    /**
     * {@code state} is DONE once every lesson of the unit is (its bonus chests and the level checkpoint are not
     * lessons). {@code unlockAfterUnit} is the unit number to finish first while this unit is locked (null for the first
     * unit, and for the first unit of a level that waits for the checkpoint before it).
     */
    @Schema(name = "LearnPathUnit")
    public record Unit(
            Long id,
            int number,
            String title,
            String titleZh,
            String iconKey,
            UnitState state,
            int lessonCount,
            int completedCount,
            Integer unlockAfterUnit,
            List<Stop> lessons) {
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
            StopState state) {
    }

    @Schema(name = "LearnPathNextLesson")
    public record NextLesson(
            Long id,
            String title,
            LearnLessonKind kind,
            Long unitId,
            int unitNumber,
            String unitTitle,
            String unitTitleZh,
            int minutes,
            int xpReward) {
    }
}

package com.example.aichinese.dto.learn;

import java.util.List;

import com.example.aichinese.entity.enums.LearnLessonKind;
import com.example.aichinese.entity.enums.PublishStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The admin Learning path page: units grouped by level. A unit's lesson count, here as on the student path, counts the
 * lessons and reviews only: a bonus chest and a level checkpoint are not lessons.
 */
public record AdminLearningPathResponse(
        int unitCount,
        List<Level> levels) {

    @Schema(name = "AdminLearningPathLevel")
    public record Level(int level, int hskLevel, List<UnitRow> units) {
    }

    /**
     * {@code lessonCount}: the unit's lessons and reviews, without its bonus chests and level checkpoint.
     * {@code bonusCount}: its bonus chests, so a page can name them beside the lessons. {@code studentsStarted}:
     * students who finished at least one stop of the unit.
     */
    public record UnitRow(
            Long id,
            int number,
            String title,
            String titleZh,
            int hskLevel,
            int lessonCount,
            int bonusCount,
            long studentsStarted,
            PublishStatus status) {
    }

    /** A unit in the drawer: its stops in order (a level checkpoint is listed last). */
    public record UnitDetail(UnitRow unit, List<StopRow> lessons) {
    }

    public record StopRow(Long id, String title, LearnLessonKind kind, int minutes, int xpReward) {
    }
}

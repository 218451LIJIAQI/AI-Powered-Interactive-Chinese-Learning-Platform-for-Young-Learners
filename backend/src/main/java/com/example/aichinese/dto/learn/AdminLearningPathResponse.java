package com.example.aichinese.dto.learn;

import java.util.List;

import com.example.aichinese.entity.enums.LearnLessonKind;
import com.example.aichinese.entity.enums.PublishStatus;

/** The admin Learning path page: units grouped by level. */
public record AdminLearningPathResponse(
        int unitCount,
        int lessonCount,
        List<Level> levels) {

    public AdminLearningPathResponse {
        levels = levels == null ? List.of() : List.copyOf(levels);
    }

    public record Level(int level, int hskLevel, int lessonCount, List<UnitRow> units) {

        public Level {
            units = units == null ? List.of() : List.copyOf(units);
        }
    }

    /** {@code studentsStarted}: students who finished at least one stop of the unit. */
    public record UnitRow(
            Long id,
            int number,
            String title,
            String titleZh,
            int hskLevel,
            int lessonCount,
            long studentsStarted,
            PublishStatus status) {
    }

    /** A unit in the drawer: its stops in order (a level checkpoint is listed last). */
    public record UnitDetail(UnitRow unit, List<StopRow> lessons) {

        public UnitDetail {
            lessons = lessons == null ? List.of() : List.copyOf(lessons);
        }
    }

    public record StopRow(
            Long id,
            int position,
            String title,
            LearnLessonKind kind,
            int minutes,
            int xpReward,
            int questionCount) {
    }
}

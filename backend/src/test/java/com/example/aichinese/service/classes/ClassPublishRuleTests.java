package com.example.aichinese.service.classes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.enums.ContentStatus;

/**
 * The shape a class needs to go live (Library · UI rules, Classes): exactly 2 units holding published lessons, 4 to 6
 * published lessons in all. Draft and archived lessons, and units without a published lesson, do not count.
 */
class ClassPublishRuleTests {

    /** {@code perUnit}: the published lessons of each unit, e.g. "2;2" for two units of two. */
    @ParameterizedTest
    @CsvSource({
            "2;2, true",
            "3;3, true",
            "1;3, true",
            "5;1, true",
            "2;1, false",
            "4;3, false",
            "4, false",
            "6, false",
            "2;2;1, false",
            "2;2;0, true",
    })
    void twoUnitsHoldingFourToSixPublishedLessons(String perUnit, boolean canPublish) {
        List<Lesson> lessons = new ArrayList<>();
        long unitId = 1;
        for (String count : perUnit.split(";")) {
            LearningModule unit = unit(unitId++);
            for (int index = 0; index < Integer.parseInt(count); index++) {
                lessons.add(lesson(unit, ContentStatus.PUBLISHED));
            }
            // Drafts and archived lessons are not published lessons.
            lessons.add(lesson(unit, ContentStatus.DRAFT));
            lessons.add(lesson(unit, ContentStatus.ARCHIVED));
        }

        assertThat(ClassAuthoringService.canPublish(classWith(ContentStatus.DRAFT), lessons)).isEqualTo(canPublish);
    }

    @Test
    void anArchivedClassIsNeverPublished() {
        List<Lesson> lessons = new ArrayList<>();
        for (long unitId = 1; unitId <= 2; unitId++) {
            LearningModule unit = unit(unitId);
            lessons.add(lesson(unit, ContentStatus.PUBLISHED));
            lessons.add(lesson(unit, ContentStatus.PUBLISHED));
        }

        assertThat(ClassAuthoringService.canPublish(classWith(ContentStatus.ARCHIVED), lessons)).isFalse();
        assertThat(ClassAuthoringService.canPublish(classWith(ContentStatus.DRAFT), lessons)).isTrue();
    }

    private static CoursePackage classWith(ContentStatus status) {
        CoursePackage coursePackage = new CoursePackage();
        coursePackage.setContentStatus(status);
        return coursePackage;
    }

    private static LearningModule unit(long id) {
        LearningModule unit = new LearningModule();
        unit.setId(id);
        return unit;
    }

    private static Lesson lesson(LearningModule unit, ContentStatus status) {
        Lesson lesson = new Lesson();
        lesson.setModule(unit);
        lesson.setContentStatus(status);
        return lesson;
    }
}

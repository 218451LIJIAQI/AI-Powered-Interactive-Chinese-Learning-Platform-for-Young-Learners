package com.example.aichinese.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.aichinese.entity.enums.LessonMastery;

/**
 * Unit rules for the FRPROG02 mastery indicator.
 */
class LessonMasteryTests {

    @Test
    void masteryFollowsProgressAndBestQuizPercentage() {
        assertThat(LessonMasteryCalculator.mastery(false, false, null)).isEqualTo(LessonMastery.NOT_STARTED);
        assertThat(LessonMasteryCalculator.mastery(true, false, null)).isEqualTo(LessonMastery.IN_PROGRESS);
        assertThat(LessonMasteryCalculator.mastery(false, false, 40)).isEqualTo(LessonMastery.IN_PROGRESS);
        assertThat(LessonMasteryCalculator.mastery(true, true, null)).isEqualTo(LessonMastery.COMPLETED);
        assertThat(LessonMasteryCalculator.mastery(true, true, 79)).isEqualTo(LessonMastery.COMPLETED);
        assertThat(LessonMasteryCalculator.mastery(true, true, 80)).isEqualTo(LessonMastery.MASTERED);
    }
}

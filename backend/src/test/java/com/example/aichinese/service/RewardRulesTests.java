package com.example.aichinese.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

/** FR-PROG-06/07/08: level calculation, streak update and the badge catalogue rules. */
class RewardRulesTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 18);

    @Test
    void levelIsTotalPointsDividedByOneHundredPlusOne() {
        assertThat(RewardRules.levelFor(0)).isEqualTo(1);
        assertThat(RewardRules.levelFor(99)).isEqualTo(1);
        assertThat(RewardRules.levelFor(100)).isEqualTo(2);
        assertThat(RewardRules.levelFor(255)).isEqualTo(3);
        assertThat(RewardRules.levelFor(-20)).as("never below level 1").isEqualTo(1);
    }

    @Test
    void progressToNextLevelIsThePointsInsideTheCurrentLevel() {
        assertThat(RewardRules.progressToNextLevelPercent(0)).isZero();
        assertThat(RewardRules.progressToNextLevelPercent(55)).isEqualTo(55);
        assertThat(RewardRules.progressToNextLevelPercent(230)).isEqualTo(30);
    }

    @Test
    void streakStartsAtOneAndGrowsOnConsecutiveDays() {
        assertThat(RewardRules.nextStreak(null, 0, TODAY)).isEqualTo(1);
        assertThat(RewardRules.nextStreak(TODAY.minusDays(1), 1, TODAY)).isEqualTo(2);
        assertThat(RewardRules.nextStreak(TODAY.minusDays(1), 2, TODAY)).isEqualTo(RewardRules.STREAK_EXPLORER_DAYS);
    }

    @Test
    void streakIsUnchangedOnTheSameDayAndResetsAfterAGap() {
        assertThat(RewardRules.nextStreak(TODAY, 4, TODAY)).isEqualTo(4);
        assertThat(RewardRules.nextStreak(TODAY.minusDays(2), 5, TODAY)).isEqualTo(1);
        assertThat(RewardRules.nextStreak(TODAY.minusDays(30), 9, TODAY)).isEqualTo(1);
    }

    @Test
    void badgeCatalogueHasUniqueNamesIconKeysAndRuleDescriptions() {
        assertThat(Arrays.stream(RewardBadge.values()).map(RewardBadge::badgeName))
                .containsExactly("First Steps", "Quiz Star", "Lesson Finisher", "Streak Explorer", "Point Starter")
                .doesNotHaveDuplicates();
        assertThat(Arrays.stream(RewardBadge.values()).map(RewardBadge::iconKey)).doesNotHaveDuplicates();
        assertThat(RewardBadge.QUIZ_STAR.description()).contains("80%");
        assertThat(RewardBadge.STREAK_EXPLORER.description()).contains(String.valueOf(RewardRules.STREAK_EXPLORER_DAYS));
        assertThat(RewardBadge.POINT_STARTER.description()).contains(String.valueOf(RewardRules.POINT_STARTER_THRESHOLD));
        assertThat(Arrays.stream(RewardBadge.values()).map(RewardBadge::description))
                .allSatisfy(description -> assertThat(description).isNotBlank().hasSizeLessThanOrEqualTo(255));
    }
}

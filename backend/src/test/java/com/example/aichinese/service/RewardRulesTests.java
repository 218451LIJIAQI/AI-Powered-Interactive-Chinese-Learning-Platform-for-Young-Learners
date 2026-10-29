package com.example.aichinese.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/** XP levels, streak rules and the badge catalogue. */
class RewardRulesTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 18);

    @Test
    void levelIsOnePlusEveryHundredXp() {
        assertThat(RewardRules.levelFor(0)).isEqualTo(1);
        assertThat(RewardRules.levelFor(99)).isEqualTo(1);
        assertThat(RewardRules.levelFor(100)).isEqualTo(2);
        assertThat(RewardRules.levelFor(255)).isEqualTo(3);
        assertThat(RewardRules.levelFor(-20)).as("never below level 1").isEqualTo(1);
    }

    @Test
    void progressToNextLevelIsTheXpInsideTheLevel() {
        assertThat(RewardRules.progressToNextLevelPercent(0)).isZero();
        assertThat(RewardRules.progressToNextLevelPercent(55)).isEqualTo(55);
        assertThat(RewardRules.progressToNextLevelPercent(230)).isEqualTo(30);
    }

    @Test
    void reachingTheGoalOnConsecutiveDaysExtendsTheStreak() {
        assertThat(RewardRules.nextStreak(null, 0, TODAY)).isEqualTo(1);
        assertThat(RewardRules.nextStreak(TODAY.minusDays(1), 1, TODAY)).isEqualTo(2);
        assertThat(RewardRules.nextStreak(TODAY.minusDays(1), 2, TODAY)).isEqualTo(RewardRules.STREAK_EXPLORER_DAYS);
    }

    @Test
    void sameDayKeepsAndAGapRestartsTheStreak() {
        assertThat(RewardRules.nextStreak(TODAY, 4, TODAY)).isEqualTo(4);
        assertThat(RewardRules.nextStreak(TODAY.minusDays(2), 5, TODAY)).isEqualTo(1);
        assertThat(RewardRules.nextStreak(TODAY.minusDays(30), 9, TODAY)).isEqualTo(1);
    }

    @Test
    void theCurrentStreakHoldsUntilADayIsMissed() {
        assertThat(RewardRules.currentStreak(TODAY, 4, TODAY)).isEqualTo(4);
        assertThat(RewardRules.currentStreak(TODAY.minusDays(1), 7, TODAY))
                .as("today can still keep yesterday's streak").isEqualTo(7);
        assertThat(RewardRules.currentStreak(TODAY.minusDays(2), 7, TODAY)).isZero();
        assertThat(RewardRules.currentStreak(null, 0, TODAY)).isZero();
    }

    @Test
    void dailyGoalIsTenTwentyOrThirty() {
        assertThat(RewardRules.isDailyGoalOption(10)).isTrue();
        assertThat(RewardRules.isDailyGoalOption(30)).isTrue();
        assertThat(RewardRules.isDailyGoalOption(25)).isFalse();
        assertThat(RewardRules.isDailyGoalOption(null)).isFalse();
    }

    @Test
    void longestRunCountsConsecutiveRightAnswers() {
        assertThat(RewardRules.longestRun(List.of(true, true, false, true, true, true))).isEqualTo(3);
        assertThat(RewardRules.longestRun(List.of(false, false))).isZero();
        assertThat(RewardRules.accuracyPercent(9, 10)).isEqualTo(90);
        assertThat(RewardRules.accuracyPercent(0, 0)).isZero();
    }

    @Test
    void badgeCatalogueHasEightDistinctBadgesThatStateTheirRule() {
        assertThat(RewardBadge.values()).hasSize(8);
        assertThat(Arrays.stream(RewardBadge.values()).map(RewardBadge::badgeName)).doesNotHaveDuplicates();
        assertThat(Arrays.stream(RewardBadge.values()).map(RewardBadge::iconKey)).doesNotHaveDuplicates();
        assertThat(RewardBadge.QUIZ_STAR.description()).contains(RewardRules.QUIZ_STAR_PERCENT + "%");
        assertThat(RewardBadge.STREAK_EXPLORER.description()).contains(String.valueOf(RewardRules.STREAK_EXPLORER_DAYS));
        assertThat(RewardBadge.TEN_DAY_STREAK.description()).contains(String.valueOf(RewardRules.TEN_DAY_STREAK_DAYS));
        assertThat(RewardBadge.XP_STARTER.description()).contains(String.valueOf(RewardRules.XP_STARTER_THRESHOLD));
        assertThat(RewardBadge.XP_300.description()).contains(String.valueOf(RewardRules.XP_300_THRESHOLD));
        assertThat(RewardBadge.LESSON_FINISHER.description()).contains(String.valueOf(RewardRules.LESSON_FINISHER_COUNT));
    }
}

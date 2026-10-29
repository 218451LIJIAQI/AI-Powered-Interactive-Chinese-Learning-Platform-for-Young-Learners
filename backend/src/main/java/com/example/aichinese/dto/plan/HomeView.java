package com.example.aichinese.dto.plan;

import java.util.List;

import com.example.aichinese.dto.classes.LiveSessionViews;
import com.example.aichinese.dto.learn.TodayResponse;
import com.example.aichinese.entity.enums.LearnLessonKind;
import com.example.aichinese.entity.enums.PlanSetupState;
import com.example.aichinese.service.learn.LearnPathModel.StopState;

/**
 * Everything the student Home shows in one request: the greeting, today's XP and streak week, the e-mail
 * confirmation state, the next lesson (in the plan, or the next open Learn lesson without one), today's live session
 * of a joined class, and the plan at a glance. {@code firstTime}: the student has not earned any XP yet.
 */
public record HomeView(
        String firstName,
        TodayResponse today,
        boolean firstTime,
        EmailConfirmationView email,
        PlanSetupState setupState,
        NextLesson next,
        LiveSessionViews.SessionBrief liveToday,
        PlanViews.Summary plan) {

    /**
     * The big "Next in your plan" tile: the lesson, where it is in its unit, a key phrase from it ({@code character}
     * is its first hanzi for the 田字格 tile) and the unit's stops for the mini path.
     */
    public record NextLesson(
            Long lessonId,
            String title,
            LearnLessonKind kind,
            int unitNumber,
            String unitTitle,
            String unitTitleZh,
            int position,
            int unitLessonCount,
            int completedInUnit,
            int minutes,
            int xpReward,
            String character,
            Phrase phrase,
            List<Stop> stops,
            boolean quickReview,
            int unitMinutesLeft) {

        public NextLesson {
            stops = stops == null ? List.of() : List.copyOf(stops);
        }
    }

    public record Phrase(String hanzi, String meaning) {
    }

    public record Stop(Long id, int position, StopState state, boolean current) {
    }
}

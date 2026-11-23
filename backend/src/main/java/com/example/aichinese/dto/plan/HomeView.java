package com.example.aichinese.dto.plan;

import static com.example.aichinese.dto.DtoNormalization.redactValue;

import java.util.List;

import com.example.aichinese.dto.account.EmailConfirmationView;
import com.example.aichinese.dto.learn.StopState;
import com.example.aichinese.dto.learn.TodayResponse;
import com.example.aichinese.entity.enums.LearnLessonKind;
import com.example.aichinese.entity.enums.PlanSetupState;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Everything the student Home shows in one request: the greeting, today's XP and streak week, the e-mail
 * confirmation state, the next lesson (in the plan, or the next open Learn lesson without one) and the plan at a
 * glance. {@code firstTime}: the student has not earned any XP or finished a
 * Learn lesson yet.
 */
public record HomeView(
        String firstName,
        TodayResponse today,
        boolean firstTime,
        EmailConfirmationView email,
        PlanSetupState setupState,
        NextLesson next,
        PlanViews.Summary plan) {

    @Override
    public String toString() {
        return "HomeView[firstName=" + redactValue(firstName)
                + ", today=" + today
                + ", firstTime=" + firstTime
                + ", email=" + email
                + ", setupState=" + setupState
                + ", next=" + next
                + ", plan=" + plan + "]";
    }

    /**
     * The big "Next in your plan" tile: the lesson, where it is in its unit, a key phrase from it ({@code character}
     * is its first hanzi for the 田字格 tile) and the unit's stops for the mini path. {@code inPlan}: its unit is one of
     * the plan's Learn units (false without a plan, and once the path goes on past the plan's units). {@code level} is
     * the path level of its unit.
     *
     * <p>A level checkpoint is the gate of its level, so it is shown with the level: {@code level} is the level it
     * closes and {@code stops} are that level's units ({@code position} = unit number, all done); the unit fields name
     * the level's last unit, which it follows. {@code inPlan} is true while the plan has a unit behind it to do.
     */
    @Schema(name = "HomeNextLesson")
    public record NextLesson(
            Long lessonId,
            String title,
            LearnLessonKind kind,
            int level,
            int unitNumber,
            String unitTitle,
            String unitTitleZh,
            int minutes,
            int xpReward,
            String character,
            Phrase phrase,
            List<Stop> stops,
            boolean quickReview,
            int unitMinutesLeft,
            boolean inPlan) {
    }

    @Schema(name = "HomeLessonPhrase")
    public record Phrase(String hanzi, String meaning) {
    }

    /** A step of the mini path: a lesson of the unit, or for a checkpoint a unit of its level. */
    @Schema(name = "HomeLessonStop")
    public record Stop(Long id, int position, StopState state, boolean current) {
    }
}

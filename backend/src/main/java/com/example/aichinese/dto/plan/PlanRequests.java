package com.example.aichinese.dto.plan;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.example.aichinese.dto.learn.LevelCheckNextRequest.Answer;
import com.example.aichinese.entity.enums.MandarinLevel;
import com.example.aichinese.entity.enums.PlanFocus;
import com.example.aichinese.entity.enums.PlanGoal;
import com.example.aichinese.entity.enums.PlanPace;
import com.example.aichinese.entity.enums.PlanReason;
import com.example.aichinese.entity.enums.PlanSelfLevel;

/** Requests of the learning plan: the setup, a new goal, and a retaken level check. */
public final class PlanRequests {

    private PlanRequests() {
    }

    /** The goal as the setup (or "Update my goal") collects it. */
    public record GoalInput(
            @NotNull(message = "Pick your main reason.")
            PlanReason reason,

            @Size(max = 120, message = "Keep your reason to 120 characters.")
            String reasonText,

            @Size(max = 16, message = "Pick fewer goals.")
            List<@NotNull PlanGoal> goals,

            @Size(max = 200, message = "Keep your goal to 200 characters.")
            String goalText,

            LocalDate deadline,

            @NotNull(message = "Pick how much time you can give.")
            PlanPace pace,

            @Size(max = 2, message = "Pick up to 2 things to get better at.")
            List<@NotNull PlanFocus> focus) {

        public GoalInput {
            goals = goals == null ? List.of() : List.copyOf(goals);
            focus = focus == null ? List.of() : List.copyOf(focus);
        }
    }

    /**
     * The level check of the setup: where the student said they are, and either every answer of the finished check or
     * {@code skipped} (brand new: start from the first lesson).
     */
    public record LevelCheckInput(
            @NotNull(message = "Tell us how much Mandarin you know.")
            PlanSelfLevel selfLevel,

            boolean skipped,

            @Size(max = 6, message = "The level check has at most 6 questions.")
            List<@Valid Answer> answers) {

        public LevelCheckInput {
            answers = answers == null ? List.of() : List.copyOf(answers);
        }
    }

    /** "Build my plan"; {@code language} is the interface language SparkBot writes in. */
    public record BuildRequest(
            @NotNull(message = "Tell us about your goal.")
            @Valid
            GoalInput goal,

            @NotNull(message = "Tell us how much Mandarin you know.")
            @Valid
            LevelCheckInput levelCheck,

            @Size(max = 20, message = "Language preference must not exceed 20 characters.")
            String language) {
    }

    /** "Update my goal": the level stays. */
    public record UpdateGoalRequest(
            @NotNull(message = "Tell us about your goal.")
            @Valid
            GoalInput goal,

            @Size(max = 20, message = "Language preference must not exceed 20 characters.")
            String language) {
    }

    /** A retaken level check: the level it started at and every answer. */
    public record RetakeRequest(
            MandarinLevel startLevel,

            @Size(max = 6, message = "The level check has at most 6 questions.")
            List<@Valid Answer> answers,

            @Size(max = 20, message = "Language preference must not exceed 20 characters.")
            String language) {

        public RetakeRequest {
            answers = answers == null ? List.of() : List.copyOf(answers);
        }
    }
}

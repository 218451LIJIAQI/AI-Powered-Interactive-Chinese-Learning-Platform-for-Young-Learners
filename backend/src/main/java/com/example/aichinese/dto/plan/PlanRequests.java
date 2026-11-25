package com.example.aichinese.dto.plan;

import static com.example.aichinese.dto.DtoNormalization.immutableListOrEmpty;
import static com.example.aichinese.dto.DtoNormalization.redactLength;
import static com.example.aichinese.dto.DtoNormalization.redactValue;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.example.aichinese.dto.learn.LevelCheckNextRequest.Answer;
import com.example.aichinese.entity.enums.PlanFocus;
import com.example.aichinese.entity.enums.PlanGoal;
import com.example.aichinese.entity.enums.PlanPace;
import com.example.aichinese.entity.enums.PlanReason;
import com.example.aichinese.entity.enums.PlanSelfLevel;

/** Requests of the learning plan: the setup, a new goal, the class to take first, and a retaken level check. */
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
            List<@NotNull(message = "Goal must not be null.") PlanGoal> goals,

            @Size(max = 200, message = "Keep your goal to 200 characters.")
            String goalText,

            LocalDate deadline,

            @NotNull(message = "Pick how much time you can give.")
            PlanPace pace,

            @Size(max = 2, message = "Pick up to 2 things to get better at.")
            List<@NotNull(message = "Focus must not be null.") PlanFocus> focus) {

        public GoalInput {
            reasonText = stripToNull(reasonText);
            goals = immutableListOrEmpty(goals);
            goalText = stripToNull(goalText);
            focus = immutableListOrEmpty(focus);
        }

        @Override
        public String toString() {
            return "GoalInput[reason=" + reason
                    + ", reasonText=" + redactLength(reasonText)
                    + ", goals=" + goals.size()
                    + ", goalText=" + redactLength(goalText)
                    + ", deadline=" + deadline
                    + ", pace=" + pace
                    + ", focus=" + focus + "]";
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

            @Size(max = 8, message = "The level check has at most 8 questions.")
            List<@NotNull(message = "Question is required.") @Valid Answer> answers,

            @Size(max = 512, message = "Start the level check again.")
            String runToken) {

        public LevelCheckInput {
            answers = immutableListOrEmpty(answers);
            runToken = stripToNull(runToken);
        }

        @Override
        public String toString() {
            return "LevelCheckInput[selfLevel=" + selfLevel
                    + ", skipped=" + skipped
                    + ", answers=" + answers.size()
                    + ", runToken=" + redactValue(runToken) + "]";
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

        public BuildRequest {
            language = stripToNull(language);
        }
    }

    /** "Update my goal": the level stays. */
    public record UpdateGoalRequest(
            @NotNull(message = "Tell us about your goal.")
            @Valid
            GoalInput goal,

            @Size(max = 20, message = "Language preference must not exceed 20 characters.")
            String language) {

        public UpdateGoalRequest {
            language = stripToNull(language);
        }
    }

    /** "Pick this first" on the road map: one of the plan's classes, or null to go back to the plan's own order. */
    public record ClassPickRequest(
            @Positive(message = "Pick a class from your plan.")
            Long classId) {
    }

    /** A retaken level check: every answer and the server-signed run token of that check. */
    public record RetakeRequest(
            @Size(max = 8, message = "The level check has at most 8 questions.")
            List<@NotNull(message = "Question is required.") @Valid Answer> answers,

            @Size(max = 20, message = "Language preference must not exceed 20 characters.")
            String language,

            @Size(max = 512, message = "Start the level check again.")
            String runToken) {

        public RetakeRequest {
            answers = immutableListOrEmpty(answers);
            language = stripToNull(language);
            runToken = stripToNull(runToken);
        }

        @Override
        public String toString() {
            return "RetakeRequest[answers=" + answers.size()
                    + ", language=" + language
                    + ", runToken=" + redactValue(runToken) + "]";
        }
    }
}

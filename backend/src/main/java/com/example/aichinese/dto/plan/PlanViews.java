package com.example.aichinese.dto.plan;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.example.aichinese.dto.DtoNormalization;
import com.example.aichinese.entity.enums.LearnLessonKind;
import com.example.aichinese.entity.enums.LevelCheckSkill;
import com.example.aichinese.entity.enums.MandarinLevel;
import com.example.aichinese.entity.enums.PlanFocus;
import com.example.aichinese.entity.enums.PlanGoal;
import com.example.aichinese.entity.enums.PlanPace;
import com.example.aichinese.entity.enums.PlanReason;
import com.example.aichinese.entity.enums.PlanSelfLevel;
import com.example.aichinese.entity.enums.PlanStepKind;
import com.example.aichinese.entity.enums.PracticeType;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the learning plan pages show. Texts SparkBot wrote are null in a plan built from rules: the pages then write
 * them from the goals (keys the interface translates). Progress always comes from the student's real completions.
 */
public final class PlanViews {

    private PlanViews() {
    }

    /**
     * STARTS_TODAY: the plan's first day, nothing done yet; BEHIND: a day of this plan week before today went without
     * the daily goal, or progress is well under the time that has passed.
     */
    public enum PlanStatus { STARTS_TODAY, ON_TRACK, BEHIND, COMPLETE }

    /** UNAVAILABLE: a unit or class that left the catalogue since the plan was built. */
    public enum StepStatus { DONE, IN_PROGRESS, NEXT, LATER, IN_MY_CLASSES, OPTIONAL, UNAVAILABLE }

    /** This week's to-dos: Learn lessons done this plan week, the next lesson, a practice set and Xiaomei. */
    public enum TaskKind { LEARN_DONE, LEARN_NEXT, PRACTICE, XIAOMEI }

    // Setup options

    /**
     * {@code today} is the platform's day (LearnClock), the one a goal date is checked against: the goal form counts its
     * spans ("in 2 weeks") and its earliest and latest dates from it, not from the browser's day. {@code checkStarts}
     * maps each self-reported level to the adaptive check's starting level as a single source of truth.
     */
    public record Options(
            List<ReasonOption> reasons,
            List<PaceBand> paces,
            Map<PlanSelfLevel, MandarinLevel> checkStarts,
            int goalTextMax,
            LocalDate today) {
    }

    /** A reason and the goals the setup suggests for it. */
    public record ReasonOption(PlanReason key, List<PlanGoal> goals) {
    }

    /**
     * A band of "Time I can give each day" in minutes a day ("1–2 hours a day"); {@code maxMinutesPerDay} is null for
     * the open-ended "2+ hours". The setup offers these, and a plan carries the one it was built for.
     */
    public record PaceBand(PlanPace key, int minMinutesPerDay, Integer maxMinutesPerDay) {

        public static PaceBand of(PlanPace pace) {
            return new PaceBand(pace, pace.minMinutesPerDay(), pace.maxMinutesPerDay());
        }
    }

    // The plan

    /**
     * {@code today} is the platform's day the plan was read on (LearnClock): the page tells a goal date that has passed
     * by it, as the server does, not by the browser's day. {@code pickedClassId} is the optional class the learner
     * picked to take first, null when they have not picked one or the pick no longer applies (the class was joined or
     * left the catalogue, or it is the only one left).
     */
    public record Plan(
            boolean aiAssisted,
            Goal goal,
            Level level,
            Weeks weeks,
            Progress progress,
            PaceBand pace,
            List<Step> steps,
            List<String> outcomes,
            Milestone milestone,
            List<WeekTask> thisWeek,
            Kept kept,
            LocalDate today,
            Long pickedClassId) {

        @Override
        public String toString() {
            return "Plan[aiAssisted=" + aiAssisted + ", goal=" + goal + ", level=" + level + ", weeks=" + weeks
                    + ", progress=" + progress + ", pace=" + pace + ", steps=" + (steps == null ? 0 : steps.size())
                    + ", outcomes=" + (outcomes == null ? 0 : outcomes.size()) + ", milestone=" + milestone
                    + ", thisWeek=" + (thisWeek == null ? 0 : thisWeek.size()) + ", kept=" + kept + ", today=" + today
                    + ", pickedClassId=" + pickedClassId + "]";
        }
    }

    /**
     * {@code title}, {@code summary} and {@code workOn} are SparkBot's (null / empty when built from rules);
     * {@code goals} are the goals the plan works towards (the picks, or the reason's defaults).
     */
    public record Goal(
            String title,
            String summary,
            PlanReason reason,
            String reasonText,
            List<PlanGoal> picks,
            List<PlanGoal> goals,
            String ownWords,
            LocalDate deadline,
            List<PlanFocus> focus,
            List<String> workOn) {

        @Override
        public String toString() {
            return "Goal[title=" + DtoNormalization.redactLength(title)
                    + ", summary=" + DtoNormalization.redactLength(summary)
                    + ", reason=" + reason
                    + ", reasonText=" + DtoNormalization.redactLength(reasonText)
                    + ", picks=" + picks
                    + ", goals=" + goals
                    + ", ownWords=" + DtoNormalization.redactLength(ownWords)
                    + ", deadline=" + deadline
                    + ", focus=" + focus
                    + ", workOn=" + (workOn == null ? 0 : workOn.size()) + "]";
        }
    }

    public record Level(
            MandarinLevel placed,
            MandarinLevel target,
            List<LevelCheckSkill> knownSkills,
            List<LevelCheckSkill> newSkills) {
    }

    /**
     * Calendar weeks counted from the plan's first day: {@code total} is the days to the goal date divided by 7, rounded
     * up (from the daily time without a date); {@code current} counts the 7-day blocks from the first day (and stays at
     * the last week once the plan has ended); {@code content} is the week the last Learn step should end, the weeks
     * after it are review and practice.
     */
    public record Weeks(int current, int total, int content) {
    }

    /**
     * {@code percent}: lessons done in the plan's Learn units and joined classes; {@code daysThisWeek}: days of this plan
     * week with the daily goal reached; {@code daysBehind}: days of this plan week before today without it.
     */
    public record Progress(int percent, PlanStatus status, int daysThisWeek, int daysBehind) {
    }

    /**
     * One step with its live progress. A Learn unit has its number, Chinese title and next lesson; a class has whether
     * the learner joined it, its next lesson once joined, and {@code bestAfterUnit}, the Learn unit the class is best
     * taken after (the class's own setting, null when it has none). {@code reason} is SparkBot's "Why"; a rule-based
     * plan explains the step with {@code goals}.
     */
    public record Step(
            int position,
            PlanStepKind kind,
            Long refId,
            String title,
            String titleZh,
            Integer unitNumber,
            int lessonCount,
            int completedCount,
            int percent,
            StepStatus status,
            boolean joined,
            boolean quickReview,
            int weekFrom,
            int weekTo,
            Integer bestAfterUnit,
            String reason,
            List<PlanGoal> goals,
            NextLesson nextLesson) {

        @Override
        public String toString() {
            return "Step[position=" + position + ", kind=" + kind + ", refId=" + refId + ", title=" + title
                    + ", titleZh=" + titleZh + ", unitNumber=" + unitNumber + ", lessonCount=" + lessonCount
                    + ", completedCount=" + completedCount + ", percent=" + percent + ", status=" + status
                    + ", joined=" + joined + ", quickReview=" + quickReview + ", weekFrom=" + weekFrom
                    + ", weekTo=" + weekTo + ", bestAfterUnit=" + bestAfterUnit
                    + ", reason=" + DtoNormalization.redactLength(reason)
                    + ", goals=" + goals + ", nextLesson=" + nextLesson + "]";
        }
    }

    /**
     * What to play next in a step: a lesson of the Learn unit, or, for the unit behind an open level checkpoint, that
     * checkpoint ({@code kind} CHECKPOINT); for a joined class, its next lesson ({@code kind} LESSON).
     */
    public record NextLesson(Long id, String title, LearnLessonKind kind) {
    }

    /**
     * The first milestone of an ambitious plan: its date, SparkBot's sentence (null from rules) and the Learn steps due
     * by then.
     */
    public record Milestone(LocalDate date, String text, List<Integer> stepPositions) {

        @Override
        public String toString() {
            return "Milestone[date=" + date + ", text=" + DtoNormalization.redactLength(text)
                    + ", stepPositions=" + stepPositions + "]";
        }
    }

    /** Discriminated this-week to-dos matching the frontend union without nullable placeholder fields. */
    public sealed interface WeekTask
            permits WeekTask.LearnDone, WeekTask.LearnNext, WeekTask.Practice, WeekTask.Xiaomei {

        @JsonProperty("kind")
        TaskKind kind();

        record LearnDone(String title, LocalDate doneOn) implements WeekTask {
            @Override
            public TaskKind kind() {
                return TaskKind.LEARN_DONE;
            }
        }

        record LearnNext(String title, Integer minutes, Long lessonId) implements WeekTask {
            @Override
            public TaskKind kind() {
                return TaskKind.LEARN_NEXT;
            }
        }

        record Practice(String title, PracticeType practiceType, Long practiceSetId) implements WeekTask {
            @Override
            public TaskKind kind() {
                return TaskKind.PRACTICE;
            }
        }

        record Xiaomei(boolean done) implements WeekTask {
            @Override
            public TaskKind kind() {
                return TaskKind.XIAOMEI;
            }
        }
    }

    /** What the student has done overall (the road map's "so far"; progress is never lost when the goal changes). */
    public record Kept(int lessons, int practiceSets) {
    }

    // Touchpoints

    /** The plan at a glance for Progress and a retaken level check; {@code plan} is null without a plan. */
    public record Overview(Summary plan) {
    }

    /**
     * {@code milestoneDate}: the first milestone of an ambitious plan (null without one); {@code daysBehind}: days of
     * this plan week before today without the daily goal ("You're 2 study days behind"); {@code today}: the platform's
     * day the summary was read on (LearnClock), so Progress and Home judge expired plans the same way as the server.
     */
    @Schema(name = "LearningPlanSummary")
    public record Summary(
            String goalTitle,
            PlanReason reason,
            LocalDate deadline,
            LocalDate milestoneDate,
            int week,
            int totalWeeks,
            int percent,
            PlanStatus status,
            int daysBehind,
            MandarinLevel level,
            MandarinLevel targetLevel,
            OptionalClass optionalClass,
            PracticeSuggestion practice,
            LocalDate today) {

        @Override
        public String toString() {
            return "Summary[goalTitle=" + DtoNormalization.redactLength(goalTitle) + ", reason=" + reason
                    + ", deadline=" + deadline + ", milestoneDate=" + milestoneDate + ", week=" + week
                    + ", totalWeeks=" + totalWeeks + ", percent=" + percent + ", status=" + status
                    + ", daysBehind=" + daysBehind + ", level=" + level + ", targetLevel=" + targetLevel
                    + ", optionalClass=" + optionalClass + ", practice=" + practice + ", today=" + today + "]";
        }
    }

    /**
     * The optional class not joined yet that comes first (the one the learner picked, else the plan's first), for
     * "Optional · Everyday Conversations"; {@code bestAfterUnit} is the class's own "Best after Unit N" (null when it has
     * none).
     */
    public record OptionalClass(String title, int weekFrom, Integer bestAfterUnit) {
    }

    /** A Practice set from the student's focus that is not done yet. */
    public record PracticeSuggestion(PracticeType type, Long setId, String title) {
    }

    // Level check retake

    /** The new level; {@code changed} when the level (or head start) changed and the plan was rebuilt. */
    public record RetakeResult(MandarinLevel level, boolean changed) {
    }
}

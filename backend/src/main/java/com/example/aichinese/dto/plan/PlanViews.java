package com.example.aichinese.dto.plan;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import com.example.aichinese.entity.enums.LevelCheckSkill;
import com.example.aichinese.entity.enums.MandarinLevel;
import com.example.aichinese.entity.enums.PlanFocus;
import com.example.aichinese.entity.enums.PlanGoal;
import com.example.aichinese.entity.enums.PlanPace;
import com.example.aichinese.entity.enums.PlanReason;
import com.example.aichinese.entity.enums.PlanSetupState;
import com.example.aichinese.entity.enums.PlanStepKind;
import com.example.aichinese.entity.enums.PracticeType;

/**
 * What the learning plan pages show. Texts SparkBot wrote are null in a plan built from rules: the pages then write
 * them from the goals (keys the interface translates). Progress always comes from the student's real completions.
 */
public final class PlanViews {

    private PlanViews() {
    }

    /** STARTS_TODAY: nothing done this first week yet; BEHIND: progress is well under the time that has passed. */
    public enum PlanStatus { STARTS_TODAY, ON_TRACK, BEHIND, COMPLETE }

    /** UNAVAILABLE: a class that left the catalogue since the plan was built. */
    public enum StepStatus { DONE, IN_PROGRESS, NEXT, LATER, IN_MY_CLASSES, OPTIONAL, UNAVAILABLE }

    /** This week's to-dos: Learn lessons done this plan week, the next lesson, a practice set and Xiaomei. */
    public enum TaskKind { LEARN_DONE, LEARN_NEXT, PRACTICE, XIAOMEI }

    // ===== Setup options =====

    public record Options(
            List<ReasonOption> reasons,
            List<PaceOption> paces,
            List<PlanFocus> focuses,
            int goalTextMax) {
    }

    /** A reason and the goals the setup suggests for it. */
    public record ReasonOption(PlanReason key, List<PlanGoal> goals) {
    }

    public record PaceOption(PlanPace key, int minutesPerDay, int daysPerWeek, int dailyGoalXp) {
    }

    // ===== The plan =====

    public record Plan(
            boolean aiAssisted,
            Goal goal,
            Level level,
            Weeks weeks,
            Progress progress,
            Pace pace,
            List<Step> steps,
            List<String> outcomes,
            List<CoverageItem> coverage,
            Milestone milestone,
            List<WeekTask> thisWeek,
            Kept kept) {
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
    }

    /** {@code checkedAt} is null when a brand-new student skipped the check. */
    public record Level(
            MandarinLevel placed,
            MandarinLevel target,
            boolean headStart,
            List<LevelCheckSkill> knownSkills,
            List<LevelCheckSkill> newSkills,
            LocalDateTime checkedAt) {
    }

    /** {@code content}: the week the last Learn step should end; the weeks after it are review and practice. */
    public record Weeks(int current, int total, int content) {
    }

    /**
     * {@code percent}: lessons done in the plan's Learn units and joined classes; {@code daysThisWeek}: days of this plan
     * week with the daily goal reached.
     */
    public record Progress(int percent, PlanStatus status, int daysThisWeek, int paceDays) {
    }

    /** {@code dailyGoalXp} is the student's current daily goal (changeable in Account). */
    public record Pace(PlanPace key, int minutesPerDay, int daysPerWeek, int dailyGoalXp) {
    }

    /**
     * One step with its live progress. A Learn unit has its number, Chinese title, icon and next lesson; a class has
     * its teacher, whether the student joined it and its next live session. {@code reason} is SparkBot's "Why"; a
     * rule-based plan explains the step with {@code goals}.
     */
    public record Step(
            int position,
            PlanStepKind kind,
            Long refId,
            String title,
            String titleZh,
            Integer unitNumber,
            Integer hskLevel,
            String iconKey,
            int lessonCount,
            int completedCount,
            int percent,
            StepStatus status,
            boolean joined,
            boolean quickReview,
            int weekFrom,
            int weekTo,
            String reason,
            List<PlanGoal> goals,
            String teacherName,
            NextLesson nextLesson,
            OffsetDateTime nextSessionAt) {
    }

    public record NextLesson(Long id, String title) {
    }

    /** A goal and what covers it: a Learn unit, a class in the plan (its title), or nothing yet. */
    public record CoverageItem(PlanGoal goal, boolean learn, Long classId, String classTitle) {
    }

    /**
     * The first milestone of an ambitious plan: its date, SparkBot's sentence (null from rules) and the Learn steps due
     * by then.
     */
    public record Milestone(LocalDate date, String text, List<Integer> stepPositions) {
    }

    /** {@code doneOn} for a lesson finished this week; {@code done} for Xiaomei when a talk happened this week. */
    public record WeekTask(
            TaskKind kind,
            String title,
            boolean done,
            LocalDate doneOn,
            Integer minutes,
            Long lessonId,
            PracticeType practiceType,
            Long practiceSetId) {
    }

    /** What the student has done overall (shown when the goal changes: progress is never lost). */
    public record Kept(int lessons, int practiceSets, int classes) {
    }

    // ===== Touchpoints =====

    /**
     * The plan at a glance for Home, Progress, the Learn rail, Find classes and the setup state. {@code plan} is null
     * without a plan.
     */
    public record Overview(PlanSetupState setupState, Summary plan) {
    }

    public record Summary(
            String goalTitle,
            PlanReason reason,
            LocalDate deadline,
            int week,
            int totalWeeks,
            int percent,
            PlanStatus status,
            MandarinLevel level,
            MandarinLevel targetLevel,
            GoalUnit goalUnit,
            List<ClassStep> classSteps,
            OptionalClass optionalClass,
            FirstStep firstStep,
            PracticeSuggestion practice) {
    }

    /** The furthest Learn unit the plan reaches ("Your goal is at Unit 3 · Getting around"). */
    public record GoalUnit(int number, String title, String titleZh) {
    }

    /** A class in the plan and its step number ("In your plan · step 4"). */
    public record ClassStep(Long classId, int position) {
    }

    /** The first optional class not joined yet, for "Optional · Everyday Conversations". */
    public record OptionalClass(Long classId, String title, int weekFrom) {
    }

    /** The plan's current Learn step, for "First · Review Say hello · 3 lessons · about 12 min". */
    public record FirstStep(String title, int lessonsLeft, int minutesLeft, boolean quickReview) {
    }

    /** A Practice set from the student's focus that is not done yet. */
    public record PracticeSuggestion(PracticeType type, Long setId, String title) {
    }

    // ===== Level check retake =====

    /** The new placement; {@code changed} when the level (or head start) changed and the plan was rebuilt. */
    public record RetakeResult(
            MandarinLevel level,
            boolean headStart,
            List<LevelCheckSkill> knownSkills,
            List<LevelCheckSkill> newSkills,
            int answered,
            boolean changed) {
    }

    // ===== Teacher =====

    /**
     * The read-only learning goal a teacher sees for a student in one of their classes (no own words, no AI summary).
     */
    public record TeacherGoal(
            String goalTitle,
            PlanReason reason,
            LocalDate deadline,
            List<PlanFocus> focus,
            MandarinLevel level,
            MandarinLevel targetLevel,
            LocalDateTime updatedAt,
            List<TeacherClassStep> yourClasses,
            int stepCount) {
    }

    public record TeacherClassStep(Long classId, String title, int position) {
    }
}

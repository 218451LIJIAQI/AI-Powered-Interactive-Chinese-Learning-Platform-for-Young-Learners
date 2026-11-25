package com.example.aichinese.service.plan;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.example.aichinese.dto.learn.UnitState;
import com.example.aichinese.entity.enums.LevelCheckSkill;
import com.example.aichinese.entity.enums.MandarinLevel;
import com.example.aichinese.entity.enums.PlanFocus;
import com.example.aichinese.entity.enums.PlanGoal;
import com.example.aichinese.entity.enums.PlanPace;
import com.example.aichinese.entity.enums.PlanReason;
import com.example.aichinese.entity.enums.PlanSelfLevel;
import com.example.aichinese.entity.enums.PlanStepKind;

/**
 * The rules that decide a plan; SparkBot never does. From the goal, the level check placement and the catalogue:
 * <ul>
 *   <li>the Learn units: from the student's current unit to the furthest unit a goal needs (the path opens in order,
 *       so every unit in between is part of the plan), and at least into the level after the placed one;</li>
 *   <li>units below the placed level, and Unit 1 for a Beginner 1 head start, are a quick review;</li>
 *   <li>classes are optional: joined classes ("In My classes") and one or two others that cover a goal the Learn units
 *       do not, or the student's focus;</li>
 *   <li>the Learn steps' weeks from the daily time: a lesson takes its minutes, three times over with practice and review
 *       (once for a quick review), against the minutes a week the daily time gives
 *       ({@link PlanPace#planningMinutesPerWeek()});</li>
 *   <li>the plan's weeks are calendar weeks from its first day: to the goal date (the days divided by 7, rounded up),
 *       or without a date the Learn steps' weeks and one more for review.</li>
 * </ul>
 * An {@link AiDraft} may then choose and order the classes among the candidates and add its texts; Learn units always
 * stay in path order.
 */
public final class PlanComposer {

    /** The latest goal date the setup accepts, in days from today (the calendar offers up to two years). */
    public static final int MAX_GOAL_DAYS = 730;

    /** A lesson's minutes, again in practice sets and once more in reviews. */
    private static final int STUDY_FACTOR = 3;
    /** The calendar weeks to the latest goal date: 730 days is 105 weeks, a started week counting in full. */
    private static final int MAX_WEEKS = Math.ceilDiv(MAX_GOAL_DAYS, 7);
    private static final int MAX_JOINED_CLASSES = 2;
    private static final int MAX_CLASS_CANDIDATES = 5;
    static final int MAX_PLAN_CLASSES = 3;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");

    private static final Comparator<PlanCatalogue.ClassOption> JOINED_FIRST =
            Comparator.comparing((PlanCatalogue.ClassOption option) -> !option.joined());

    private PlanComposer() {
    }

    /** What the student told the setup about their goal. */
    public record Goal(
            PlanReason reason,
            String reasonText,
            List<PlanGoal> picks,
            String ownWords,
            LocalDate deadline,
            PlanPace pace,
            List<PlanFocus> focus) {

        public Goal {
            picks = List.copyOf(new LinkedHashSet<>(picks));
            focus = List.copyOf(new LinkedHashSet<>(focus));
        }

        /** The goals the plan works towards: the picks, or the reason's defaults when there are none. */
        public List<PlanGoal> effectiveGoals() {
            List<PlanGoal> chosen = picks.stream().filter(goal -> goal != PlanGoal.NOT_SURE).toList();
            return chosen.isEmpty() ? reason.defaultGoals() : chosen;
        }

        /**
         * This goal for a plan that starts {@code startsOn}. A plan is never built towards a date that is not after its
         * first day: a stored goal whose date has passed (a retake rebuilding an older plan) is planned as if the
         * student had chosen "No deadline", and they can pick a new date with "Update my goal".
         */
        public Goal startingOn(LocalDate startsOn) {
            return deadline == null || deadline.isAfter(startsOn)
                    ? this
                    : new Goal(reason, reasonText, picks, ownWords, null, pace, focus);
        }

        /**
         * The calendar weeks from {@code today} to the goal date: the days divided by 7, a started week counting in full
         * (Mon 18 May to Sat 18 Jul is 61 days, 9 weeks); at least one; null without a date.
         */
        public Integer weeksToDeadline(LocalDate today) {
            return deadline == null ? null : (int) Math.max(1, Math.ceilDiv(ChronoUnit.DAYS.between(today, deadline), 7));
        }
    }

    /** Where the level check placed the student; {@code checked} is false when a brand-new student skipped it. */
    public record Placement(
            PlanSelfLevel selfLevel,
            MandarinLevel level,
            boolean headStart,
            List<LevelCheckSkill> knownSkills,
            List<LevelCheckSkill> newSkills,
            int answered,
            boolean checked) {
    }

    /** What SparkBot wrote and chose; every reference has already been checked against the candidates. */
    public record AiDraft(
            String goalTitle,
            String summary,
            List<String> workOn,
            MandarinLevel targetLevel,
            List<DraftStep> steps,
            List<String> outcomes,
            boolean unrealistic,
            String milestone) {
    }

    public record DraftStep(PlanStepKind kind, Long refId, String reason) {
    }

    /** The Learn units the plan needs and the classes SparkBot may choose from. */
    public record Candidates(List<PlanCatalogue.Unit> units, List<PlanCatalogue.ClassOption> classes) {
    }

    public record Step(
            PlanStepKind kind,
            Long refId,
            String title,
            String reason,
            Set<PlanGoal> goals,
            boolean quickReview,
            int weekFrom,
            int weekTo) {
    }

    public record Composed(
            List<Step> steps,
            MandarinLevel targetLevel,
            int contentWeeks,
            int totalWeeks,
            boolean ambitious,
            Integer milestoneWeek) {
    }

    // Candidates

    public static Candidates candidates(Goal goal, Placement placement, PlanCatalogue.Catalogue catalogue) {
        List<PlanCatalogue.Unit> units = learnUnits(goal, placement, catalogue.units());
        return new Candidates(units, rankedClasses(goal, units, catalogue.classes()));
    }

    /**
     * From the first unit not done to the furthest unit a goal needs, and at least into the level after the placed one
     * (a learner placed at HSK 1 works towards HSK 2), or to the placed level when the path has nothing after it.
     */
    private static List<PlanCatalogue.Unit> learnUnits(Goal goal, Placement placement, List<PlanCatalogue.Unit> units) {
        int start = firstIndex(units, unit -> unit.state() != UnitState.DONE);
        if (start < 0) {
            return List.of();
        }
        int end = start;
        for (PlanGoal planGoal : goal.effectiveGoals()) {
            end = Math.max(end, firstIndex(units, unit -> matches(planGoal, unit.text())));
        }
        int placedLevel = pathLevel(placement.level());
        int reach = firstIndex(units, unit -> unit.level() > placedLevel);
        end = Math.max(end, reach >= 0 ? reach : firstIndex(units, unit -> unit.level() >= placedLevel));
        return List.copyOf(units.subList(start, end + 1));
    }

    /** The index of the first unit that passes {@code test}, or -1. */
    private static int firstIndex(List<PlanCatalogue.Unit> units, Predicate<PlanCatalogue.Unit> test) {
        return IntStream.range(0, units.size()).filter(index -> test.test(units.get(index))).findFirst().orElse(-1);
    }

    /**
     * Classes by how well they fit: joined classes first (up to two, whatever their score), then the others scoring at
     * least 2: 2 for a goal the Learn units do not cover, 1 for any other goal they match, 1 for the focus.
     */
    private static List<PlanCatalogue.ClassOption> rankedClasses(
            Goal goal, List<PlanCatalogue.Unit> units, List<PlanCatalogue.ClassOption> classes) {
        Set<PlanGoal> covered = coveredGoals(goal, units);
        Map<PlanCatalogue.ClassOption, Integer> scores = new LinkedHashMap<>();
        for (PlanCatalogue.ClassOption option : classes) {
            int goalScore = goalsOf(goal, option.text()).stream()
                    .mapToInt(planGoal -> covered.contains(planGoal) ? 1 : 2)
                    .sum();
            boolean focus = goal.focus().stream().anyMatch(skill -> startsAWord(skill.keywords(), option.text()));
            scores.put(option, goalScore + (focus ? 1 : 0));
        }
        Comparator<PlanCatalogue.ClassOption> bestFirst = Comparator.comparingInt(option -> -scores.get(option));
        Stream<PlanCatalogue.ClassOption> joined = classes.stream()
                .filter(PlanCatalogue.ClassOption::joined)
                .sorted(bestFirst)
                .limit(MAX_JOINED_CLASSES);
        Stream<PlanCatalogue.ClassOption> others = classes.stream()
                .filter(option -> !option.joined() && scores.get(option) >= 2)
                .sorted(bestFirst);
        return Stream.concat(joined, others).limit(MAX_CLASS_CANDIDATES).toList();
    }

    /** The rule-based choice: the joined classes and the best other class (two when Learn leaves two goals open), capped at {@link #MAX_PLAN_CLASSES}. */
    private static List<PlanCatalogue.ClassOption> ruleClasses(Goal goal, Candidates candidates) {
        Set<PlanGoal> covered = coveredGoals(goal, candidates.units());
        long uncovered = goal.effectiveGoals().stream().filter(planGoal -> !covered.contains(planGoal)).count();
        return Stream.concat(
                        candidates.classes().stream().filter(PlanCatalogue.ClassOption::joined),
                        candidates.classes().stream().filter(option -> !option.joined()).limit(uncovered >= 2 ? 2 : 1))
                .limit(MAX_PLAN_CLASSES)
                .toList();
    }

    /** The goals the plan works towards that one of the Learn units teaches. */
    private static Set<PlanGoal> coveredGoals(Goal goal, List<PlanCatalogue.Unit> units) {
        Set<PlanGoal> covered = EnumSet.noneOf(PlanGoal.class);
        units.forEach(unit -> covered.addAll(goalsOf(goal, unit.text())));
        return covered;
    }

    // Compose

    /**
     * The plan: Learn units in path order with the classes placed after the unit they back up (SparkBot's placement
     * when it chose them), week ranges from the daily time, the target level and, for a goal date too close for the
     * path (or a goal SparkBot finds unrealistic), a first milestone. With a date the plan has exactly the calendar
     * weeks to it (a too-close date's milestone is that date; the Learn steps after it come later); without one it has
     * the Learn steps' weeks and a review week.
     */
    public static Composed compose(
            Goal goal,
            Placement placement,
            Candidates candidates,
            Optional<AiDraft> draft,
            LocalDate today) {
        List<PlanCatalogue.Unit> units = candidates.units();
        Map<StepRef, String> reasons = new LinkedHashMap<>();
        draft.ifPresent(found -> found.steps().forEach(step -> reasons.put(new StepRef(step.kind(), step.refId()), step.reason())));

        // Learn steps, with their weeks from the daily time.
        int capacity = goal.pace().planningMinutesPerWeek();
        int minutes = 0;
        List<Step> learnSteps = new ArrayList<>();
        for (PlanCatalogue.Unit unit : units) {
            boolean quickReview = quickReview(placement, unit);
            int start = minutes;
            minutes += Math.max(1, unit.remainingMinutes()) * (quickReview ? 1 : STUDY_FACTOR);
            int weekFrom = start / capacity + 1;
            int weekTo = Math.max(weekFrom, Math.ceilDiv(minutes, capacity));
            learnSteps.add(new Step(PlanStepKind.LEARN_UNIT, unit.id(), unit.title(),
                    reasons.get(new StepRef(PlanStepKind.LEARN_UNIT, unit.id())), goalsOf(goal, unit.text()), quickReview,
                    Math.min(weekFrom, MAX_WEEKS), Math.min(weekTo, MAX_WEEKS)));
        }
        int contentWeeks = Math.clamp(Math.ceilDiv(minutes, capacity), 1, MAX_WEEKS);

        // Each class follows the Learn unit it backs up; a class without a unit of the plan (or without any unit) goes
        // with the first unit.
        Map<PlanCatalogue.ClassOption, Long> anchors = classAnchors(goal, candidates, draft, learnSteps);
        Long firstLearnId = learnSteps.isEmpty() ? null : learnSteps.getFirst().refId();
        Map<Long, List<PlanCatalogue.ClassOption>> classesByUnit = new LinkedHashMap<>();
        for (Step learn : learnSteps) {
            classesByUnit.put(learn.refId(), new ArrayList<>());
        }
        List<PlanCatalogue.ClassOption> unplaced = new ArrayList<>();
        anchors.forEach((option, anchorId) -> {
            List<PlanCatalogue.ClassOption> bucket = anchorId == null ? null : classesByUnit.get(anchorId);
            if (bucket != null) {
                bucket.add(option);
            } else if (firstLearnId != null) {
                classesByUnit.get(firstLearnId).add(option);
            } else {
                unplaced.add(option);
            }
        });
        List<Step> steps = new ArrayList<>(learnSteps.size() + anchors.size());
        if (learnSteps.isEmpty()) {
            unplaced.sort(JOINED_FIRST);
            unplaced.forEach(option -> steps.add(classStep(goal, option, reasons, 1, contentWeeks)));
        } else {
            for (Step learn : learnSteps) {
                steps.add(learn);
                List<PlanCatalogue.ClassOption> here = classesByUnit.get(learn.refId());
                here.sort(JOINED_FIRST);
                here.forEach(option -> steps.add(classStep(goal, option, reasons, learn.weekFrom(), contentWeeks)));
            }
        }

        // Weeks and the deadline.
        Schedule schedule = schedule(contentWeeks, goal.weeksToDeadline(today),
                draft.map(AiDraft::unrealistic).orElse(false));

        return new Composed(steps, targetLevel(placement, units, draft), contentWeeks,
                schedule.totalWeeks(), schedule.ambitious(), schedule.milestoneWeek());
    }

    record Schedule(int totalWeeks, boolean ambitious, Integer milestoneWeek) {
    }

    /** Computes plan length and milestone week from study content weeks and optional deadline weeks. */
    static Schedule schedule(int contentWeeks, Integer weeksToDeadline, boolean aiUnrealistic) {
        Integer availableWeeks = weeksToDeadline == null ? null : Math.min(weeksToDeadline, MAX_WEEKS);
        boolean overDeadline = availableWeeks != null && contentWeeks > availableWeeks;
        boolean ambitious = overDeadline || aiUnrealistic;
        int totalWeeks = availableWeeks == null ? Math.min(MAX_WEEKS, contentWeeks + 1) : availableWeeks;
        Integer milestoneWeek = !ambitious ? null : overDeadline ? availableWeeks : contentWeeks;
        return new Schedule(totalWeeks, ambitious, milestoneWeek);
    }

    /**
     * The calendar week of a plan {@code day} is in: the 7-day blocks from the plan's first day (Sat 9 May to Sat 18
     * Jul is 10 weeks, Mon 18 May is week 2), the last week once the plan has ended.
     */
    public static int weekOf(LocalDate startsOn, LocalDate day, int totalWeeks) {
        long days = Math.max(0, ChronoUnit.DAYS.between(startsOn, day));
        return (int) Math.min(totalWeeks, days / 7 + 1);
    }

    /**
     * The plan's classes in order, each with the Learn unit it backs up: SparkBot's choice and placement when it chose
     * classes (a class backs up the unit before it in SparkBot's order, none when it comes before every unit), else the
     * rules' ({@link #ruleClasses}, {@link #ruleAnchor}). Joined classes are never dropped even if SparkBot omits them.
     */
    private static Map<PlanCatalogue.ClassOption, Long> classAnchors(
            Goal goal, Candidates candidates, Optional<AiDraft> draft, List<Step> learnSteps) {
        Map<PlanCatalogue.ClassOption, Long> anchors = new LinkedHashMap<>();
        if (draft.isEmpty() || draft.get().steps().stream().noneMatch(step -> step.kind() == PlanStepKind.CLASS)) {
            ruleClasses(goal, candidates)
                    .forEach(option -> anchors.put(option, ruleAnchor(goal, candidates.units(), learnSteps, option)));
            return anchors;
        }
        Map<Long, PlanCatalogue.ClassOption> byId = new LinkedHashMap<>();
        int joinedCount = 0;
        for (PlanCatalogue.ClassOption option : candidates.classes()) {
            byId.put(option.id(), option);
            if (option.joined()) {
                joinedCount++;
            }
        }
        int optionalCap = Math.max(0, MAX_PLAN_CLASSES - joinedCount);
        int optionalPicked = 0;
        Long anchor = null;
        for (DraftStep step : draft.get().steps()) {
            if (step.kind() == PlanStepKind.LEARN_UNIT) {
                anchor = step.refId();
                continue;
            }
            PlanCatalogue.ClassOption option = byId.get(step.refId());
            if (option == null || anchors.containsKey(option) || anchors.size() >= MAX_PLAN_CLASSES) {
                continue;
            }
            if (option.joined()) {
                anchors.put(option, anchor);
            } else if (optionalPicked < optionalCap) {
                anchors.put(option, anchor);
                optionalPicked++;
            }
        }
        // Keep every joined class even when SparkBot left it out; optionalCap reserved slots for them above.
        for (PlanCatalogue.ClassOption option : candidates.classes()) {
            if (anchors.size() >= MAX_PLAN_CLASSES) {
                break;
            }
            if (option.joined() && !anchors.containsKey(option)) {
                anchors.put(option, ruleAnchor(goal, candidates.units(), learnSteps, option));
            }
        }
        return anchors;
    }

    private static Step classStep(
            Goal goal, PlanCatalogue.ClassOption option, Map<StepRef, String> reasons, int weekFrom, int contentWeeks) {
        return new Step(PlanStepKind.CLASS, option.id(), option.title(), reasons.get(new StepRef(PlanStepKind.CLASS, option.id())),
                goalsOf(goal, option.text()), false, weekFrom, Math.max(weekFrom, contentWeeks));
    }

    /**
     * A class backs up its authored {@code bestAfterUnit} when that unit is in the plan, else the first Learn unit that
     * shares one of its goals, else the first unit.
     */
    private static Long ruleAnchor(
            Goal goal, List<PlanCatalogue.Unit> units, List<Step> learnSteps, PlanCatalogue.ClassOption option) {
        if (option.bestAfterUnit() != null) {
            Optional<Long> authored = units.stream()
                    .filter(unit -> unit.number() == option.bestAfterUnit())
                    .map(PlanCatalogue.Unit::id)
                    .findFirst();
            if (authored.isPresent()) {
                return authored.get();
            }
        }
        Set<PlanGoal> classGoals = goalsOf(goal, option.text());
        return learnSteps.stream()
                .filter(step -> step.goals().stream().anyMatch(classGoals::contains))
                .map(Step::refId)
                .findFirst()
                .orElse(learnSteps.isEmpty() ? null : learnSteps.get(0).refId());
    }

    /**
     * The level the plan's units reach (never below where the student is now). SparkBot may suggest a more realistic,
     * lower target, but never one below the student's level or above what the units teach.
     */
    private static MandarinLevel targetLevel(
            Placement placement, List<PlanCatalogue.Unit> units, Optional<AiDraft> draft) {
        MandarinLevel reached = units.isEmpty() ? placement.level() : levelOfPath(units.get(units.size() - 1).level());
        MandarinLevel ceiling = reached.compareTo(placement.level()) > 0 ? reached : placement.level();
        return draft.map(AiDraft::targetLevel)
                .filter(suggested -> suggested.compareTo(placement.level()) >= 0 && suggested.compareTo(ceiling) <= 0)
                .orElse(ceiling);
    }

    /** Units below the placed level, and Unit 1 when a Beginner 1 head start already knows some of it. */
    private static boolean quickReview(Placement placement, PlanCatalogue.Unit unit) {
        if (unit.level() < pathLevel(placement.level())) {
            return true;
        }
        return placement.headStart() && placement.level() == MandarinLevel.BEGINNER_1 && unit.number() == 1
                && !placement.knownSkills().isEmpty();
    }

    // Matching

    /** A goal matches a text when one of its keywords starts a word there ("number" matches "Numbers 1–10"). */
    public static boolean matches(PlanGoal goal, String text) {
        return startsAWord(goal.keywords(), text);
    }

    /** The goals the plan works towards that {@code text} matches. */
    private static Set<PlanGoal> goalsOf(Goal goal, String text) {
        return goal.effectiveGoals().stream()
                .filter(planGoal -> matches(planGoal, text))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(PlanGoal.class)));
    }

    private static boolean startsAWord(List<String> keywords, String text) {
        Matcher words = WORD.matcher(text);
        while (words.find()) {
            for (String keyword : keywords) {
                if (text.regionMatches(true, words.start(), keyword, 0, keyword.length())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int pathLevel(MandarinLevel level) {
        return switch (level) {
            case BEGINNER_2 -> 2;
            case BEGINNER_1 -> 1;
            default -> 0;
        };
    }

    static MandarinLevel levelOfPath(int pathLevel) {
        return pathLevel >= 2 ? MandarinLevel.BEGINNER_2 : MandarinLevel.BEGINNER_1;
    }

    /** Unit and class primary keys belong to different tables. */
    record StepRef(PlanStepKind kind, Long id) {
    }
}

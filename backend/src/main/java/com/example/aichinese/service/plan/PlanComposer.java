package com.example.aichinese.service.plan;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.example.aichinese.entity.enums.LevelCheckSkill;
import com.example.aichinese.entity.enums.MandarinLevel;
import com.example.aichinese.entity.enums.PlanFocus;
import com.example.aichinese.entity.enums.PlanGoal;
import com.example.aichinese.entity.enums.PlanPace;
import com.example.aichinese.entity.enums.PlanReason;
import com.example.aichinese.entity.enums.PlanSelfLevel;
import com.example.aichinese.entity.enums.PlanStepKind;
import com.example.aichinese.service.learn.LearnPathModel.UnitState;

/**
 * The rules that decide a plan; SparkBot never does. From the goal, the level check placement and the catalogue:
 * <ul>
 *   <li>the Learn units: from the student's current unit to the furthest unit a goal needs (the path opens in order,
 *       so every unit in between is part of the plan), and at least to the start of the placed level;</li>
 *   <li>units below the placed level, and Unit 1 for a Beginner 1 head start, are a quick review;</li>
 *   <li>classes are optional: joined classes that fit the goal ("In My classes") and one or two others that cover a
 *       goal the Learn units do not, or the student's focus;</li>
 *   <li>weeks from the pace: a lesson takes its minutes, three times over with practice and review (once for a quick
 *       review), against the minutes the pace gives a week;</li>
 *   <li>goal coverage: a goal is covered when a Learn unit or class in the plan matches it.</li>
 * </ul>
 * An {@link AiDraft} may then choose and order the classes among the candidates and add its texts; Learn units always
 * stay in path order.
 */
public final class PlanComposer {

    /** A lesson's minutes, again in practice sets and once more in reviews. */
    static final int STUDY_FACTOR = 3;
    static final int MAX_WEEKS = 52;
    static final int MAX_JOINED_CLASSES = 2;
    static final int MAX_CLASS_CANDIDATES = 5;
    static final int MAX_PLAN_CLASSES = 3;

    private static final Map<PlanFocus, List<String>> FOCUS_KEYWORDS = Map.of(
            PlanFocus.SPEAKING, List.of("speaking", "pronunciation", "tone"),
            PlanFocus.LISTENING, List.of("listening"),
            PlanFocus.READING, List.of("reading", "character", "pinyin"),
            PlanFocus.WRITING, List.of("writing", "character", "stroke"),
            PlanFocus.VOCABULARY, List.of("vocabulary", "words"),
            PlanFocus.CONVERSATION, List.of("conversation", "speaking", "listening"),
            PlanFocus.BALANCED, List.of());

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
            picks = picks == null ? List.of() : List.copyOf(new LinkedHashSet<>(picks));
            focus = focus == null ? List.of() : List.copyOf(new LinkedHashSet<>(focus));
        }

        /** The goals the plan works towards: the picks, or the reason's defaults when there are none. */
        public List<PlanGoal> effectiveGoals() {
            List<PlanGoal> chosen = picks.stream().filter(goal -> goal != PlanGoal.NOT_SURE).toList();
            return chosen.isEmpty() ? reason.defaultGoals() : chosen;
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

        public Placement {
            knownSkills = knownSkills == null ? List.of() : List.copyOf(knownSkills);
            newSkills = newSkills == null ? List.of() : List.copyOf(newSkills);
        }
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

        public AiDraft {
            workOn = workOn == null ? List.of() : List.copyOf(workOn);
            steps = steps == null ? List.of() : List.copyOf(steps);
            outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
        }
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
            boolean optional,
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

    // ===== Candidates =====

    public static Candidates candidates(Goal goal, Placement placement, PlanCatalogue.Catalogue catalogue) {
        List<PlanCatalogue.Unit> units = learnUnits(goal, placement, catalogue.units());
        return new Candidates(units, rankedClasses(goal, units, catalogue.classes()).stream()
                .limit(MAX_CLASS_CANDIDATES)
                .toList());
    }

    /** From the first unit not done to the furthest unit a goal needs (and at least to the placed level). */
    static List<PlanCatalogue.Unit> learnUnits(Goal goal, Placement placement, List<PlanCatalogue.Unit> units) {
        int start = -1;
        for (int index = 0; index < units.size(); index++) {
            if (units.get(index).state() != UnitState.DONE) {
                start = index;
                break;
            }
        }
        if (start < 0) {
            return List.of();
        }
        int end = start;
        for (PlanGoal planGoal : goal.effectiveGoals()) {
            for (int index = 0; index < units.size(); index++) {
                if (matches(planGoal, units.get(index).text())) {
                    end = Math.max(end, index);
                    break;
                }
            }
        }
        int placedLevel = pathLevel(placement.level());
        for (int index = 0; index < units.size(); index++) {
            if (units.get(index).level() >= placedLevel) {
                end = Math.max(end, index);
                break;
            }
        }
        return List.copyOf(units.subList(start, end + 1));
    }

    /**
     * Classes by how well they fit: joined classes first (up to two), then the others by score: 2 for a goal the
     * Learn units do not cover, 1 for any other goal they match, 1 for the focus. A class that fits nothing is left out.
     */
    static List<PlanCatalogue.ClassOption> rankedClasses(
            Goal goal, List<PlanCatalogue.Unit> units, List<PlanCatalogue.ClassOption> classes) {
        Set<PlanGoal> learnGoals = goal.effectiveGoals().stream()
                .filter(planGoal -> units.stream().anyMatch(unit -> matches(planGoal, unit.text())))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(PlanGoal.class)));
        Map<PlanCatalogue.ClassOption, Integer> scores = new LinkedHashMap<>();
        for (PlanCatalogue.ClassOption option : classes) {
            int score = 0;
            for (PlanGoal planGoal : goal.effectiveGoals()) {
                if (matches(planGoal, option.text())) {
                    score += learnGoals.contains(planGoal) ? 1 : 2;
                }
            }
            if (goal.focus().stream().anyMatch(focus -> focusMatches(focus, option.text()))) {
                score += 1;
            }
            scores.put(option, score);
        }
        List<PlanCatalogue.ClassOption> joined = classes.stream()
                .filter(PlanCatalogue.ClassOption::joined)
                .sorted(Comparator.comparingInt((PlanCatalogue.ClassOption option) -> -scores.get(option)))
                .limit(MAX_JOINED_CLASSES)
                .toList();
        List<PlanCatalogue.ClassOption> others = classes.stream()
                .filter(option -> !option.joined() && scores.get(option) >= 2)
                .sorted(Comparator.comparingInt((PlanCatalogue.ClassOption option) -> -scores.get(option)))
                .toList();
        List<PlanCatalogue.ClassOption> ranked = new ArrayList<>(joined);
        ranked.addAll(others);
        return ranked;
    }

    /** The rule-based choice: the joined classes and the best other class (two when Learn leaves two goals open). */
    static List<PlanCatalogue.ClassOption> ruleClasses(Goal goal, Candidates candidates) {
        long uncovered = goal.effectiveGoals().stream()
                .filter(planGoal -> candidates.units().stream().noneMatch(unit -> matches(planGoal, unit.text())))
                .count();
        int optionalLimit = uncovered >= 2 ? 2 : 1;
        List<PlanCatalogue.ClassOption> chosen = new ArrayList<>(
                candidates.classes().stream().filter(PlanCatalogue.ClassOption::joined).toList());
        candidates.classes().stream()
                .filter(option -> !option.joined())
                .limit(optionalLimit)
                .forEach(chosen::add);
        return chosen;
    }

    // ===== Compose =====

    /**
     * The plan: Learn units in path order with the classes placed after the unit they back up (SparkBot's placement
     * when it chose them), week ranges from the pace, the target level and, for a deadline shorter than the path,
     * a first milestone.
     */
    public static Composed compose(
            Goal goal,
            Placement placement,
            Candidates candidates,
            Optional<AiDraft> draft,
            LocalDate today) {
        List<PlanCatalogue.Unit> units = candidates.units();
        Map<Long, String> reasons = new LinkedHashMap<>();
        draft.ifPresent(found -> found.steps().forEach(step -> reasons.put(key(step.kind(), step.refId()), step.reason())));

        // Learn steps, with their weeks from the pace.
        int capacity = goal.pace().minutesPerWeek();
        int minutes = 0;
        List<Step> learnSteps = new ArrayList<>();
        for (PlanCatalogue.Unit unit : units) {
            boolean quickReview = quickReview(placement, unit);
            int start = minutes;
            minutes += Math.max(1, unit.remainingMinutes()) * (quickReview ? 1 : STUDY_FACTOR);
            int weekFrom = start / capacity + 1;
            int weekTo = Math.max(weekFrom, ceilDiv(minutes, capacity));
            learnSteps.add(new Step(PlanStepKind.LEARN_UNIT, unit.id(), unit.title(),
                    reasons.get(key(PlanStepKind.LEARN_UNIT, unit.id())), goalsOf(goal, unit.text()), false,
                    quickReview, Math.min(weekFrom, MAX_WEEKS), Math.min(weekTo, MAX_WEEKS)));
        }
        int contentWeeks = Math.min(MAX_WEEKS, Math.max(1, ceilDiv(minutes, capacity)));

        // Classes: SparkBot's choice and placement, or the rules'.
        List<PlanCatalogue.ClassOption> classes;
        Map<Long, Long> anchors = new LinkedHashMap<>();
        if (draft.isPresent() && draft.get().steps().stream().anyMatch(step -> step.kind() == PlanStepKind.CLASS)) {
            classes = new ArrayList<>();
            Long anchor = null;
            for (DraftStep step : draft.get().steps()) {
                if (step.kind() == PlanStepKind.LEARN_UNIT) {
                    anchor = step.refId();
                } else if (classes.size() < MAX_PLAN_CLASSES) {
                    Optional<PlanCatalogue.ClassOption> option = candidates.classes().stream()
                            .filter(candidate -> candidate.id().equals(step.refId()))
                            .findFirst();
                    if (option.isPresent() && !classes.contains(option.get())) {
                        classes.add(option.get());
                        anchors.put(option.get().id(), anchor);
                    }
                }
            }
        } else {
            classes = ruleClasses(goal, candidates);
            for (PlanCatalogue.ClassOption option : classes) {
                anchors.put(option.id(), ruleAnchor(goal, learnSteps, option));
            }
        }

        List<Step> steps = new ArrayList<>();
        List<PlanCatalogue.ClassOption> unplaced = new ArrayList<>(classes.stream()
                .filter(option -> anchors.get(option.id()) == null
                        || learnSteps.stream().noneMatch(step -> step.refId().equals(anchors.get(option.id()))))
                .toList());
        if (learnSteps.isEmpty()) {
            unplaced.forEach(option -> steps.add(classStep(goal, option, reasons, 1, contentWeeks)));
            unplaced.clear();
        }
        for (int index = 0; index < learnSteps.size(); index++) {
            Step learn = learnSteps.get(index);
            steps.add(learn);
            List<PlanCatalogue.ClassOption> here = new ArrayList<>();
            classes.stream().filter(option -> learn.refId().equals(anchors.get(option.id()))).forEach(here::add);
            if (index == 0) {
                here.addAll(unplaced);
            }
            here.sort(Comparator.comparing((PlanCatalogue.ClassOption option) -> !option.joined()));
            here.forEach(option -> steps.add(classStep(goal, option, reasons, learn.weekFrom(), contentWeeks)));
        }

        // Weeks and the deadline.
        Integer availableWeeks = goal.deadline() == null ? null
                : (int) Math.min(MAX_WEEKS, Math.max(1, ceilDiv((int) ChronoUnit.DAYS.between(today, goal.deadline()), 7)));
        boolean overDeadline = availableWeeks != null && contentWeeks > availableWeeks;
        boolean ambitious = overDeadline || draft.map(AiDraft::unrealistic).orElse(false);
        int totalWeeks;
        if (availableWeeks == null) {
            totalWeeks = Math.min(MAX_WEEKS, contentWeeks + 1);
        } else {
            totalWeeks = Math.max(contentWeeks, availableWeeks);
        }
        Integer milestoneWeek = !ambitious ? null : overDeadline ? availableWeeks : Math.min(totalWeeks, contentWeeks);

        return new Composed(steps, targetLevel(placement, units, draft), contentWeeks, totalWeeks, ambitious, milestoneWeek);
    }

    private static Step classStep(
            Goal goal, PlanCatalogue.ClassOption option, Map<Long, String> reasons, int weekFrom, int contentWeeks) {
        return new Step(PlanStepKind.CLASS, option.id(), option.title(), reasons.get(key(PlanStepKind.CLASS, option.id())),
                goalsOf(goal, option.text()), true, false, weekFrom, Math.max(weekFrom, contentWeeks));
    }

    /** A class backs up the first Learn unit that shares one of its goals, else the first unit. */
    private static Long ruleAnchor(Goal goal, List<Step> learnSteps, PlanCatalogue.ClassOption option) {
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
    static MandarinLevel targetLevel(Placement placement, List<PlanCatalogue.Unit> units, Optional<AiDraft> draft) {
        MandarinLevel reached = units.isEmpty() ? placement.level() : levelOfPath(units.get(units.size() - 1).level());
        MandarinLevel ceiling = reached.ordinal() > placement.level().ordinal() ? reached : placement.level();
        MandarinLevel suggested = draft.map(AiDraft::targetLevel).orElse(null);
        boolean valid = suggested != null
                && suggested.ordinal() >= placement.level().ordinal()
                && suggested.ordinal() <= ceiling.ordinal();
        return valid ? suggested : ceiling;
    }

    /** Units below the placed level, and Unit 1 when a Beginner 1 head start already knows some of it. */
    static boolean quickReview(Placement placement, PlanCatalogue.Unit unit) {
        if (unit.level() < pathLevel(placement.level())) {
            return true;
        }
        return placement.headStart() && placement.level() == MandarinLevel.BEGINNER_1 && unit.number() == 1
                && !placement.knownSkills().isEmpty();
    }

    // ===== Coverage =====

    /** One goal and what covers it in the plan: a Learn unit, a class (its title), or nothing yet. */
    public record Coverage(PlanGoal goal, boolean learn, Long classId, String classTitle) {
    }

    public static List<Coverage> coverage(List<PlanGoal> goals, List<StepGoals> steps) {
        return goals.stream().map(goal -> {
            boolean learn = steps.stream()
                    .anyMatch(step -> step.kind() == PlanStepKind.LEARN_UNIT && step.goals().contains(goal));
            Optional<StepGoals> classStep = steps.stream()
                    .filter(step -> step.kind() == PlanStepKind.CLASS && step.goals().contains(goal))
                    .findFirst();
            return new Coverage(goal, learn, classStep.map(StepGoals::refId).orElse(null),
                    classStep.map(StepGoals::title).orElse(null));
        }).toList();
    }

    public record StepGoals(PlanStepKind kind, Long refId, String title, Set<PlanGoal> goals) {
    }

    // ===== Matching =====

    /** A goal matches a text when one of its keywords starts a word there ("number" matches "Numbers 1–10"). */
    public static boolean matches(PlanGoal goal, String text) {
        String haystack = PlanCatalogue.normalize(text);
        return goal.keywords().stream().anyMatch(keyword -> startsWord(haystack, keyword));
    }

    static boolean focusMatches(PlanFocus focus, String text) {
        String haystack = PlanCatalogue.normalize(text);
        return FOCUS_KEYWORDS.getOrDefault(focus, List.of()).stream().anyMatch(keyword -> startsWord(haystack, keyword));
    }

    static Set<PlanGoal> goalsOf(Goal goal, String text) {
        return goal.effectiveGoals().stream()
                .filter(planGoal -> matches(planGoal, text))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(PlanGoal.class)));
    }

    private static boolean startsWord(String haystack, String keyword) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(keyword.toLowerCase(Locale.ROOT)))
                .matcher(haystack)
                .find();
    }

    // ===== Helpers =====

    static int pathLevel(MandarinLevel level) {
        return switch (level) {
            case BEGINNER_2 -> 2;
            case BEGINNER_1 -> 1;
            default -> 0;
        };
    }

    static MandarinLevel levelOfPath(int pathLevel) {
        return pathLevel >= 2 ? MandarinLevel.BEGINNER_2 : MandarinLevel.BEGINNER_1;
    }

    static Long key(PlanStepKind kind, Long refId) {
        // Unit and class ids come from different tables; the sign keeps them apart in one map.
        return kind == PlanStepKind.CLASS ? -refId : refId;
    }

    private static int ceilDiv(int value, int divisor) {
        return Math.max(0, (value + divisor - 1) / divisor);
    }
}

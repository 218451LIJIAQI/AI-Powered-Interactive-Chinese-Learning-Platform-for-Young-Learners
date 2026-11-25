package com.example.aichinese.service.plan;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.example.aichinese.dto.classes.ClassViews;
import com.example.aichinese.dto.learn.LearnPathResponse;
import com.example.aichinese.dto.learn.StopState;
import com.example.aichinese.dto.learn.UnitState;
import com.example.aichinese.dto.plan.PlanViews;
import com.example.aichinese.entity.LearnLessonCompletion;
import com.example.aichinese.entity.LearningPlan;
import com.example.aichinese.entity.LearningPlanStep;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.entity.enums.LearnLessonKind;
import com.example.aichinese.entity.enums.PlanFocus;
import com.example.aichinese.entity.enums.PlanGoal;
import com.example.aichinese.entity.enums.PlanStepKind;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.repository.AiUsageLogRepository;
import com.example.aichinese.repository.LearnLessonCompletionRepository;
import com.example.aichinese.repository.LearningPlanStepRepository;
import com.example.aichinese.repository.PracticeCompletionRepository;
import com.example.aichinese.service.RewardRules;
import com.example.aichinese.service.classes.StudentClassService;
import com.example.aichinese.service.learn.LearnClock;
import com.example.aichinese.service.learn.PracticeSetDirectory;
import com.example.aichinese.service.learn.XpService;

import lombok.RequiredArgsConstructor;

/**
 * Turns a stored plan into what the pages show, with live progress: each step's state from the Learn path and the
 * joined classes, the plan's percentage, the plan week, the days of this week with the daily goal reached, this
 * week's to-dos and the first milestone.
 */
@Component
@RequiredArgsConstructor
class PlanViewAssembler {

    /** Progress may trail the time passed by this many points and still be "on track". */
    private static final int ON_TRACK_TOLERANCE = 10;
    private static final int DONE_TASKS = 2;

    private final LearningPlanStepRepository stepRepository;
    private final PlanCatalogue planCatalogue;
    private final StudentClassService studentClassService;
    private final PracticeSetDirectory practiceSets;
    private final XpService xpService;
    private final LearnLessonCompletionRepository completionRepository;
    private final PracticeCompletionRepository practiceCompletionRepository;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final LearnClock clock;

    /** The stored steps, catalogue and progress for the learner's plan, read once per request. */
    record Live(
            List<LearningPlanStep> planSteps,
            PlanCatalogue.Catalogue catalogue,
            Map<Long, ClassViews.MyClassCard> joined,
            LocalDate today) {
    }

    Live live(StudentProfile student, LearningPlan plan) {
        List<LearningPlanStep> planSteps = plan == null ? List.of() : stepRepository.findByPlanIdOrderByPositionAsc(plan.getId());
        Set<Long> classIds = planSteps.stream().filter(step -> step.getStepKind() == PlanStepKind.CLASS)
                .map(LearningPlanStep::getRefId).collect(Collectors.toSet());
        Map<Long, ClassViews.MyClassCard> joined = studentClassService.joinedCards(student, classIds).stream()
                .collect(Collectors.toMap(card -> card.summary().id(), Function.identity()));
        Set<Long> optionalClassIds = classIds.stream()
                .filter(id -> !joined.containsKey(id))
                .collect(Collectors.toSet());
        return new Live(planSteps, planCatalogue.forStoredPlan(student, optionalClassIds), joined, clock.today());
    }

    // Whole plan

    PlanViews.Plan plan(StudentProfile student, LearningPlan plan) {
        Live live = live(student, plan);
        List<PlanViews.Step> steps = steps(live);
        PlanComposer.Goal goal = PlanCodec.goal(plan);
        return new PlanViews.Plan(
                plan.isAiAssisted(),
                new PlanViews.Goal(plan.getGoalTitle(), plan.getGoalSummary(), goal.reason(), goal.reasonText(),
                        goal.picks(), goal.effectiveGoals(), goal.ownWords(), goal.deadline(), goal.focus(),
                        PlanCodec.lines(plan.getWorkOn())),
                new PlanViews.Level(plan.getPlacedLevel(), plan.getTargetLevel(),
                        PlanCodec.skills(plan.getKnownSkills()), PlanCodec.skills(plan.getNewSkills())),
                weeks(plan, live.today()),
                progress(student, plan, steps, live.today()),
                PlanViews.PaceBand.of(plan.getPace()),
                steps,
                PlanCodec.lines(plan.getOutcomes()),
                milestone(plan, steps),
                thisWeek(student, plan, live),
                // Lessons as the path counts them: a bonus chest or a passed checkpoint is not a lesson.
                new PlanViews.Kept(
                        (int) completionRepository.countByStudentIdAndLessonKinds(
                                student.getId(), LearnLessonKind.COUNTED_AS_LESSONS),
                        (int) practiceCompletionRepository.countFinishedSets(student.getId())),
                live.today(),
                pickedClass(plan, steps));
    }

    /**
     * The optional class the learner picked to take first, while the pick still means something: the class is one of
     * the plan's optional classes (not joined, still in the catalogue) and there is another to choose from.
     */
    private static Long pickedClass(LearningPlan plan, List<PlanViews.Step> steps) {
        Long picked = plan.getPickedClassId();
        if (picked == null) {
            return null;
        }
        List<PlanViews.Step> optional = steps.stream()
                .filter(step -> step.status() == PlanViews.StepStatus.OPTIONAL)
                .toList();
        return optional.size() > 1 && optional.stream().anyMatch(step -> picked.equals(step.refId())) ? picked : null;
    }

    // At a glance

    PlanViews.Summary summary(StudentProfile student, LearningPlan plan, Live live) {
        List<PlanViews.Step> steps = steps(live);
        PlanViews.Weeks weeks = weeks(plan, live.today());
        PlanViews.Progress progress = progress(student, plan, steps, live.today());
        // The class the learner picked comes first (S3.19/S3.20); the others keep the plan's order.
        Long picked = plan.getPickedClassId();
        PlanViews.OptionalClass optionalClass = steps.stream()
                .filter(step -> step.status() == PlanViews.StepStatus.OPTIONAL)
                .min(Comparator.comparing(step -> !Objects.equals(step.refId(), picked)))
                .map(step -> new PlanViews.OptionalClass(step.title(), step.weekFrom(), step.bestAfterUnit()))
                .orElse(null);
        PlanViews.Milestone milestone = milestone(plan, steps);
        return new PlanViews.Summary(
                plan.getGoalTitle(),
                plan.getReason(),
                plan.getDeadline(),
                milestone == null ? null : milestone.date(),
                weeks.current(),
                weeks.total(),
                progress.percent(),
                progress.status(),
                progress.daysBehind(),
                plan.getPlacedLevel(),
                plan.getTargetLevel(),
                optionalClass,
                practice(student, plan),
                live.today());
    }

    // Steps

    /**
     * A level checkpoint that is the path's next stop: every lesson of its level is done (so are its units), and the
     * path has no next lesson (a checkpoint is not one). It is the gate of its level: the next level opens once it is
     * passed, so it is the next step of whatever comes after it.
     */
    record OpenCheckpoint(LearnPathResponse.Level level, LearnPathResponse.Checkpoint checkpoint) {

        /** About a minute a question, five at least. */
        int minutes() {
            return Math.max(5, checkpoint.questionCount());
        }

        /** The last unit of the level, which the checkpoint follows on the path. */
        LearnPathResponse.Unit lastUnit() {
            return level.units().get(level.units().size() - 1);
        }
    }

    static Optional<OpenCheckpoint> openCheckpoint(LearnPathResponse path) {
        if (path.nextLesson() != null) {
            return Optional.empty();
        }
        return path.levels().stream()
                .filter(level -> level.checkpoint() != null && level.checkpoint().state() == StopState.AVAILABLE
                        && !level.units().isEmpty())
                .findFirst()
                .map(level -> new OpenCheckpoint(level, level.checkpoint()));
    }

    /**
     * Whether the plan leads through an open checkpoint: one of its Learn units (still on the path) is not done yet.
     * Every unit before an open checkpoint is done, so such a unit lies behind it, and the plan's current step offers
     * the checkpoint first ({@link #steps}).
     */
    static boolean hasUnitNotDone(List<LearningPlanStep> stored, PlanCatalogue.Catalogue catalogue) {
        return stored.stream()
                .filter(step -> step.getStepKind() == PlanStepKind.LEARN_UNIT)
                .map(step -> catalogue.unit(step.getRefId()))
                .flatMap(Optional::stream)
                .anyMatch(unit -> unit.state() != UnitState.DONE);
    }

    private static List<PlanViews.Step> steps(Live live) {
        Optional<OpenCheckpoint> checkpoint = openCheckpoint(live.catalogue().path());
        List<PlanViews.Step> steps = new ArrayList<>();
        // The status of the next unit not done; null until the current unit (the first not done) is found. A started
        // current unit is in progress and the unit after it next; otherwise the current unit is next itself.
        PlanViews.StepStatus following = null;
        for (LearningPlanStep step : live.planSteps()) {
            List<PlanGoal> goals = PlanCodec.goals(step);
            if (step.getStepKind() == PlanStepKind.CLASS) {
                steps.add(classStep(step, goals, live));
                continue;
            }
            Optional<PlanCatalogue.Unit> found = live.catalogue().unit(step.getRefId());
            if (found.isEmpty()) {
                steps.add(unavailable(step, goals));
                continue;
            }
            PlanCatalogue.Unit unit = found.get();
            PlanViews.StepStatus status;
            boolean current = false;
            if (unit.state() == UnitState.DONE) {
                status = PlanViews.StepStatus.DONE;
            } else if (following == null) {
                current = true;
                boolean started = unit.completedCount() > 0;
                status = started ? PlanViews.StepStatus.IN_PROGRESS : PlanViews.StepStatus.NEXT;
                following = started ? PlanViews.StepStatus.NEXT : PlanViews.StepStatus.LATER;
            } else {
                status = following;
                following = PlanViews.StepStatus.LATER;
            }
            steps.add(new PlanViews.Step(step.getPosition(), step.getStepKind(), unit.id(), unit.title(),
                    unit.titleZh(), unit.number(), unit.lessonCount(), unit.completedCount(),
                    percent(unit.completedCount(), unit.lessonCount()), status, false, step.isQuickReview(),
                    step.getWeekFrom(), step.getWeekTo(), null, step.getReason(), goals,
                    nextLesson(unit, current ? checkpoint : Optional.empty())));
        }
        return steps;
    }

    /**
     * A joined class with its progress and next lesson (as My classes shows it), an optional class still in the
     * catalogue, or one that left it.
     */
    private static PlanViews.Step classStep(LearningPlanStep step, List<PlanGoal> goals, Live live) {
        ClassViews.MyClassCard joined = live.joined().get(step.getRefId());
        if (joined == null) {
            return live.catalogue().classOption(step.getRefId())
                    .map(option -> new PlanViews.Step(step.getPosition(), step.getStepKind(), step.getRefId(),
                            option.title(), null, null, 0, 0, 0, PlanViews.StepStatus.OPTIONAL, false,
                            false, step.getWeekFrom(), step.getWeekTo(), option.bestAfterUnit(), step.getReason(),
                            goals, null))
                    .orElseGet(() -> unavailable(step, goals));
        }
        int lessons = joined.summary().lessonCount();
        PlanViews.StepStatus status = lessons > 0 && joined.completedLessons() >= lessons
                ? PlanViews.StepStatus.DONE
                : PlanViews.StepStatus.IN_MY_CLASSES;
        ClassViews.NextStep next = joined.nextStep();
        return new PlanViews.Step(step.getPosition(), step.getStepKind(), step.getRefId(),
                joined.summary().title(), null, null, lessons, joined.completedLessons(),
                joined.progressPercent(), status, true, false, step.getWeekFrom(), step.getWeekTo(),
                joined.summary().bestAfterUnit(), step.getReason(), goals,
                next == null ? null : new PlanViews.NextLesson(next.lessonId(), next.lessonTitle(),
                        LearnLessonKind.LESSON));
    }

    /** A unit or class that left the catalogue since the plan was built. */
    private static PlanViews.Step unavailable(LearningPlanStep step, List<PlanGoal> goals) {
        return new PlanViews.Step(step.getPosition(), step.getStepKind(), step.getRefId(), step.getTitle(), null, null,
                0, 0, 0, PlanViews.StepStatus.UNAVAILABLE, false, step.isQuickReview(), step.getWeekFrom(),
                step.getWeekTo(), null, step.getReason(), goals, null);
    }

    /**
     * The first open lesson in road order, or the open level checkpoint for the plan's current unit behind it.
     */
    private static PlanViews.NextLesson nextLesson(
            PlanCatalogue.Unit unit, Optional<OpenCheckpoint> checkpoint) {
        return unit.lessons().stream()
                .filter(stop -> stop.state() == StopState.AVAILABLE && stop.kind() != LearnLessonKind.BONUS)
                .findFirst()
                .map(stop -> new PlanViews.NextLesson(stop.id(), stop.title(), stop.kind()))
                .or(() -> checkpoint.map(open -> new PlanViews.NextLesson(
                        open.checkpoint().id(), open.checkpoint().title(), LearnLessonKind.CHECKPOINT)))
                .orElse(null);
    }

    // Progress

    private static PlanViews.Weeks weeks(LearningPlan plan, LocalDate today) {
        return new PlanViews.Weeks(PlanComposer.weekOf(plan.getStartsOn(), today, plan.getTotalWeeks()),
                plan.getTotalWeeks(), plan.getContentWeeks());
    }

    /** The first day of the 7-day plan week {@code today} is in; these go on after the plan's last week. */
    private static LocalDate weekStart(LearningPlan plan, LocalDate today) {
        long elapsed = Math.max(0, ChronoUnit.DAYS.between(plan.getStartsOn(), today));
        return plan.getStartsOn().plusWeeks(elapsed / 7);
    }

    private PlanViews.Progress progress(StudentProfile student, LearningPlan plan, List<PlanViews.Step> steps, LocalDate today) {
        int done = 0;
        int total = 0;
        for (PlanViews.Step step : steps) {
            if (step.kind() == PlanStepKind.LEARN_UNIT || step.joined()) {
                // A finished unit counts in full: its bonus chest is optional, so nothing waits for it.
                done += step.status() == PlanViews.StepStatus.DONE ? step.lessonCount() : step.completedCount();
                total += step.lessonCount();
            }
        }
        int percent = percent(done, total);
        LocalDate weekStart = weekStart(plan, today);
        Set<LocalDate> goalDays = xpService.goalDays(student, weekStart, today);
        int daysThisWeek = goalDays.size();
        // The learner gives the plan time each day: a day of this plan week before today without the daily goal is a
        // study day behind (today can still be made up).
        int daysBehind = weekStart.isBefore(today)
                ? (int) weekStart.datesUntil(today).filter(day -> !goalDays.contains(day)).count()
                : 0;
        long elapsed = Math.max(0, ChronoUnit.DAYS.between(plan.getStartsOn(), today));
        int expected = (int) Math.min(100, elapsed * 100 / (plan.getTotalWeeks() * 7L));
        PlanViews.PlanStatus status;
        if (total > 0 && done >= total) {
            status = PlanViews.PlanStatus.COMPLETE;
        } else if (daysBehind > 0) {
            status = PlanViews.PlanStatus.BEHIND;
        } else if (elapsed == 0 && daysThisWeek == 0) {
            status = PlanViews.PlanStatus.STARTS_TODAY;
        } else {
            status = total == 0 || percent + ON_TRACK_TOLERANCE >= expected
                    ? PlanViews.PlanStatus.ON_TRACK
                    : PlanViews.PlanStatus.BEHIND;
        }
        return new PlanViews.Progress(percent, status, daysThisWeek, daysBehind);
    }

    private static PlanViews.Milestone milestone(LearningPlan plan, List<PlanViews.Step> steps) {
        if (plan.getMilestoneWeek() == null) {
            return null;
        }
        int week = plan.getMilestoneWeek();
        // A goal date too close for the path (it falls in the milestone's last week) is the milestone's date; otherwise
        // the milestone ends its week.
        LocalDate date = plan.getDeadline() != null && !plan.getDeadline().isAfter(plan.getStartsOn().plusWeeks(week))
                ? plan.getDeadline()
                : plan.getStartsOn().plusWeeks(week).minusDays(1);
        List<Integer> positions = steps.stream()
                .filter(step -> step.kind() == PlanStepKind.LEARN_UNIT && step.weekTo() <= week)
                .map(PlanViews.Step::position)
                .toList();
        if (positions.isEmpty()) {
            positions = steps.stream().filter(step -> step.kind() == PlanStepKind.LEARN_UNIT).limit(1)
                    .map(PlanViews.Step::position).toList();
        }
        return new PlanViews.Milestone(date, plan.getMilestoneText(), positions);
    }

    // This week

    private List<PlanViews.WeekTask> thisWeek(StudentProfile student, LearningPlan plan, Live live) {
        LocalDateTime since = clock.startOf(weekStart(plan, live.today()));
        List<PlanViews.WeekTask> tasks = new ArrayList<>();
        // Lessons and reviews, as the plan's progress counts them: an opened bonus chest or a passed checkpoint is
        // not one.
        completionRepository.findRecentCompletions(student.getId(), since, LearnLessonKind.COUNTED_AS_LESSONS,
                Pageable.ofSize(DONE_TASKS)).stream()
                .sorted(Comparator.comparing(LearnLessonCompletion::getFirstCompletedAt))
                .forEach(completion -> tasks.add(new PlanViews.WeekTask.LearnDone(
                        completion.getLesson().getTitle(), clock.dayOf(completion.getFirstCompletedAt()))));
        LearnPathResponse.NextLesson next = live.catalogue().path().nextLesson();
        if (next != null) {
            tasks.add(new PlanViews.WeekTask.LearnNext(next.title(), next.minutes(), next.id(), next.kind()));
        } else {
            openCheckpoint(live.catalogue().path()).ifPresent(open -> tasks.add(new PlanViews.WeekTask.LearnNext(
                    open.checkpoint().title(), open.minutes(), open.checkpoint().id(), LearnLessonKind.CHECKPOINT)));
        }
        PlanViews.PracticeSuggestion practice = practice(student, plan);
        if (practice != null) {
            tasks.add(new PlanViews.WeekTask.Practice(practice.title(), practice.type(), practice.setId()));
        }
        boolean talked = aiUsageLogRepository.existsByUserIdAndFeatureKeyAndStatusAndCreatedAtGreaterThanEqual(
                student.getUser().getId(), AiFeatureKey.XIAOMEI_CONVERSATION, AiRequestStatus.SUCCESS, since);
        tasks.add(new PlanViews.WeekTask.Xiaomei(talked));
        return tasks;
    }

    /** The first Practice set of the student's focus skills not done yet (none for speaking). */
    private PlanViews.PracticeSuggestion practice(StudentProfile student, LearningPlan plan) {
        for (PlanFocus focus : PlanCodec.focus(plan)) {
            PracticeType type = focus.practiceType();
            if (type == null) {
                continue;
            }
            Optional<PlanViews.PracticeSuggestion> found = practiceSets.firstUnfinished(type, student.getId())
                    .map(set -> new PlanViews.PracticeSuggestion(type, set.id(), set.title()));
            if (found.isPresent()) {
                return found.get();
            }
        }
        return null;
    }

    private static int percent(int done, int total) {
        return Math.min(100, RewardRules.accuracyPercent(done, total));
    }
}

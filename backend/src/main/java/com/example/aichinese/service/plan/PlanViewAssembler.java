package com.example.aichinese.service.plan;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.aichinese.dto.classes.ClassViews;
import com.example.aichinese.dto.classes.LiveSessionViews;
import com.example.aichinese.dto.learn.LearnPathResponse;
import com.example.aichinese.dto.learn.PracticeSetsResponse;
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
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.classes.StudentClassService;
import com.example.aichinese.service.learn.LearnClock;
import com.example.aichinese.service.learn.LearnPathModel.StopState;
import com.example.aichinese.service.learn.LearnPathModel.UnitState;
import com.example.aichinese.service.learn.PracticeService;
import com.example.aichinese.service.learn.XpService;

/**
 * Turns a stored plan into what the pages show, with live progress: each step's state from the Learn path and the
 * joined classes, the plan's percentage, the plan week, the days of this week with the daily goal reached, this
 * week's to-dos, goal coverage and the first milestone.
 */
@Component
public class PlanViewAssembler {

    /** Progress may trail the time passed by this many points and still be "on track". */
    static final int ON_TRACK_TOLERANCE = 10;
    private static final int DONE_TASKS = 2;

    private final LearningPlanStepRepository stepRepository;
    private final PlanCatalogue planCatalogue;
    private final StudentClassService studentClassService;
    private final PracticeService practiceService;
    private final XpService xpService;
    private final LearnLessonCompletionRepository completionRepository;
    private final PracticeCompletionRepository practiceCompletionRepository;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final LearnClock clock;

    public PlanViewAssembler(
            LearningPlanStepRepository stepRepository,
            PlanCatalogue planCatalogue,
            StudentClassService studentClassService,
            PracticeService practiceService,
            XpService xpService,
            LearnLessonCompletionRepository completionRepository,
            PracticeCompletionRepository practiceCompletionRepository,
            AiUsageLogRepository aiUsageLogRepository,
            LearnClock clock) {
        this.stepRepository = stepRepository;
        this.planCatalogue = planCatalogue;
        this.studentClassService = studentClassService;
        this.practiceService = practiceService;
        this.xpService = xpService;
        this.completionRepository = completionRepository;
        this.practiceCompletionRepository = practiceCompletionRepository;
        this.aiUsageLogRepository = aiUsageLogRepository;
        this.clock = clock;
    }

    /** The student's catalogue and joined classes (with progress and today's booked session), read once per request. */
    public record Live(
            PlanCatalogue.Catalogue catalogue,
            Map<Long, ClassViews.MyClassCard> joined,
            LiveSessionViews.SessionBrief liveToday,
            LocalDate today) {
    }

    public Live live(StudentProfile student, SecurityUserDetails currentUser) {
        ClassViews.MyClasses classes = studentClassService.myClasses(currentUser);
        Map<Long, ClassViews.MyClassCard> joined = new LinkedHashMap<>();
        classes.classes().forEach(card -> joined.put(card.summary().id(), card));
        return new Live(planCatalogue.of(student), joined, classes.liveToday(), clock.today());
    }

    // ===== Whole plan =====

    public PlanViews.Plan plan(StudentProfile student, LearningPlan plan, Live live) {
        List<LearningPlanStep> stored = stepRepository.findByPlanIdOrderByPositionAsc(plan.getId());
        List<PlanViews.Step> steps = steps(stored, live);
        PlanViews.Weeks weeks = weeks(plan, live.today());
        PlanViews.Progress progress = progress(student, plan, steps, weeks, live.today());
        PlanComposer.Goal goal = PlanCodec.goal(plan);
        return new PlanViews.Plan(
                plan.isAiAssisted(),
                new PlanViews.Goal(plan.getGoalTitle(), plan.getGoalSummary(), plan.getReason(), plan.getReasonText(),
                        PlanCodec.picks(plan), goal.effectiveGoals(), plan.getGoalText(), plan.getDeadline(),
                        PlanCodec.focus(plan), PlanCodec.lines(plan.getWorkOn())),
                level(plan),
                weeks,
                progress,
                new PlanViews.Pace(plan.getPace(), plan.getPace().minutesPerDay(), plan.getPace().daysPerWeek(),
                        student.getDailyGoalXp()),
                steps,
                PlanCodec.lines(plan.getOutcomes()),
                coverage(goal, stored),
                milestone(plan, steps),
                thisWeek(student, plan, weeks, live),
                new PlanViews.Kept(
                        (int) completionRepository.countByStudentId(student.getId()),
                        practiceCompletionRepository.findFinishedSets(student.getId()).size(),
                        live.joined().size()));
    }

    private static PlanViews.Level level(LearningPlan plan) {
        return new PlanViews.Level(plan.getPlacedLevel(), plan.getTargetLevel(), plan.isHeadStart(),
                PlanCodec.skills(plan.getKnownSkills()), PlanCodec.skills(plan.getNewSkills()), plan.getLevelCheckedAt());
    }

    // ===== At a glance =====

    public PlanViews.Summary summary(StudentProfile student, LearningPlan plan, Live live) {
        List<LearningPlanStep> stored = stepRepository.findByPlanIdOrderByPositionAsc(plan.getId());
        List<PlanViews.Step> steps = steps(stored, live);
        PlanViews.Weeks weeks = weeks(plan, live.today());
        PlanViews.Progress progress = progress(student, plan, steps, weeks, live.today());
        PlanViews.GoalUnit goalUnit = steps.stream()
                .filter(step -> step.kind() == PlanStepKind.LEARN_UNIT && step.unitNumber() != null)
                .reduce((first, second) -> second)
                .map(step -> new PlanViews.GoalUnit(step.unitNumber(), step.title(), step.titleZh()))
                .orElse(null);
        List<PlanViews.ClassStep> classSteps = steps.stream()
                .filter(step -> step.kind() == PlanStepKind.CLASS)
                .map(step -> new PlanViews.ClassStep(step.refId(), step.position()))
                .toList();
        PlanViews.OptionalClass optionalClass = steps.stream()
                .filter(step -> step.status() == PlanViews.StepStatus.OPTIONAL)
                .findFirst()
                .map(step -> new PlanViews.OptionalClass(step.refId(), step.title(), step.weekFrom()))
                .orElse(null);
        PlanViews.FirstStep firstStep = steps.stream()
                .filter(step -> step.kind() == PlanStepKind.LEARN_UNIT && step.status() != PlanViews.StepStatus.DONE
                        && step.unitNumber() != null)
                .findFirst()
                .map(step -> new PlanViews.FirstStep(step.title(), Math.max(0, step.lessonCount() - step.completedCount()),
                        live.catalogue().unit(step.refId()).map(PlanCatalogue.Unit::remainingMinutes).orElse(0),
                        step.quickReview()))
                .orElse(null);
        return new PlanViews.Summary(
                plan.getGoalTitle(),
                plan.getReason(),
                plan.getDeadline(),
                weeks.current(),
                weeks.total(),
                progress.percent(),
                progress.status(),
                plan.getPlacedLevel(),
                plan.getTargetLevel(),
                goalUnit,
                classSteps,
                optionalClass,
                firstStep,
                practice(student, plan));
    }

    // ===== Steps =====

    List<PlanViews.Step> steps(List<LearningPlanStep> stored, Live live) {
        Long nextLessonId = live.catalogue().path().nextLesson() == null ? null : live.catalogue().path().nextLesson().id();
        List<PlanViews.Step> steps = new ArrayList<>();
        boolean currentSeen = false;
        boolean currentStarted = false;
        boolean nextGiven = false;
        for (LearningPlanStep step : stored) {
            List<PlanGoal> goals = PlanCodec.goals(step);
            if (step.getStepKind() == PlanStepKind.LEARN_UNIT) {
                Optional<PlanCatalogue.Unit> found = live.catalogue().unit(step.getRefId());
                if (found.isEmpty()) {
                    steps.add(new PlanViews.Step(step.getPosition(), step.getStepKind(), step.getRefId(), step.getTitle(),
                            null, null, null, null, 0, 0, 0, PlanViews.StepStatus.UNAVAILABLE, false,
                            step.isQuickReview(), step.getWeekFrom(), step.getWeekTo(), step.getReason(), goals, null,
                            null, null));
                    continue;
                }
                PlanCatalogue.Unit unit = found.get();
                PlanViews.StepStatus status;
                if (unit.state() == UnitState.DONE) {
                    status = PlanViews.StepStatus.DONE;
                } else if (!currentSeen) {
                    currentSeen = true;
                    currentStarted = unit.completedCount() > 0;
                    status = currentStarted ? PlanViews.StepStatus.IN_PROGRESS : PlanViews.StepStatus.NEXT;
                } else if (currentStarted && !nextGiven) {
                    nextGiven = true;
                    status = PlanViews.StepStatus.NEXT;
                } else {
                    status = PlanViews.StepStatus.LATER;
                }
                steps.add(new PlanViews.Step(step.getPosition(), step.getStepKind(), unit.id(), unit.title(),
                        unit.titleZh(), unit.number(), unit.hskLevel(), unit.iconKey(), unit.lessonCount(),
                        unit.completedCount(), percent(unit.completedCount(), unit.lessonCount()), status, false,
                        step.isQuickReview(), step.getWeekFrom(), step.getWeekTo(), step.getReason(), goals, null,
                        nextLesson(unit, nextLessonId), null));
            } else {
                ClassViews.MyClassCard joined = live.joined().get(step.getRefId());
                Optional<PlanCatalogue.ClassOption> option = live.catalogue().classOption(step.getRefId());
                if (joined != null) {
                    int lessons = joined.summary().lessonCount();
                    PlanViews.StepStatus status = lessons > 0 && joined.completedLessons() >= lessons
                            ? PlanViews.StepStatus.DONE
                            : PlanViews.StepStatus.IN_MY_CLASSES;
                    steps.add(new PlanViews.Step(step.getPosition(), step.getStepKind(), step.getRefId(),
                            joined.summary().title(), null, null, null, null, lessons, joined.completedLessons(),
                            joined.progressPercent(), status, true, false, step.getWeekFrom(), step.getWeekTo(),
                            step.getReason(), goals, joined.summary().teacher() == null ? null : joined.summary().teacher().name(),
                            null, joined.nextLiveSession() == null ? null : joined.nextLiveSession().startsAt()));
                } else if (option.isPresent()) {
                    steps.add(new PlanViews.Step(step.getPosition(), step.getStepKind(), step.getRefId(),
                            option.get().title(), null, null, null, null, 0, 0, 0, PlanViews.StepStatus.OPTIONAL, false,
                            false, step.getWeekFrom(), step.getWeekTo(), step.getReason(), goals,
                            option.get().teacherName(), null, null));
                } else {
                    steps.add(new PlanViews.Step(step.getPosition(), step.getStepKind(), step.getRefId(), step.getTitle(),
                            null, null, null, null, 0, 0, 0, PlanViews.StepStatus.UNAVAILABLE, false, false,
                            step.getWeekFrom(), step.getWeekTo(), step.getReason(), goals, null, null, null));
                }
            }
        }
        return steps;
    }

    private static PlanViews.NextLesson nextLesson(PlanCatalogue.Unit unit, Long nextLessonId) {
        return unit.lessons().stream()
                .filter(stop -> stop.id().equals(nextLessonId))
                .findFirst()
                .or(() -> unit.lessons().stream()
                        .filter(stop -> stop.state() == StopState.AVAILABLE && stop.kind() != LearnLessonKind.BONUS)
                        .findFirst())
                .map(stop -> new PlanViews.NextLesson(stop.id(), stop.title()))
                .orElse(null);
    }

    // ===== Progress =====

    static PlanViews.Weeks weeks(LearningPlan plan, LocalDate today) {
        long days = Math.max(0, ChronoUnit.DAYS.between(plan.getStartsOn(), today));
        int current = (int) Math.min(plan.getTotalWeeks(), days / 7 + 1);
        return new PlanViews.Weeks(Math.max(1, current), plan.getTotalWeeks(), plan.getContentWeeks());
    }

    private PlanViews.Progress progress(
            StudentProfile student, LearningPlan plan, List<PlanViews.Step> steps, PlanViews.Weeks weeks, LocalDate today) {
        int done = 0;
        int total = 0;
        for (PlanViews.Step step : steps) {
            if (step.kind() == PlanStepKind.LEARN_UNIT || step.joined()) {
                done += step.completedCount();
                total += step.lessonCount();
            }
        }
        int percent = percent(done, total);
        LocalDate weekStart = plan.getStartsOn().plusWeeks(weeks.current() - 1L);
        int daysThisWeek = weekStart.isAfter(today) ? 0 : xpService.goalDays(student, weekStart, today).size();
        long elapsed = Math.max(0, ChronoUnit.DAYS.between(plan.getStartsOn(), today));
        int expected = (int) Math.min(100, elapsed * 100 / Math.max(1, weeks.total() * 7L));
        PlanViews.PlanStatus status;
        if (total > 0 && done >= total) {
            status = PlanViews.PlanStatus.COMPLETE;
        } else if (weeks.current() == 1 && daysThisWeek == 0) {
            status = PlanViews.PlanStatus.STARTS_TODAY;
        } else {
            status = percent + ON_TRACK_TOLERANCE >= expected ? PlanViews.PlanStatus.ON_TRACK : PlanViews.PlanStatus.BEHIND;
        }
        return new PlanViews.Progress(percent, status, daysThisWeek, plan.getPace().daysPerWeek());
    }

    private static List<PlanViews.CoverageItem> coverage(PlanComposer.Goal goal, List<LearningPlanStep> stored) {
        List<PlanComposer.StepGoals> stepGoals = stored.stream()
                .map(step -> new PlanComposer.StepGoals(step.getStepKind(), step.getRefId(), step.getTitle(),
                        Set.copyOf(PlanCodec.goals(step))))
                .toList();
        return PlanComposer.coverage(goal.effectiveGoals(), stepGoals).stream()
                .map(item -> new PlanViews.CoverageItem(item.goal(), item.learn(), item.classId(), item.classTitle()))
                .toList();
    }

    private static PlanViews.Milestone milestone(LearningPlan plan, List<PlanViews.Step> steps) {
        if (!plan.isAmbitious() || plan.getMilestoneWeek() == null) {
            return null;
        }
        int week = plan.getMilestoneWeek();
        LocalDate date = plan.getDeadline() != null ? plan.getDeadline() : plan.getStartsOn().plusWeeks(week).minusDays(1);
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

    // ===== This week =====

    private List<PlanViews.WeekTask> thisWeek(StudentProfile student, LearningPlan plan, PlanViews.Weeks weeks, Live live) {
        LocalDate weekStart = plan.getStartsOn().plusWeeks(weeks.current() - 1L);
        LocalDateTime since = clock.startOf(weekStart);
        List<PlanViews.WeekTask> tasks = new ArrayList<>();
        completionRepository.findByStudentIdWithLesson(student.getId()).stream()
                .filter(completion -> !completion.getFirstCompletedAt().isBefore(since))
                .filter(completion -> completion.getLesson().getLessonKind() != LearnLessonKind.CHECKPOINT)
                .sorted(Comparator.comparing(LearnLessonCompletion::getFirstCompletedAt).reversed())
                .limit(DONE_TASKS)
                .sorted(Comparator.comparing(LearnLessonCompletion::getFirstCompletedAt))
                .forEach(completion -> tasks.add(new PlanViews.WeekTask(PlanViews.TaskKind.LEARN_DONE,
                        completion.getLesson().getTitle(), true, clock.dayOf(completion.getFirstCompletedAt()),
                        completion.getLesson().getMinutes(), completion.getLesson().getId(), null, null)));
        LearnPathResponse.NextLesson next = live.catalogue().path().nextLesson();
        if (next != null) {
            tasks.add(new PlanViews.WeekTask(PlanViews.TaskKind.LEARN_NEXT, next.title(), false, null, next.minutes(),
                    next.id(), null, null));
        }
        PlanViews.PracticeSuggestion practice = practice(student, plan);
        if (practice != null) {
            tasks.add(new PlanViews.WeekTask(PlanViews.TaskKind.PRACTICE, practice.title(), false, null, null, null,
                    practice.type(), practice.setId()));
        }
        boolean talked = aiUsageLogRepository.countByUserIdAndFeatureKeyAndStatusAndCreatedAtGreaterThanEqual(
                student.getUser().getId(), AiFeatureKey.XIAOMEI_CONVERSATION, AiRequestStatus.SUCCESS.name(), since) > 0;
        tasks.add(new PlanViews.WeekTask(PlanViews.TaskKind.XIAOMEI, null, talked, null, null, null, null, null));
        return tasks;
    }

    /** The first Practice set of the student's focus not done yet (none for speaking or conversation). */
    PlanViews.PracticeSuggestion practice(StudentProfile student, LearningPlan plan) {
        PracticeType type = PlanCodec.focus(plan).stream()
                .map(PlanFocus::practiceType)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (type == null) {
            return null;
        }
        PracticeSetsResponse sets = practiceService.sets(student, type);
        return sets.sets().stream()
                .filter(set -> !set.done())
                .findFirst()
                .map(set -> new PlanViews.PracticeSuggestion(type, set.id(), set.title()))
                .orElse(null);
    }

    static int percent(int done, int total) {
        return total <= 0 ? 0 : (int) Math.min(100, Math.round(done * 100.0 / total));
    }
}

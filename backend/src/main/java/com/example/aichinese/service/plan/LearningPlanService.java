package com.example.aichinese.service.plan;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.learn.LevelCheckNextRequest;
import com.example.aichinese.dto.learn.LevelCheckNextResponse;
import com.example.aichinese.dto.plan.PlanRequests;
import com.example.aichinese.dto.plan.PlanViews;
import com.example.aichinese.entity.ClassEnrolment;
import com.example.aichinese.entity.LearningPlan;
import com.example.aichinese.entity.LearningPlanStep;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.MandarinLevel;
import com.example.aichinese.entity.enums.PlanFocus;
import com.example.aichinese.entity.enums.PlanPace;
import com.example.aichinese.entity.enums.PlanReason;
import com.example.aichinese.entity.enums.PlanSelfLevel;
import com.example.aichinese.entity.enums.PlanSetupState;
import com.example.aichinese.entity.enums.PlanStepKind;
import com.example.aichinese.repository.ClassEnrolmentRepository;
import com.example.aichinese.repository.LearningPlanRepository;
import com.example.aichinese.repository.LearningPlanStepRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.StudentLearningAccessService;
import com.example.aichinese.service.ai.AiUserResolver;
import com.example.aichinese.service.classes.ClassAccessService;
import com.example.aichinese.service.learn.LearnClock;
import com.example.aichinese.service.learn.LevelCheckService;
import com.example.aichinese.service.learn.XpService;
import com.example.aichinese.util.AiInputSanitizer;
import com.example.aichinese.util.TextUtils;

/**
 * The student's learning plan: built at the end of the setup (or later from Home), rebuilt when the goal changes or a
 * retaken level check changes the level, and read by Home, Progress, Learn, Find classes and the teacher. The level
 * always comes from the system's level check; the steps from the real catalogue; weeks and coverage from the rules
 * ({@link PlanComposer}); SparkBot only adds texts and orders classes ({@link PlanAdvisor}). Each rebuild keeps the
 * earlier versions, and progress (read from real completions) is never touched.
 */
@Service
public class LearningPlanService {

    static final int MAX_DEADLINE_DAYS = 730;
    private static final int MAX_REASON_TEXT = 120;
    private static final int MAX_GOAL_TEXT = 200;

    private final StudentLearningAccessService accessService;
    private final StudentProfileRepository studentProfileRepository;
    private final LearningPlanRepository planRepository;
    private final LearningPlanStepRepository stepRepository;
    private final PlanCatalogue planCatalogue;
    private final PlanAdvisor planAdvisor;
    private final PlanViewAssembler assembler;
    private final LevelCheckService levelCheckService;
    private final XpService xpService;
    private final AiUserResolver aiUserResolver;
    private final ClassAccessService classAccessService;
    private final ClassEnrolmentRepository enrolmentRepository;
    private final LearnClock clock;
    private final TransactionTemplate transaction;

    public LearningPlanService(
            StudentLearningAccessService accessService,
            StudentProfileRepository studentProfileRepository,
            LearningPlanRepository planRepository,
            LearningPlanStepRepository stepRepository,
            PlanCatalogue planCatalogue,
            PlanAdvisor planAdvisor,
            PlanViewAssembler assembler,
            LevelCheckService levelCheckService,
            XpService xpService,
            AiUserResolver aiUserResolver,
            ClassAccessService classAccessService,
            ClassEnrolmentRepository enrolmentRepository,
            LearnClock clock,
            PlatformTransactionManager transactionManager) {
        this.accessService = accessService;
        this.studentProfileRepository = studentProfileRepository;
        this.planRepository = planRepository;
        this.stepRepository = stepRepository;
        this.planCatalogue = planCatalogue;
        this.planAdvisor = planAdvisor;
        this.assembler = assembler;
        this.levelCheckService = levelCheckService;
        this.xpService = xpService;
        this.aiUserResolver = aiUserResolver;
        this.classAccessService = classAccessService;
        this.enrolmentRepository = enrolmentRepository;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    // ===== Setup =====

    /** The choices of the setup (and of "Update my goal"). */
    public PlanViews.Options options() {
        return new PlanViews.Options(
                Arrays.stream(PlanReason.values())
                        .map(reason -> new PlanViews.ReasonOption(reason, reason.suggestedGoals()))
                        .toList(),
                Arrays.stream(PlanPace.values())
                        .map(pace -> new PlanViews.PaceOption(pace, pace.minutesPerDay(), pace.daysPerWeek(), pace.dailyGoalXp()))
                        .toList(),
                List.of(PlanFocus.values()),
                MAX_GOAL_TEXT);
    }

    /** "Set up later": Home offers the plan again. A built plan is not affected. */
    @Transactional
    public PlanViews.Overview skip(SecurityUserDetails currentUser) {
        StudentProfile student = accessService.requireStudent(currentUser);
        if (student.getPlanSetupState() == PlanSetupState.NOT_STARTED) {
            student.setPlanSetupState(PlanSetupState.SKIPPED);
            studentProfileRepository.save(student);
        }
        return new PlanViews.Overview(student.getPlanSetupState(), null);
    }

    /**
     * "Build my plan": the level from the finished level check (graded again here, or New to Mandarin when a brand-new
     * student skipped it), the daily goal from the pace, and a new plan version.
     */
    public PlanViews.Plan build(SecurityUserDetails currentUser, PlanRequests.BuildRequest request) {
        StudentProfile student = accessService.requireStudent(currentUser);
        PlanComposer.Goal goal = goal(request.goal());
        PlanComposer.Placement placement = placement(request.levelCheck());
        return planView(currentUser, compose(currentUser, student, goal, placement, request.language(), true));
    }

    /** "Update my goal": the level stays; the path is rebuilt from where the student is now. */
    public PlanViews.Plan updateGoal(SecurityUserDetails currentUser, PlanRequests.UpdateGoalRequest request) {
        StudentProfile student = accessService.requireStudent(currentUser);
        LearningPlan current = requirePlan(student);
        return planView(currentUser, compose(currentUser, student, goal(request.goal()), placementOf(current),
                request.language(), true));
    }

    /**
     * A retaken level check. When the level (or head start) changed, the plan is rebuilt for the same goal; otherwise
     * only the check's details are updated and the path stays the same.
     */
    public PlanViews.RetakeResult retake(SecurityUserDetails currentUser, PlanRequests.RetakeRequest request) {
        StudentProfile student = accessService.requireStudent(currentUser);
        LearningPlan current = requirePlan(student);
        LevelCheckNextResponse.Result result = levelCheckService.evaluate(
                new LevelCheckNextRequest(request.startLevel(), request.answers()));
        boolean changed = result.level() != current.getPlacedLevel() || result.headStart() != current.isHeadStart();
        if (changed) {
            PlanComposer.Placement placement = new PlanComposer.Placement(current.getSelfLevel(), result.level(),
                    result.headStart(), result.knownSkills(), result.newSkills(), result.answeredCount(), true);
            compose(currentUser, student, PlanCodec.goal(current), placement, request.language(), false);
        } else {
            transaction.executeWithoutResult(status -> {
                LearningPlan plan = planRepository.findById(current.getId()).orElseThrow();
                plan.setKnownSkills(PlanCodec.names(result.knownSkills()));
                plan.setNewSkills(PlanCodec.names(result.newSkills()));
                plan.setLevelCheckedAt(clock.now());
                plan.setLevelCheckAnswered(result.answeredCount());
                planRepository.save(plan);
            });
        }
        return new PlanViews.RetakeResult(result.level(), result.headStart(), result.knownSkills(), result.newSkills(),
                result.answeredCount(), changed);
    }

    // ===== Reading =====

    /** The student's plan, or null without one. */
    @Transactional(readOnly = true)
    public PlanViews.Plan current(SecurityUserDetails currentUser) {
        StudentProfile student = accessService.requireStudent(currentUser);
        return active(student)
                .map(plan -> assembler.plan(student, plan, assembler.live(student, currentUser)))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public PlanViews.Overview overview(SecurityUserDetails currentUser) {
        StudentProfile student = accessService.requireStudent(currentUser);
        return new PlanViews.Overview(student.getPlanSetupState(), summary(student, currentUser).orElse(null));
    }

    /** The plan at a glance (for Home), or empty without a plan. */
    @Transactional(readOnly = true)
    public Optional<PlanViews.Summary> summary(StudentProfile student, SecurityUserDetails currentUser) {
        return active(student).map(plan -> assembler.summary(student, plan, assembler.live(student, currentUser)));
    }

    /**
     * A teacher reads the goal of a student in one of their classes (403 otherwise): the goal title, focus, level and
     * where the teacher's classes are in the plan; never the student's own words or SparkBot's summary.
     */
    @Transactional(readOnly = true)
    public PlanViews.TeacherGoal teacherGoal(SecurityUserDetails currentUser, Long studentId) {
        TeacherProfile teacher = classAccessService.requireTeacher(currentUser);
        List<ClassEnrolment> enrolments = enrolmentRepository.findByCoursePackageTeacherIdAndStudentId(teacher.getId(), studentId);
        if (enrolments.isEmpty()) {
            throw new ResponseStatusException(FORBIDDEN, "This student is not in any of your classes.");
        }
        StudentProfile student = enrolments.get(0).getStudent();
        Optional<LearningPlan> found = active(student);
        if (found.isEmpty()) {
            return null;
        }
        LearningPlan plan = found.get();
        Set<Long> teacherClassIds = enrolments.stream()
                .map(enrolment -> enrolment.getCoursePackage().getId())
                .collect(Collectors.toSet());
        List<LearningPlanStep> steps = stepRepository.findByPlanIdOrderByPositionAsc(plan.getId());
        List<PlanViews.TeacherClassStep> yours = steps.stream()
                .filter(step -> step.getStepKind() == PlanStepKind.CLASS && teacherClassIds.contains(step.getRefId()))
                .map(step -> new PlanViews.TeacherClassStep(step.getRefId(), step.getTitle(), step.getPosition()))
                .toList();
        return new PlanViews.TeacherGoal(plan.getGoalTitle(), plan.getReason(), plan.getDeadline(),
                PlanCodec.focus(plan), plan.getPlacedLevel(), plan.getTargetLevel(), plan.getCreatedAt(), yours,
                steps.size());
    }

    // ===== Building =====

    /** The saved plan as the plan page shows it, read in a new transaction after the save. */
    private PlanViews.Plan planView(SecurityUserDetails currentUser, LearningPlan plan) {
        return transaction.execute(status -> {
            StudentProfile fresh = accessService.requireStudent(currentUser);
            return assembler.plan(fresh, plan, assembler.live(fresh, currentUser));
        });
    }

    /**
     * Composes and saves a new version. The catalogue is read and the plan saved in short transactions; the SparkBot
     * request runs between them, outside any transaction.
     */
    private LearningPlan compose(
            SecurityUserDetails currentUser,
            StudentProfile student,
            PlanComposer.Goal goal,
            PlanComposer.Placement placement,
            String requestedLanguage,
            boolean setDailyGoal) {
        LocalDate today = clock.today();
        PlanCatalogue.Catalogue catalogue = transaction.execute(status -> planCatalogue.of(student));
        PlanComposer.Candidates candidates = PlanComposer.candidates(goal, placement, catalogue);
        User user = aiUserResolver.require(currentUser);
        String language = AiUserResolver.language(user, requestedLanguage);
        Optional<PlanComposer.AiDraft> draft = planAdvisor.draft(user, language, goal, placement, candidates, today);
        PlanComposer.Composed composed = PlanComposer.compose(goal, placement, candidates, draft, today);
        return transaction.execute(status -> save(student.getId(), goal, placement, composed, draft, today, clock.now(),
                setDailyGoal));
    }

    /**
     * A plan built earlier from a known draft, for the demo data: composed by the same rules as of {@code startsOn} and
     * saved as built then. The daily goal is left as it is.
     */
    @Transactional
    public void seed(
            StudentProfile student,
            PlanComposer.Goal goal,
            PlanComposer.Placement placement,
            Optional<PlanComposer.AiDraft> draft,
            LocalDateTime builtAt) {
        LocalDate startsOn = builtAt.toLocalDate();
        PlanComposer.Candidates candidates = PlanComposer.candidates(goal, placement, planCatalogue.of(student));
        PlanComposer.Composed composed = PlanComposer.compose(goal, placement, candidates, draft, startsOn);
        save(student.getId(), goal, placement, composed, draft, startsOn, builtAt, false);
    }

    private LearningPlan save(
            Long studentId,
            PlanComposer.Goal goal,
            PlanComposer.Placement placement,
            PlanComposer.Composed composed,
            Optional<PlanComposer.AiDraft> draft,
            LocalDate today,
            LocalDateTime builtAt,
            boolean setDailyGoal) {
        StudentProfile student = studentProfileRepository.findLockedById(studentId).orElseThrow();
        int version = planRepository.maxVersion(studentId) + 1;
        planRepository.deactivateAll(studentId);
        student = studentProfileRepository.findById(studentId).orElseThrow();

        LearningPlan plan = new LearningPlan();
        plan.setStudent(student);
        plan.setVersion(version);
        plan.setActive(true);
        plan.setReason(goal.reason());
        plan.setReasonText(goal.reasonText());
        plan.setGoalPicks(PlanCodec.names(goal.picks()));
        plan.setGoalText(goal.ownWords());
        plan.setDeadline(goal.deadline());
        plan.setPace(goal.pace());
        plan.setFocusSkills(PlanCodec.names(goal.focus()));
        plan.setSelfLevel(placement.selfLevel());
        plan.setPlacedLevel(placement.level());
        plan.setHeadStart(placement.headStart());
        plan.setKnownSkills(PlanCodec.names(placement.knownSkills()));
        plan.setNewSkills(PlanCodec.names(placement.newSkills()));
        plan.setLevelCheckedAt(placement.checked() ? builtAt : null);
        plan.setCreatedAt(builtAt);
        plan.setUpdatedAt(builtAt);
        plan.setLevelCheckAnswered(placement.answered());
        plan.setTargetLevel(composed.targetLevel());
        plan.setAiAssisted(draft.isPresent());
        draft.ifPresent(found -> {
            plan.setGoalTitle(found.goalTitle());
            plan.setGoalSummary(found.summary());
            plan.setWorkOn(PlanCodec.lines(found.workOn()));
            plan.setOutcomes(PlanCodec.lines(found.outcomes()));
            plan.setMilestoneText(composed.ambitious() ? found.milestone() : null);
        });
        plan.setAmbitious(composed.ambitious());
        plan.setMilestoneWeek(composed.milestoneWeek());
        plan.setStartsOn(today);
        plan.setTotalWeeks(composed.totalWeeks());
        plan.setContentWeeks(composed.contentWeeks());
        LearningPlan saved = planRepository.save(plan);

        int position = 1;
        for (PlanComposer.Step composedStep : composed.steps()) {
            LearningPlanStep step = new LearningPlanStep();
            step.setPlan(saved);
            step.setPosition(position++);
            step.setStepKind(composedStep.kind());
            step.setRefId(composedStep.refId());
            step.setTitle(TextUtils.safeTruncate(composedStep.title(), 200));
            step.setReason(composedStep.reason());
            step.setGoalPicks(PlanCodec.names(composedStep.goals()));
            step.setOptionalStep(composedStep.optional());
            step.setQuickReview(composedStep.quickReview());
            step.setWeekFrom(composedStep.weekFrom());
            step.setWeekTo(composedStep.weekTo());
            stepRepository.save(step);
        }

        student.setPlanSetupState(PlanSetupState.COMPLETED);
        studentProfileRepository.save(student);
        if (setDailyGoal && !Objects.equals(student.getDailyGoalXp(), goal.pace().dailyGoalXp())) {
            xpService.changeDailyGoal(student, goal.pace().dailyGoalXp());
        }
        return saved;
    }

    // ===== Inputs =====

    private PlanComposer.Goal goal(PlanRequests.GoalInput input) {
        String reasonText = AiInputSanitizer.sanitize(input.reasonText(), MAX_REASON_TEXT);
        if (input.reason() == PlanReason.OTHER && reasonText == null && AiInputSanitizer.sanitize(input.goalText(), MAX_GOAL_TEXT) == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Tell us your reason in your own words.");
        }
        LocalDate deadline = input.deadline();
        LocalDate today = clock.today();
        if (deadline != null && (!deadline.isAfter(today) || deadline.isAfter(today.plusDays(MAX_DEADLINE_DAYS)))) {
            throw new ResponseStatusException(BAD_REQUEST, "Pick a date in the future, within 2 years.");
        }
        return new PlanComposer.Goal(input.reason(), input.reason() == PlanReason.OTHER ? reasonText : null,
                input.goals(), AiInputSanitizer.sanitize(input.goalText(), MAX_GOAL_TEXT), deadline, input.pace(),
                input.focus());
    }

    private PlanComposer.Placement placement(PlanRequests.LevelCheckInput input) {
        if (input.skipped()) {
            // "Brand new? Skip the check": only someone new to Mandarin may skip it.
            if (input.selfLevel() != PlanSelfLevel.NEW_TO_MANDARIN) {
                throw new ResponseStatusException(BAD_REQUEST, "Finish the level check first.");
            }
            return new PlanComposer.Placement(input.selfLevel(), MandarinLevel.NEW_TO_MANDARIN, false, List.of(),
                    List.of(), 0, false);
        }
        LevelCheckNextResponse.Result result = levelCheckService.evaluate(
                new LevelCheckNextRequest(input.selfLevel().checkStartLevel(), input.answers()));
        return new PlanComposer.Placement(input.selfLevel(), result.level(), result.headStart(), result.knownSkills(),
                result.newSkills(), result.answeredCount(), true);
    }

    private static PlanComposer.Placement placementOf(LearningPlan plan) {
        return new PlanComposer.Placement(plan.getSelfLevel(), plan.getPlacedLevel(), plan.isHeadStart(),
                PlanCodec.skills(plan.getKnownSkills()), PlanCodec.skills(plan.getNewSkills()),
                plan.getLevelCheckAnswered(), plan.getLevelCheckedAt() != null);
    }

    private Optional<LearningPlan> active(StudentProfile student) {
        return planRepository.findFirstByStudentIdAndActiveTrueOrderByVersionDesc(student.getId());
    }

    private LearningPlan requirePlan(StudentProfile student) {
        return active(student).orElseThrow(() -> new ResponseStatusException(CONFLICT, "Build your learning plan first."));
    }
}

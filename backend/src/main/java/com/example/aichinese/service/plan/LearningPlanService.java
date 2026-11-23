package com.example.aichinese.service.plan;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.plan.PlanRequests;
import com.example.aichinese.dto.plan.PlanViews;
import com.example.aichinese.entity.LearningPlan;
import com.example.aichinese.entity.LearningPlanStep;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.MandarinLevel;
import com.example.aichinese.entity.enums.PlanPace;
import com.example.aichinese.entity.enums.PlanReason;
import com.example.aichinese.entity.enums.PlanSelfLevel;
import com.example.aichinese.entity.enums.PlanSetupState;
import com.example.aichinese.entity.enums.PlanStepKind;
import com.example.aichinese.repository.LearningPlanRepository;
import com.example.aichinese.repository.LearningPlanStepRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.StudentLearningAccessService;
import com.example.aichinese.service.account.CurrentAccount;
import com.example.aichinese.service.learn.LearnClock;
import com.example.aichinese.service.learn.LevelCheckService;
import com.example.aichinese.util.AiInputSanitizer;
import com.example.aichinese.util.LanguageCodes;
import com.example.aichinese.util.TextUtils;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

/**
 * The student's learning plan: built at the end of the setup (or later from Home) and saved once the learner starts it,
 * rebuilt when the goal changes or a retaken level check changes the level, and read by Home, Progress and Find
 * classes. The level always comes from the system's level check; the steps from the real catalogue; weeks from the
 * rules ({@link PlanComposer}); SparkBot only adds texts and orders classes ({@link PlanAdvisor}). Each rebuild keeps
 * the earlier versions, and progress (read from real completions) is never touched. The daily goal is an Account
 * setting: no plan changes it.
 *
 * <p>Until "Start my path" ({@link #confirm}), only the setup shows its draft. The setup can change its goal
 * ({@link #updateGoal}), and "Set up later" ({@link #skip}) removes it. A draft older than a day must be rebuilt
 * before it can be started.
 */
@Service
public class LearningPlanService {

    /** How long a built plan can wait for "Start my path". */
    private static final Duration DRAFT_LIFETIME = Duration.ofDays(1);
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
    private final CurrentAccount currentAccount;
    private final LearnClock clock;
    private final TransactionTemplate writeTransaction;
    private final TransactionTemplate readOnlyTransaction;
    private final EntityManager entityManager;

    public LearningPlanService(
            StudentLearningAccessService accessService,
            StudentProfileRepository studentProfileRepository,
            LearningPlanRepository planRepository,
            LearningPlanStepRepository stepRepository,
            PlanCatalogue planCatalogue,
            PlanAdvisor planAdvisor,
            PlanViewAssembler assembler,
            LevelCheckService levelCheckService,
            CurrentAccount currentAccount,
            LearnClock clock,
            PlatformTransactionManager transactionManager,
            EntityManager entityManager) {
        this.accessService = accessService;
        this.studentProfileRepository = studentProfileRepository;
        this.planRepository = planRepository;
        this.stepRepository = stepRepository;
        this.planCatalogue = planCatalogue;
        this.planAdvisor = planAdvisor;
        this.assembler = assembler;
        this.levelCheckService = levelCheckService;
        this.currentAccount = currentAccount;
        this.clock = clock;
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.writeTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
        this.entityManager = entityManager;
    }

    // Setup

    /** The choices of the setup (and of "Update my goal"). */
    public PlanViews.Options options() {
        Map<PlanSelfLevel, MandarinLevel> checkStarts = new EnumMap<>(PlanSelfLevel.class);
        for (PlanSelfLevel level : PlanSelfLevel.values()) {
            checkStarts.put(level, level.checkStart());
        }
        return new PlanViews.Options(
                Arrays.stream(PlanReason.values())
                        .map(reason -> new PlanViews.ReasonOption(reason, reason.suggestedGoals()))
                        .toList(),
                Arrays.stream(PlanPace.values()).map(PlanViews.PaceBand::of).toList(),
                checkStarts,
                MAX_GOAL_TEXT,
                // The day goal() checks a deadline against.
                clock.today());
    }

    /**
     * "Set up later" (also when leaving the setup after the plan was built): a built plan that was not started is
     * removed and Home offers the plan again. A started plan is not affected.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void skip(SecurityUserDetails currentUser) {
        StudentProfile student = lockStudent(currentUser);
        if (student.getPlanSetupState() == PlanSetupState.NOT_STARTED) {
            student.setPlanSetupState(PlanSetupState.SKIPPED);
        }
        deleteDrafts(student.getId());
    }

    /**
     * "Build my plan": the level from the finished level check (graded again here, or New to Mandarin when a brand-new
     * student skipped it; with no published question, the level the check starts from for the student's own answer
     * to "how much Mandarin do you know"), saved as a draft that "Start my path" ({@link #confirm}) makes the plan.
     */
    public PlanViews.Plan build(SecurityUserDetails currentUser, PlanRequests.BuildRequest request) {
        LocalDate today = clock.today();
        PlanComposer.Goal requestedGoal = goal(request.goal(), today);
        ComposeInputs inputs = readOnlyTransaction.execute(status -> {
            User user = currentAccount.require(currentUser);
            StudentProfile student = accessService.requireStudent(currentUser);
            if (active(student).isPresent()) {
                throw planChanged();
            }
            SetupSnapshot setup = SetupSnapshot.of(student, draft(student).orElse(null));
            PlanComposer.Placement placement = placement(request.levelCheck());
            PlanComposer.Candidates candidates = PlanComposer.candidates(
                    requestedGoal, placement, planCatalogue.forNewPlan(student));
            return new ComposeInputs(user, null, setup, requestedGoal, placement,
                    placement.checked() ? LocalDateTime.now() : null, candidates, today);
        });
        return planView(currentUser, compose(currentUser, inputs, request.language(), false));
    }

    /**
     * "Update my goal": the level stays; the path is rebuilt from where the student is now. On My learning plan the
     * plan changes at once; before the learner has a plan, it rebuilds the setup's draft (the result screen's "Edit
     * goal"), which still waits for "Start my path".
     */
    public PlanViews.Plan updateGoal(SecurityUserDetails currentUser, PlanRequests.UpdateGoalRequest request) {
        LocalDate today = clock.today();
        PlanComposer.Goal requestedGoal = goal(request.goal(), today);
        ComposeInputs inputs = readOnlyTransaction.execute(status -> {
            User user = currentAccount.require(currentUser);
            StudentProfile student = accessService.requireStudent(currentUser);
            Optional<LearningPlan> current = active(student);
            LearningPlan plan = current.or(() -> draft(student)).orElseThrow(LearningPlanService::noPlan);
            PlanSnapshot expected = current.map(PlanSnapshot::of).orElse(null);
            SetupSnapshot setup = current.isEmpty() ? SetupSnapshot.of(student, plan) : null;
            PlanComposer.Placement placement = placementOf(plan);
            PlanComposer.Candidates candidates = PlanComposer.candidates(
                    requestedGoal, placement, planCatalogue.forNewPlan(student));
            return new ComposeInputs(user, expected, setup, requestedGoal, placement,
                    plan.getLevelCheckedAt(), candidates, today);
        });
        return planView(currentUser, compose(currentUser, inputs, request.language(), true));
    }

    /**
     * "Start my path": the setup's plan becomes the learner's plan and the setup is done. Starting again (a second
     * click) returns the plan; without a plan, or with one built more than a day ago, there is nothing to start.
     */
    public PlanViews.Plan confirm(SecurityUserDetails currentUser) {
        LearningPlan started = writeTransaction.execute(status -> {
            StudentProfile locked = lockStudent(currentUser);
            Long studentId = locked.getId();
            Optional<LearningPlan> draft = lockedDraft(studentId);
            if (draft.isEmpty()) {
                return lockedActive(studentId).orElseThrow(LearningPlanService::noPlan);
            }
            LocalDate today = clock.today();
            LocalDate draftDeadline = draft.get().getDeadline();
            // Check expired goal date before the 24-hour draft lifetime so overnight expiry always surfaces the date reason.
            if (draftDeadline != null && !draftDeadline.isAfter(today)) {
                throw new ResponseStatusException(CONFLICT, "This plan's goal date has passed. Build it again.");
            }
            if (draft.get().getCreatedAt().isBefore(LocalDateTime.now().minus(DRAFT_LIFETIME))) {
                throw new ResponseStatusException(CONFLICT, "This plan was built more than a day ago. Build it again.");
            }
            locked.setPlanSetupState(PlanSetupState.COMPLETED);
            planRepository.deactivateAll(studentId);
            // deactivateAll clears the persistence context. Reload the draft (still locked by this transaction) with a
            // current read: a plain read uses the snapshot taken before the account lock, which may predate the draft.
            LearningPlan plan = lockedDraft(studentId).orElseThrow();
            if (plan.getStartsOn().isBefore(today)) {
                plan.setStartsOn(today);
                if (plan.getDeadline() != null) {
                    PlanComposer.Schedule rescheduled = PlanComposer.schedule(
                            plan.getContentWeeks(),
                            PlanCodec.goal(plan).weeksToDeadline(today),
                            plan.getMilestoneWeek() != null);
                    plan.setTotalWeeks(rescheduled.totalWeeks());
                    plan.setMilestoneWeek(rescheduled.milestoneWeek());
                }
            }
            plan.setConfirmed(true);
            plan.setActive(true);
            return plan;
        });
        return planView(currentUser, started);
    }

    /**
     * "Pick this first" on the road map: the optional class the learner wants first (null for the plan's own order),
     * kept with the plan so that Home shows the same class (S3.19/S3.20) on any device. It applies to the plan on
     * screen: the learner's plan, or before one was started, the setup's plan.
     */
    public PlanViews.Plan pickClass(SecurityUserDetails currentUser, Long classId) {
        LearningPlan picked = writeTransaction.execute(status -> {
            Long studentId = lockStudent(currentUser).getId();
            LearningPlan plan = lockedActive(studentId)
                    .or(() -> lockedDraft(studentId))
                    .orElseThrow(LearningPlanService::noPlan);
            if (classId != null && !stepRepository.existsByPlanIdAndStepKindAndRefId(
                    plan.getId(), PlanStepKind.CLASS, classId)) {
                throw new ResponseStatusException(BAD_REQUEST, "Pick a class from your plan.");
            }
            plan.setPickedClassId(classId);
            return plan;
        });
        return planView(currentUser, picked);
    }

    /**
     * A retaken level check. When the level (or head start) changed, the plan is rebuilt for the same goal (without
     * its target date once that date has passed, see {@link PlanComposer.Goal#startingOn}); otherwise only the check's
     * details are updated and the path stays the same.
     */
    public PlanViews.RetakeResult retake(SecurityUserDetails currentUser, PlanRequests.RetakeRequest request) {
        record RetakeSnapshot(
                PlanSnapshot expected,
                LevelCheckService.Placement result,
                ComposeInputs rebuildInputs) {
        }
        LocalDateTime checkedAt = LocalDateTime.now();
        RetakeSnapshot snapshot = readOnlyTransaction.execute(status -> {
            User user = currentAccount.require(currentUser);
            StudentProfile student = accessService.requireStudent(currentUser);
            LearningPlan current = active(student).orElseThrow(LearningPlanService::noPlan);
            PlanSnapshot expected = PlanSnapshot.of(current);
            // The retake starts at the plan's level.
            LevelCheckService.Placement result = levelCheckService.evaluate(current.getPlacedLevel(), request.answers());
            boolean samePlacement = result.level() == current.getPlacedLevel() && result.headStart() == current.isHeadStart();
            if (result.answeredCount() == 0 || samePlacement) {
                return new RetakeSnapshot(expected, result, null);
            }
            LocalDate today = clock.today();
            PlanComposer.Goal effectiveGoal = PlanCodec.goal(current).startingOn(today);
            PlanComposer.Placement placement = placementOf(current.getSelfLevel(), result);
            PlanComposer.Candidates candidates = PlanComposer.candidates(
                    effectiveGoal, placement, planCatalogue.forNewPlan(student));
            ComposeInputs rebuildInputs = new ComposeInputs(
                    user, expected, null, effectiveGoal, placement, checkedAt, candidates, today);
            return new RetakeSnapshot(expected, result, rebuildInputs);
        });
        if (snapshot.result().answeredCount() == 0) {
            // No published question, so nothing was asked: the plan and its earlier check stay as they are, skills too.
            return new PlanViews.RetakeResult(snapshot.expected().placement().level(), false);
        }
        boolean rebuild = snapshot.rebuildInputs() != null;
        if (rebuild) {
            compose(currentUser, snapshot.rebuildInputs(), request.language(), true);
        } else {
            writeTransaction.executeWithoutResult(status -> {
                StudentProfile locked = lockStudent(currentUser);
                LearningPlan plan = lockUnchangedPlan(locked, snapshot.expected());
                plan.setKnownSkills(PlanCodec.names(snapshot.result().knownSkills()));
                plan.setNewSkills(PlanCodec.names(snapshot.result().newSkills()));
                plan.setLevelCheckedAt(checkedAt);
                plan.setLevelCheckAnswered(snapshot.result().answeredCount());
            });
        }
        return new PlanViews.RetakeResult(snapshot.result().level(), rebuild);
    }

    // Reading

    /** The student's plan, or null without one. */
    @Transactional(readOnly = true)
    public PlanViews.Plan current(SecurityUserDetails currentUser) {
        StudentProfile student = accessService.requireStudent(currentUser);
        return active(student).map(plan -> assembler.plan(student, plan)).orElse(null);
    }

    @Transactional(readOnly = true)
    public PlanViews.Overview overview(SecurityUserDetails currentUser) {
        StudentProfile student = accessService.requireStudent(currentUser);
        return new PlanViews.Overview(active(student)
                .map(plan -> assembler.summary(student, plan, assembler.live(student, plan)))
                .orElse(null));
    }

    // Building

    /** The saved plan as the plan page shows it, read in a new read-only transaction after the save. */
    private PlanViews.Plan planView(SecurityUserDetails currentUser, LearningPlan plan) {
        return readOnlyTransaction.execute(status -> assembler.plan(accessService.requireStudent(currentUser), plan));
    }

    /** Inputs read before calling SparkBot. Only a setup draft carries the setup state to verify before saving. */
    private record ComposeInputs(
            User user,
            PlanSnapshot expected,
            SetupSnapshot expectedSetup,
            PlanComposer.Goal goal,
            PlanComposer.Placement placement,
            LocalDateTime checkedAt,
            PlanComposer.Candidates candidates,
            LocalDate today) {
    }

    /**
     * Composes and saves a new version: a draft for the setup, else the learner's plan at once. The catalogue
     * and plan state were read before calling SparkBot outside a transaction. The save checks that the plan and any
     * setup being edited still match what was read before the call. A rebuild ({@code keepPick}) keeps the class the
     * learner picked first, as the plan has it when the new version is saved.
     */
    private LearningPlan compose(
            SecurityUserDetails currentUser,
            ComposeInputs inputs,
            String requestedLanguage,
            boolean keepPick) {
        String language = LanguageCodes.forRequest(requestedLanguage, inputs.user().getPreferredLanguage());
        Optional<PlanComposer.AiDraft> aiDraft = planAdvisor.draft(
                inputs.user(), currentUser.getSessionId(), language, inputs.goal(), inputs.placement(),
                inputs.candidates(), inputs.today());
        PlanComposer.Composed composed = PlanComposer.compose(
                inputs.goal(), inputs.placement(), inputs.candidates(), aiDraft, inputs.today());
        return writeTransaction.execute(status -> {
            StudentProfile locked = lockStudent(currentUser);
            LearningPlan current = lockUnchangedPlan(locked, inputs.expected());
            LearningPlan edited = inputs.expectedSetup() == null
                    ? null : requireUnchangedSetup(locked, inputs.expectedSetup());
            LearningPlan rebuilt = current != null ? current : edited;
            Long pick = keepPick && rebuilt != null ? rebuilt.getPickedClassId() : null;
            return save(locked, inputs.goal(), inputs.placement(), composed, aiDraft, inputs.today(),
                    inputs.checkedAt(), LocalDateTime.now(), inputs.expectedSetup() != null, pick);
        });
    }

    /**
     * A plan built earlier from a known SparkBot draft, for the demo data: composed by the same rules as of
     * {@code startsOn} and saved as started then.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void seed(
            StudentProfile student,
            PlanComposer.Goal seeded,
            PlanComposer.Placement placement,
            Optional<PlanComposer.AiDraft> draft,
            LocalDateTime builtAt) {
        // builtAt is server-local; a plan's days are platform days, like everywhere else in the plan.
        LocalDate startsOn = clock.dayOf(builtAt);
        PlanComposer.Goal goal = seeded.startingOn(startsOn);
        PlanComposer.Candidates candidates = PlanComposer.candidates(goal, placement, planCatalogue.forNewPlan(student));
        PlanComposer.Composed composed = PlanComposer.compose(goal, placement, candidates, draft, startsOn);
        StudentProfile locked = studentProfileRepository.findLockedById(student.getId()).orElseThrow();
        save(locked, goal, placement, composed, draft, startsOn, placement.checked() ? builtAt : null, builtAt, false,
                null);
    }

    /**
     * Caller holds the student row lock; public writes also hold and validate the account. Any earlier draft goes; a
     * draft leaves the learner's plan and setup state as they are, a plan replaces the active version. A picked class
     * stays picked while the new version still has it.
     */
    private LearningPlan save(
            StudentProfile student,
            PlanComposer.Goal goal,
            PlanComposer.Placement placement,
            PlanComposer.Composed composed,
            Optional<PlanComposer.AiDraft> aiDraft,
            LocalDate startsOn,
            LocalDateTime checkedAt,
            LocalDateTime builtAt,
            boolean draft,
            Long pickedClassId) {
        Long studentId = student.getId();
        deleteDrafts(studentId);
        int version = planRepository.findLatestVersionForUpdate(studentId).orElse(0) + 1;
        if (!draft) {
            planRepository.deactivateAll(studentId);
        }
        LearningPlan plan = new LearningPlan();
        plan.setStudent(entityManager.getReference(StudentProfile.class, studentId));
        plan.setVersion(version);
        plan.setActive(!draft);
        plan.setConfirmed(!draft);
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
        plan.setLevelCheckedAt(checkedAt);
        plan.setCreatedAt(builtAt);
        plan.setUpdatedAt(builtAt);
        plan.setLevelCheckAnswered(placement.answered());
        plan.setTargetLevel(composed.targetLevel());
        plan.setAiAssisted(aiDraft.isPresent());
        aiDraft.ifPresent(found -> {
            plan.setGoalTitle(found.goalTitle());
            plan.setGoalSummary(found.summary());
            plan.setWorkOn(PlanCodec.lines(found.workOn()));
            plan.setOutcomes(PlanCodec.lines(found.outcomes()));
            plan.setMilestoneText(composed.ambitious() ? found.milestone() : null);
        });
        plan.setMilestoneWeek(composed.milestoneWeek());
        plan.setStartsOn(startsOn);
        plan.setTotalWeeks(composed.totalWeeks());
        plan.setContentWeeks(composed.contentWeeks());
        boolean pickStays = pickedClassId != null && composed.steps().stream()
                .anyMatch(step -> step.kind() == PlanStepKind.CLASS && pickedClassId.equals(step.refId()));
        plan.setPickedClassId(pickStays ? pickedClassId : null);
        LearningPlan saved = planRepository.save(plan);
        saveSteps(saved, composed.steps());

        if (!draft) {
            // The bulk writes cleared the persistence context. Read the locked row again before updating it.
            StudentProfile locked = studentProfileRepository.findLockedById(studentId).orElseThrow();
            locked.setPlanSetupState(PlanSetupState.COMPLETED);
        }
        return saved;
    }

    /** Removes the student's plans that were built but never started, with their steps. */
    private void deleteDrafts(Long studentId) {
        stepRepository.deleteDraftSteps(studentId);
        planRepository.deleteDrafts(studentId);
    }

    private void saveSteps(LearningPlan plan, List<PlanComposer.Step> steps) {
        int position = 1;
        for (PlanComposer.Step composed : steps) {
            LearningPlanStep step = new LearningPlanStep();
            step.setPlan(plan);
            step.setPosition(position++);
            step.setStepKind(composed.kind());
            step.setRefId(composed.refId());
            step.setTitle(TextUtils.safeTruncate(composed.title(), 200));
            step.setReason(composed.reason());
            step.setGoalPicks(PlanCodec.names(composed.goals()));
            step.setQuickReview(composed.quickReview());
            step.setWeekFrom(composed.weekFrom());
            step.setWeekTo(composed.weekTo());
            stepRepository.save(step);
        }
    }

    /**
     * The learner's plan as a request read it before its slow part (SparkBot's draft, or grading a retake): its id and
     * its level check, which a retake that keeps the level changes without making a new version.
     */
    private record PlanSnapshot(Long id, PlanComposer.Placement placement, LocalDateTime checkedAt) {
        static PlanSnapshot of(LearningPlan plan) {
            return new PlanSnapshot(plan.getId(), placementOf(plan), plan.getLevelCheckedAt());
        }
    }

    /** A draft is replaced as a whole. Its id and the setup state identify what the learner was editing. */
    private record SetupSnapshot(PlanSetupState state, Long draftId) {
        static SetupSnapshot of(StudentProfile student, LearningPlan draft) {
            return new SetupSnapshot(student.getPlanSetupState(), draft == null ? null : draft.getId());
        }
    }

    private StudentProfile lockStudent(SecurityUserDetails currentUser) {
        // Account deletion holds User before clearing plans; never wait for AI while holding either row.
        currentAccount.lock(currentUser);
        return accessService.requireLockedStudent(currentUser);
    }

    /** Returns null only for the first setup, when there was and still is no active plan. */
    private LearningPlan lockUnchangedPlan(StudentProfile student, PlanSnapshot expected) {
        LearningPlan current = lockedActive(student.getId()).orElse(null);
        if (current != null) {
            entityManager.refresh(current, LockModeType.PESSIMISTIC_WRITE);
        }
        if (!Objects.equals(expected, current == null ? null : PlanSnapshot.of(current))) {
            throw planChanged();
        }
        return current;
    }

    /** Returns the setup's draft, null when there is none (and was none). */
    private LearningPlan requireUnchangedSetup(StudentProfile student, SetupSnapshot expected) {
        LearningPlan draft = lockedDraft(student.getId()).orElse(null);
        if (!expected.equals(SetupSnapshot.of(student, draft))) {
            throw planChanged();
        }
        return draft;
    }

    private static ResponseStatusException planChanged() {
        return new ResponseStatusException(CONFLICT,
                "Your learning plan changed while this request was running. Reload it and try again.");
    }

    private static ResponseStatusException noPlan() {
        return new ResponseStatusException(CONFLICT, "Build your learning plan first.");
    }

    // Inputs

    private PlanComposer.Goal goal(PlanRequests.GoalInput input, LocalDate today) {
        String reasonText = AiInputSanitizer.sanitize(input.reasonText(), MAX_REASON_TEXT);
        String ownWords = AiInputSanitizer.sanitize(input.goalText(), MAX_GOAL_TEXT);
        if (input.reason() == PlanReason.OTHER && reasonText == null && ownWords == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Tell us your reason in your own words.");
        }
        LocalDate deadline = input.deadline();
        // The calendar offers tomorrow up to two years from today.
        if (deadline != null
                && (!deadline.isAfter(today) || deadline.isAfter(today.plusDays(PlanComposer.MAX_GOAL_DAYS)))) {
            throw new ResponseStatusException(BAD_REQUEST, "Pick a date in the future, within 2 years.");
        }
        return new PlanComposer.Goal(input.reason(), input.reason() == PlanReason.OTHER ? reasonText : null,
                input.goals(), ownWords, deadline, input.pace(), input.focus());
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
        return placementOf(input.selfLevel(),
                levelCheckService.evaluate(input.selfLevel().checkStart(), input.answers()));
    }

    /** Where a finished (graded) level check placed the student. */
    private static PlanComposer.Placement placementOf(PlanSelfLevel selfLevel, LevelCheckService.Placement result) {
        return new PlanComposer.Placement(selfLevel, result.level(), result.headStart(), result.knownSkills(),
                result.newSkills(), result.answeredCount(), true);
    }

    /** The placement a stored plan was built for. */
    private static PlanComposer.Placement placementOf(LearningPlan plan) {
        return new PlanComposer.Placement(plan.getSelfLevel(), plan.getPlacedLevel(), plan.isHeadStart(),
                PlanCodec.skills(plan.getKnownSkills()), PlanCodec.skills(plan.getNewSkills()),
                plan.getLevelCheckAnswered(), plan.getLevelCheckedAt() != null);
    }

    private Optional<LearningPlan> active(StudentProfile student) {
        return planRepository.findFirstByStudentIdAndActiveTrueOrderByVersionDesc(student.getId());
    }

    private Optional<LearningPlan> draft(StudentProfile student) {
        return planRepository.findFirstByStudentIdAndConfirmedFalseOrderByVersionDesc(student.getId());
    }

    private Optional<LearningPlan> lockedActive(Long studentId) {
        return planRepository.findFirstLockedByStudentIdAndActiveTrueOrderByVersionDesc(studentId);
    }

    private Optional<LearningPlan> lockedDraft(Long studentId) {
        return planRepository.findFirstLockedByStudentIdAndConfirmedFalseOrderByVersionDesc(studentId);
    }
}

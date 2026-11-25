package com.example.aichinese.plan;

import static com.example.aichinese.support.ConcurrentTransactions.withAfterMethod;
import static com.example.aichinese.support.ConcurrentTransactions.withEarlierRead;
import static com.example.aichinese.support.ConcurrentTransactions.withPausedOperation;
import static com.example.aichinese.support.LearningApiTestClient.ADMIN_EMAIL;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.aichinese.dto.learn.LevelCheckNextRequest;
import com.example.aichinese.dto.learn.LevelCheckNextRequest.Answer;
import com.example.aichinese.dto.plan.PlanRequests;
import com.example.aichinese.entity.LearningPlan;
import com.example.aichinese.entity.enums.MandarinLevel;
import com.example.aichinese.entity.enums.PlanPace;
import com.example.aichinese.entity.enums.PlanReason;
import com.example.aichinese.entity.enums.PlanSelfLevel;
import com.example.aichinese.entity.enums.PlanSetupState;
import com.example.aichinese.repository.LearningPlanRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuthService;
import com.example.aichinese.service.account.AccountDeletionService;
import com.example.aichinese.service.admin.AdminUserService;
import com.example.aichinese.service.learn.LevelCheckService;
import com.example.aichinese.service.learn.XpService;
import com.example.aichinese.service.plan.LearningPlanService;
import com.example.aichinese.service.plan.PlanAdvisor;
import com.example.aichinese.support.ApiIntegrationTest;

/** Pause the external draft, not a database transaction; exercise the real service read/compute/save boundaries. */
class LearningPlanConcurrencyTests extends ApiIntegrationTest {

    private static final String ORIGINAL = "Visiting my family in Ipoh.";
    private static final String LATEST = "My latest goal is to speak Mandarin at work.";

    @Autowired
    private LearningPlanService service;

    @Autowired
    private LearningPlanRepository plans;

    @Autowired
    private AccountDeletionService deletion;

    @Autowired
    private AuthService auth;

    @Autowired
    private AdminUserService adminUsers;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private XpService xpService;

    @MockitoBean
    private PlanAdvisor advisor;

    @Autowired
    private LevelCheckService levelCheck;

    private SecurityUserDetails principal;
    private Long studentId;

    @BeforeEach
    void registerStudent() throws Exception {
        String email = uniqueEmail("plan-concurrent");
        api.registerStudentAccount(email);
        principal = principalFor(email);
        studentId = studentProfile(email).getId();
    }

    @ParameterizedTest
    @ValueSource(strings = { "deleted", "signed_out", "disabled" })
    void draftCannotSaveAfterTheAccountOrSessionEnds(String action) {
        assertHttpError(() -> duringDraft(this::build, () -> {
            switch (action) {
                case "deleted" -> deletion.delete(principal, principal.getUsername());
                case "signed_out" -> auth.logout(principal);
                case "disabled" -> adminUsers.deactivate(principalFor(ADMIN_EMAIL),
                        principal.getUserId(), "Account no longer available");
                default -> throw new AssertionError(action);
            }
        }), 401);
        assertThat(jdbc.queryForObject("select count(*) from learning_plans where student_id = ?",
                Long.class, studentId)).isZero();
        assertThat(studentProfileRepository.findById(studentId).orElseThrow().getPlanSetupState())
                .isEqualTo(PlanSetupState.NOT_STARTED);
    }

    @ParameterizedTest
    @ValueSource(strings = { "goal", "retake" })
    void delayedDraftCannotReplaceANewerGoal(String action) throws Exception {
        build();
        FinishedCheck correct = finishCheck(true);
        assertHttpError(() -> duringDraft(() -> {
            switch (action) {
                case "goal" -> updateGoal("An earlier goal", PlanPace.HOURS_2_PLUS);
                case "retake" -> retake(correct);
                default -> throw new AssertionError(action);
            }
        }, () -> updateGoal(LATEST, PlanPace.UNDER_30_MIN)), 409);
        LearningPlan saved = current();
        assertThat(saved.getVersion()).isEqualTo(2);
        assertThat(saved.getGoalText()).isEqualTo(LATEST);
        assertThat(saved.getPlacedLevel()).isEqualTo(MandarinLevel.NEW_TO_MANDARIN);
        // No plan changes the daily goal: the new learner's 20 XP stays.
        assertThat(studentProfileRepository.findById(studentId).orElseThrow().getDailyGoalXp()).isEqualTo(20);
    }

    @Test
    void buildIsRefusedOnceAPlanIsAlreadyActive() {
        build();
        assertHttpError(() -> buildDraft(LATEST, PlanPace.UNDER_30_MIN), 409);
        assertThat(plans.findFirstByStudentIdAndConfirmedFalseOrderByVersionDesc(studentId)).isEmpty();
    }

    @Test
    void aFirstDraftCannotReplaceAPlanStartedWhileItWasWaiting() {
        assertHttpError(() -> duringDraft(this::build, () -> build(LATEST, PlanPace.UNDER_30_MIN)), 409);
        LearningPlan saved = current();
        assertThat(saved.getVersion()).isEqualTo(1);
        assertThat(saved.getGoalText()).isEqualTo(LATEST);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void aPendingDraftCannotBringBackASkippedSetup(boolean editing) {
        if (editing) buildDraft(ORIGINAL, PlanPace.HOURS_1_TO_2);

        assertHttpError(() -> duringDraft(() -> {
            if (editing) updateGoal(LATEST, PlanPace.UNDER_30_MIN);
            else buildDraft(ORIGINAL, PlanPace.HOURS_1_TO_2);
        }, () -> service.skip(principal)), 409);

        assertThat(plans.findFirstByStudentIdAndConfirmedFalseOrderByVersionDesc(studentId)).isEmpty();
        assertThat(studentProfileRepository.findById(studentId).orElseThrow().getPlanSetupState())
                .isEqualTo(PlanSetupState.SKIPPED);
    }

    @Test
    void aDelayedGoalDraftCannotReplaceANewerDraft() {
        buildDraft(ORIGINAL, PlanPace.HOURS_1_TO_2);

        assertHttpError(() -> duringDraft(() -> updateGoal("An earlier goal", PlanPace.HOURS_2_PLUS),
                () -> updateGoal(LATEST, PlanPace.UNDER_30_MIN)), 409);

        LearningPlan saved = plans.findFirstByStudentIdAndConfirmedFalseOrderByVersionDesc(studentId).orElseThrow();
        assertThat(saved.getGoalText()).isEqualTo(LATEST);
        assertThat(saved.isActive()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void goalDraftCannotOverwriteANewerLevelCheck(boolean higherLevel) throws Exception {
        build();
        FinishedCheck check = finishCheck(higherLevel);
        assertHttpError(() -> duringDraft(() -> updateGoal("An earlier goal", PlanPace.HOURS_2_PLUS),
                () -> retake(check)), 409);
        LearningPlan saved = current();
        assertThat(saved.getGoalText()).isEqualTo(ORIGINAL);
        assertThat(saved.getLevelCheckedAt()).isNotNull();
        assertThat(saved.getVersion()).isEqualTo(higherLevel ? 2 : 1);
        assertThat(saved.getPlacedLevel() != MandarinLevel.NEW_TO_MANDARIN).isEqualTo(higherLevel);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void unchangedLevelRetakeCannotWriteAfterDeletionOrANewerGoal(boolean deleted) throws Exception {
        build();
        Long originalId = current().getId();
        FinishedCheck check = finishCheck(false);
        assertHttpError(() -> withPausedOperation(
                pause -> withAfterMethod(levelCheck, "evaluate", pause, () -> retake(check)),
                () -> {
                    if (deleted) deletion.delete(principal, principal.getUsername());
                    else updateGoal(LATEST, PlanPace.UNDER_30_MIN);
                }), deleted ? 401 : 409);
        assertThat(plans.findById(originalId).orElseThrow().getLevelCheckedAt()).isNull();
        assertThat(current().getGoalText()).isEqualTo(deleted ? null : LATEST);
    }

    @Test
    void delayedSkipPreservesAStartedPlanAndTheDailyGoal() throws Exception {
        withEarlierRead(transactions, () -> studentProfileRepository.findById(studentId).orElseThrow(),
                () -> service.skip(principal), () -> build(ORIGINAL, PlanPace.HOURS_2_PLUS));
        var student = studentProfileRepository.findById(studentId).orElseThrow();
        assertThat(student.getPlanSetupState()).isEqualTo(PlanSetupState.COMPLETED);
        assertThat(student.getDailyGoalXp()).isEqualTo(20);
        assertThat(current().getVersion()).isEqualTo(1);
    }

    @Test
    void delayedSkipPreservesXpEarnedAfterItsEarlierRead() throws Exception {
        withEarlierRead(transactions, () -> studentProfileRepository.findById(studentId).orElseThrow(),
                () -> service.skip(principal), () -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                    var session = xpService.begin(studentProfileRepository.findById(studentId).orElseThrow());
                    session.award("CONCURRENT_PRACTICE", 1L, 5, "Practice completed");
                    session.finish();
                }));
        var student = studentProfileRepository.findById(studentId).orElseThrow();
        assertThat(student.getPlanSetupState()).isEqualTo(PlanSetupState.SKIPPED);
        assertThat(student.getTotalPoints()).isEqualTo(5);
    }

    @Test
    void versionAllocationSeesAPlanCommittedAfterAnEarlierRead() throws Exception {
        build();
        withEarlierRead(transactions,
                () -> assertThat(jdbc.queryForObject(
                        "select max(version) from learning_plans where student_id = ?", Integer.class, studentId))
                        .isEqualTo(1),
                () -> assertThat(plans.findLatestVersionForUpdate(studentId)).contains(2),
                () -> updateGoal(LATEST, PlanPace.UNDER_30_MIN));
    }

    @Test
    void changingOnlyTheGoalKeepsWhenTheLevelWasActuallyChecked() {
        build();
        LocalDateTime checkedAt = LocalDateTime.of(2026, 1, 2, 12, 0);
        // Bound as a Timestamp, as Hibernate binds it, so the connection's time-zone conversion applies.
        jdbc.update("update learning_plans set level_checked_at = ?, level_check_answered = 3 where id = ?",
                Timestamp.valueOf(checkedAt), current().getId());
        updateGoal(LATEST, PlanPace.UNDER_30_MIN);
        LearningPlan saved = current();
        assertThat(saved.getLevelCheckedAt()).isEqualTo(checkedAt);
        assertThat(saved.getLevelCheckAnswered()).isEqualTo(3);
    }

    private void duringDraft(Runnable delayed, Runnable concurrent) throws Exception {
        AtomicBoolean first = new AtomicBoolean(true);
        withPausedOperation(pause -> {
            when(advisor.draft(any(), any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                if (first.getAndSet(false)) pause.run();
                return Optional.empty();
            });
            delayed.run();
        }, concurrent);
    }

    private void build() {
        build(ORIGINAL, PlanPace.HOURS_1_TO_2);
    }

    /** The setup: "Build my plan", then "Start my path". */
    private void build(String words, PlanPace pace) {
        buildDraft(words, pace);
        service.confirm(principal);
    }

    private void buildDraft(String words, PlanPace pace) {
        service.build(principal, new PlanRequests.BuildRequest(goal(words, pace), skippedCheck(), "en"));
    }

    private void updateGoal(String words, PlanPace pace) {
        service.updateGoal(principal, new PlanRequests.UpdateGoalRequest(goal(words, pace), "en"));
    }

    private record FinishedCheck(List<Answer> answers, String runToken) {
    }

    private void retake(FinishedCheck check) {
        service.retake(principal, new PlanRequests.RetakeRequest(check.answers(), "en", check.runToken()));
    }

    private LearningPlan current() {
        return plans.findFirstByStudentIdAndActiveTrueOrderByVersionDesc(studentId).orElseThrow();
    }

    private FinishedCheck finishCheck(boolean correct) {
        List<Answer> answers = new ArrayList<>();
        var step = levelCheck.next(principal, new LevelCheckNextRequest(MandarinLevel.NEW_TO_MANDARIN, answers, null));
        while (!step.done()) {
            Long questionId = step.next().id();
            Long optionId = correct ? rightLevelCheckOption(questionId) : null;
            answers.add(new Answer(questionId, optionId));
            step = levelCheck.next(principal,
                    new LevelCheckNextRequest(MandarinLevel.NEW_TO_MANDARIN, answers, step.runToken()));
        }
        return new FinishedCheck(answers, step.runToken());
    }

    private static PlanRequests.GoalInput goal(String words, PlanPace pace) {
        return new PlanRequests.GoalInput(PlanReason.TRAVEL, null, List.of(), words, null, pace, List.of());
    }

    private static PlanRequests.LevelCheckInput skippedCheck() {
        return new PlanRequests.LevelCheckInput(PlanSelfLevel.NEW_TO_MANDARIN, true, List.of(), null);
    }
}

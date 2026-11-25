package com.example.aichinese.service.learn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.aichinese.config.SecurityProperties;
import com.example.aichinese.dto.learn.HintDto;
import com.example.aichinese.entity.ServedHint;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.enums.HintMode;
import com.example.aichinese.entity.enums.MandarinLevel;
import com.example.aichinese.repository.LearningPlanRepository;
import com.example.aichinese.repository.ServedHintRepository;
import com.example.aichinese.repository.SystemSettingRepository;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.StudentLearningAccessService;
import com.example.aichinese.support.ConfigFixtures;

/** Unit tests for {@link HintPolicyService} and its {@link HintRunSigner} token signing rules. */
class HintPolicyServiceTests {

    private final SystemSettingRepository settingRepository = mock(SystemSettingRepository.class);
    private final LearningPlanRepository planRepository = mock(LearningPlanRepository.class);
    private final ServedHintRepository servedHintRepository = mock(ServedHintRepository.class);
    private final HintRunSigner runSigner = mock(HintRunSigner.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final StudentLearningAccessService accessService = mock(StudentLearningAccessService.class);
    private final HintPolicyService service = new HintPolicyService(
            settingRepository, planRepository, servedHintRepository, runSigner, auditLogService, accessService);

    @Test
    void retryingAnAnsweredPracticeItemInTheSameRunRecordsAFreshPostAnswerHint() {
        StudentProfile student = student(7L);
        String scope = "PRACTICE#LISTENING#3#";
        String itemRef = scope + "0";
        LocalDateTime startedAt = LocalDateTime.of(2026, 5, 1, 10, 0, 0);
        LocalDateTime lastAnswered = LocalDateTime.of(2026, 5, 1, 10, 0, 5);
        HintPolicyService.Run run = new HintPolicyService.Run(startedAt, "sig");
        List<HintDto.Step> ladder = List.of(new HintDto.Step("nudge", List.of(), List.of(), null, null, List.of()));

        when(runSigner.requireId(7L, scope, startedAt, "sig")).thenReturn("run-1");
        when(accessService.lockProfile(student)).thenReturn(student);
        when(planRepository.findActivePlacement(student)).thenReturn(Optional.of(MandarinLevel.BEGINNER_1));
        when(settingRepository.findValueByKey(HintPolicyService.POLICY_KEY)).thenReturn(Optional.empty());
        when(servedHintRepository.existsByStudentIdAndItemRefAndRunIdAndCreatedAtAfter(7L, itemRef, "run-1", lastAnswered))
                .thenReturn(false)
                .thenReturn(true);

        HintDto.Step firstRetryHint = service.hintInRun(student, scope, itemRef, run, ladder, studentId -> lastAnswered);
        HintDto.Step repeatedRetryHint = service.hintInRun(student, scope, itemRef, run, ladder, studentId -> lastAnswered);

        assertThat(firstRetryHint).isEqualTo(ladder.getFirst());
        assertThat(repeatedRetryHint).isEqualTo(ladder.getFirst());
        // Recorded once for the post-answer retry, not duplicated on repeated opens before the retry answer.
        verify(servedHintRepository).save(any(ServedHint.class));
        verify(servedHintRepository, never()).existsByStudentIdAndItemRefAndRunId(7L, itemRef, "run-1");
    }

    @Test
    void twoCopiesOfTheSameLessonOpenedOnTheSameClockTickHaveDistinctRunIds() {
        SecurityProperties settings = ConfigFixtures.securityProperties("unit-test-hint-signing-key", 60_000L, false);
        HintRunSigner signer = new HintRunSigner(settings);
        LocalDateTime start = LocalDateTime.of(2026, 11, 21, 9, 0);
        String scope = "LESSON#42#";
        String first = signer.sign(7L, scope, start);
        String second = signer.sign(7L, scope, start);

        String firstId = signer.requireId(7L, scope, start, first);
        String secondId = signer.requireId(7L, scope, start, second);
        assertThat(firstId).isNotNull().isNotEqualTo(secondId);
        assertThat(secondId).isNotNull();
    }

    private static StudentProfile student(long id) {
        StudentProfile profile = new StudentProfile();
        profile.setId(id);
        profile.setHintMode(HintMode.AUTOMATIC);
        return profile;
    }
}

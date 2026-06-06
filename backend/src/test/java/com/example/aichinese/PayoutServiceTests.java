package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.commerce.PayoutRequestCreateRequest;
import com.example.aichinese.entity.PayoutRequest;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.PayoutStatus;
import com.example.aichinese.entity.enums.TeacherEarningStatus;
import com.example.aichinese.repository.PayoutRequestRepository;
import com.example.aichinese.repository.TeacherEarningRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.CommerceMoneyService;
import com.example.aichinese.service.CommerceSettingsService;
import com.example.aichinese.service.NotificationService;
import com.example.aichinese.service.PayoutService;

/**
 * Unit coverage for {@code PayoutService} - pins the
 * lock-then-check-balance ordering so two concurrent payout requests
 * cannot overdraw the teacher's available balance, and the one-way
 * payout state machine (PENDING -> APPROVED -> PAID / REJECTED) whose
 * committed statuses reserve aggregate balance without rewriting
 * immutable per-order-item earning rows.
 */
class PayoutServiceTests {

    private static final long ADMIN_USER_ID = 1L;
    private static final long PAYOUT_ID = 10L;
    private static final long TEACHER_ID = 200L;
    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_NAME = "Admin Example";
    private static final String PASSWORD_HASH = "password-hash";
    private static final String DEFAULT_CURRENCY = "MYR";
    private static final String TOO_MUCH_REASON = "Too much";
    private static final String EXCEEDS_AVAILABLE_BALANCE = "exceeds available balance";
    private static final String INSUFFICIENT_CURRENT_BALANCE = "current available balance is insufficient";
    private static final BigDecimal CURRENT_NET_AMOUNT = new BigDecimal("100.00");
    private static final BigDecimal REQUESTED_AMOUNT = new BigDecimal("101.00");
    private static final BigDecimal PAYOUT_AMOUNT = new BigDecimal("90.00");

    @Test
    void createPayoutRequestUsesLockedTeacherProfileBeforeBalanceCheck() {
        Fixture fixture = fixture();
        SecurityUserDetails currentUser = currentUser();
        TeacherProfile teacher = teacherProfile();
        when(fixture.teacherProfileRepository.findLockedByUserId(currentUser.getUserId()))
                .thenReturn(Optional.of(teacher));
        mockCommittedBalance(fixture, CURRENT_NET_AMOUNT, BigDecimal.ZERO);

        assertThatThrownBy(() -> fixture.payoutService.createPayoutRequest(
                currentUser,
                payoutRequestCreateRequest()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(exception.getReason())
                        .contains(EXCEEDS_AVAILABLE_BALANCE));

        verify(fixture.teacherProfileRepository).findLockedByUserId(currentUser.getUserId());
        verify(fixture.teacherProfileRepository, never()).findByUserId(currentUser.getUserId());
        verify(fixture.payoutRequestRepository, never()).save(any(PayoutRequest.class));
    }

    @Test
    void approvePayoutRejectsWhenCurrentBalanceNoLongerCoversPendingPayout() {
        Fixture fixture = fixture();
        PayoutRequest payoutRequest = payoutRequest(PayoutStatus.PENDING);
        when(fixture.payoutRequestRepository.findLockedById(PAYOUT_ID)).thenReturn(Optional.of(payoutRequest));
        mockCommittedBalance(fixture, BigDecimal.ZERO, PAYOUT_AMOUNT);

        assertThatThrownBy(() -> fixture.payoutService.approvePayout(currentUser(), PAYOUT_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(exception.getReason())
                        .contains(INSUFFICIENT_CURRENT_BALANCE));

        assertThat(payoutRequest.getPayoutStatus()).isEqualTo(PayoutStatus.PENDING);
        verify(fixture.payoutRequestRepository, never()).save(any(PayoutRequest.class));
    }

    @Test
    void markPayoutPaidRejectsWhenCurrentBalanceNoLongerCoversApprovedPayout() {
        Fixture fixture = fixture();
        PayoutRequest payoutRequest = payoutRequest(PayoutStatus.APPROVED);
        when(fixture.payoutRequestRepository.findLockedById(PAYOUT_ID)).thenReturn(Optional.of(payoutRequest));
        mockCommittedBalance(fixture, BigDecimal.ZERO, PAYOUT_AMOUNT);

        assertThatThrownBy(() -> fixture.payoutService.markPayoutPaid(currentUser(), PAYOUT_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(exception.getReason())
                        .contains(INSUFFICIENT_CURRENT_BALANCE));

        assertThat(payoutRequest.getPayoutStatus()).isEqualTo(PayoutStatus.APPROVED);
        verify(fixture.payoutRequestRepository, never()).save(any(PayoutRequest.class));
    }

    private void mockCommittedBalance(Fixture fixture, BigDecimal currentNetAmount, BigDecimal committedPayoutAmount) {
        when(fixture.teacherEarningRepository.sumNetAmountByTeacherIdAndEarningStatusNot(
                TEACHER_ID,
                TeacherEarningStatus.REFUNDED))
                .thenReturn(currentNetAmount);
        // Reference the canonical PayoutStatus.COMMITTED set rather than
        // re-listing its members so the mock matcher self-updates if the
        // enum's COMMITTED set is ever extended (e.g. a new DISPUTED state).
        when(fixture.payoutRequestRepository.sumAmountByTeacherIdAndPayoutStatusIn(
                eq(TEACHER_ID),
                eq(PayoutStatus.COMMITTED)))
                .thenReturn(committedPayoutAmount);
    }

    private PayoutRequest payoutRequest(PayoutStatus status) {
        TeacherProfile teacher = teacherProfile();

        PayoutRequest payoutRequest = new PayoutRequest();
        payoutRequest.setId(PAYOUT_ID);
        payoutRequest.setTeacher(teacher);
        payoutRequest.setAmount(PAYOUT_AMOUNT);
        payoutRequest.setPayoutStatus(status);
        return payoutRequest;
    }

    private TeacherProfile teacherProfile() {
        TeacherProfile teacher = new TeacherProfile();
        teacher.setId(TEACHER_ID);
        return teacher;
    }

    private SecurityUserDetails currentUser() {
        User user = new User();
        user.setId(ADMIN_USER_ID);
        user.setEmail(ADMIN_EMAIL);
        user.setDisplayName(ADMIN_NAME);
        user.setPasswordHash(PASSWORD_HASH);
        user.setActive(true);
        return new SecurityUserDetails(user);
    }

    private static PayoutRequestCreateRequest payoutRequestCreateRequest() {
        return new PayoutRequestCreateRequest(REQUESTED_AMOUNT, TOO_MUCH_REASON);
    }

    private Fixture fixture() {
        TeacherProfileRepository teacherProfileRepository = mock(TeacherProfileRepository.class);
        TeacherEarningRepository teacherEarningRepository = mock(TeacherEarningRepository.class);
        PayoutRequestRepository payoutRequestRepository = mock(PayoutRequestRepository.class);
        CommerceSettingsService commerceSettingsService = mock(CommerceSettingsService.class);
        when(commerceSettingsService.getDefaultCurrency()).thenReturn(DEFAULT_CURRENCY);
        PayoutService payoutService = new PayoutService(
                teacherProfileRepository,
                teacherEarningRepository,
                payoutRequestRepository,
                new CommerceMoneyService(),
                commerceSettingsService,
                mock(NotificationService.class),
                mock(AuditLogService.class));
        return new Fixture(payoutService, teacherProfileRepository, teacherEarningRepository, payoutRequestRepository);
    }

    private record Fixture(
            PayoutService payoutService,
            TeacherProfileRepository teacherProfileRepository,
            TeacherEarningRepository teacherEarningRepository,
            PayoutRequestRepository payoutRequestRepository) {
    }
}

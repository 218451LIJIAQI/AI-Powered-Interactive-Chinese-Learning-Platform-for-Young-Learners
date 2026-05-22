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
import java.util.EnumSet;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.commerce.PayoutRequestCreateRequest;
import com.example.aichinese.entity.PayoutRequest;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.PayoutStatus;
import com.example.aichinese.repository.PayoutRequestRepository;
import com.example.aichinese.repository.TeacherEarningRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.CommerceMoneyService;
import com.example.aichinese.service.NotificationService;
import com.example.aichinese.service.PayoutService;

class PayoutServiceTests {

    @Test
    void createPayoutRequestUsesLockedTeacherProfileBeforeBalanceCheck() {
        Fixture fixture = fixture();
        SecurityUserDetails currentUser = currentUser();
        TeacherProfile teacher = teacherProfile();
        when(fixture.teacherProfileRepository.findLockedByUserId(currentUser.getUserId()))
                .thenReturn(Optional.of(teacher));
        mockCommittedBalance(fixture, new BigDecimal("100.00"), BigDecimal.ZERO);

        assertThatThrownBy(() -> fixture.payoutService.createPayoutRequest(
                currentUser,
                new PayoutRequestCreateRequest(new BigDecimal("101.00"), "Too much")))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(exception.getReason())
                        .contains("exceeds available balance"));

        verify(fixture.teacherProfileRepository).findLockedByUserId(currentUser.getUserId());
        verify(fixture.teacherProfileRepository, never()).findByUserId(currentUser.getUserId());
        verify(fixture.payoutRequestRepository, never()).save(any(PayoutRequest.class));
    }

    @Test
    void approvePayoutRejectsWhenCurrentBalanceNoLongerCoversPendingPayout() {
        Fixture fixture = fixture();
        PayoutRequest payoutRequest = payoutRequest(PayoutStatus.PENDING);
        when(fixture.payoutRequestRepository.findLockedById(10L)).thenReturn(Optional.of(payoutRequest));
        mockCommittedBalance(fixture, BigDecimal.ZERO, new BigDecimal("90.00"));

        assertThatThrownBy(() -> fixture.payoutService.approvePayout(currentUser(), 10L))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(exception.getReason())
                        .contains("current available balance is insufficient"));

        assertThat(payoutRequest.getPayoutStatus()).isEqualTo(PayoutStatus.PENDING);
        verify(fixture.payoutRequestRepository, never()).save(any(PayoutRequest.class));
    }

    @Test
    void markPayoutPaidRejectsWhenCurrentBalanceNoLongerCoversApprovedPayout() {
        Fixture fixture = fixture();
        PayoutRequest payoutRequest = payoutRequest(PayoutStatus.APPROVED);
        when(fixture.payoutRequestRepository.findLockedById(10L)).thenReturn(Optional.of(payoutRequest));
        mockCommittedBalance(fixture, BigDecimal.ZERO, new BigDecimal("90.00"));

        assertThatThrownBy(() -> fixture.payoutService.markPayoutPaid(currentUser(), 10L))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(exception.getReason())
                        .contains("current available balance is insufficient"));

        assertThat(payoutRequest.getPayoutStatus()).isEqualTo(PayoutStatus.APPROVED);
        verify(fixture.payoutRequestRepository, never()).save(any(PayoutRequest.class));
    }

    private void mockCommittedBalance(Fixture fixture, BigDecimal currentNetAmount, BigDecimal committedPayoutAmount) {
        when(fixture.teacherEarningRepository.sumNetAmountByTeacherIdAndEarningStatusNot(200L, "REFUNDED"))
                .thenReturn(currentNetAmount);
        when(fixture.payoutRequestRepository.sumAmountByTeacherIdAndPayoutStatusIn(
                eq(200L),
                eq(EnumSet.of(PayoutStatus.PENDING, PayoutStatus.APPROVED, PayoutStatus.PAID))))
                .thenReturn(committedPayoutAmount);
    }

    private PayoutRequest payoutRequest(PayoutStatus status) {
        TeacherProfile teacher = teacherProfile();

        PayoutRequest payoutRequest = new PayoutRequest();
        payoutRequest.setId(10L);
        payoutRequest.setTeacher(teacher);
        payoutRequest.setAmount(new BigDecimal("90.00"));
        payoutRequest.setPayoutStatus(status);
        return payoutRequest;
    }

    private TeacherProfile teacherProfile() {
        TeacherProfile teacher = new TeacherProfile();
        teacher.setId(200L);
        return teacher;
    }

    private SecurityUserDetails currentUser() {
        User user = new User();
        user.setId(1L);
        user.setEmail("admin@example.com");
        user.setDisplayName("Admin Example");
        user.setPasswordHash("password-hash");
        user.setActive(true);
        return new SecurityUserDetails(user);
    }

    private Fixture fixture() {
        TeacherProfileRepository teacherProfileRepository = mock(TeacherProfileRepository.class);
        TeacherEarningRepository teacherEarningRepository = mock(TeacherEarningRepository.class);
        PayoutRequestRepository payoutRequestRepository = mock(PayoutRequestRepository.class);
        PayoutService payoutService = new PayoutService(
                teacherProfileRepository,
                teacherEarningRepository,
                payoutRequestRepository,
                new CommerceMoneyService(),
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

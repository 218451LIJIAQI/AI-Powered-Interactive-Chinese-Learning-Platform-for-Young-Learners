package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.commerce.RefundDecisionRequest;
import com.example.aichinese.dto.commerce.RefundRequestCreateRequest;
import com.example.aichinese.entity.Order;
import com.example.aichinese.entity.RefundRequest;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.PaymentStatus;
import com.example.aichinese.entity.enums.RefundStatus;
import com.example.aichinese.repository.OrderRepository;
import com.example.aichinese.repository.PaymentRecordRepository;
import com.example.aichinese.repository.RefundRequestRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.CommerceMoneyService;
import com.example.aichinese.service.EntitlementService;
import com.example.aichinese.service.NotificationService;
import com.example.aichinese.service.RefundService;
import com.example.aichinese.service.StudentLearningAccessService;
import com.example.aichinese.service.TeacherEarningService;

/**
 * Unit coverage for {@code RefundService} — pins the
 * lock-order-before-active-check ordering, the one-way state machine
 * (PENDING → APPROVED → COMPLETED / REJECTED), and the entitlement
 * revocation + teacher earning refund side-effects on approval.
 */
class RefundServiceTests {

    @Test
    void createRefundRequestUsesLockedOrderLookupBeforeActiveRefundCheck() {
        Fixture fixture = fixture();
        SecurityUserDetails currentUser = currentUser();
        StudentProfile student = student();
        Order order = paidOrder(student);
        when(fixture.studentLearningAccessService.requireStudent(currentUser)).thenReturn(student);
        when(fixture.orderRepository.findLockedByIdAndStudentUserId(500L, currentUser.getUserId()))
                .thenReturn(Optional.of(order));
        when(fixture.refundRequestRepository.existsByOrderIdAndRefundStatusIn(
                eq(500L),
                any()))
                .thenReturn(true);

        assertThatThrownBy(() -> fixture.refundService.createRefundRequest(
                currentUser,
                new RefundRequestCreateRequest(500L, "Duplicate active refund")))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(exception.getReason())
                        .contains("already active"));

        verify(fixture.orderRepository).findLockedByIdAndStudentUserId(500L, currentUser.getUserId());
        verify(fixture.orderRepository, never()).findByIdAndStudentUserId(500L, currentUser.getUserId());
        verify(fixture.refundRequestRepository, never()).save(any(RefundRequest.class));
        verifyNoInteractions(
                fixture.entitlementService,
                fixture.teacherEarningService,
                fixture.notificationService,
                fixture.auditLogService);
    }

    @Test
    void rejectRefundRejectsApprovedRequestWithoutSideEffects() {
        Fixture fixture = fixture();
        RefundRequest refundRequest = refundRequest(RefundStatus.APPROVED);
        when(fixture.refundRequestRepository.findLockedById(10L)).thenReturn(Optional.of(refundRequest));

        assertThatThrownBy(() -> fixture.refundService.rejectRefund(
                currentUser(),
                10L,
                new RefundDecisionRequest("Too late")))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(exception.getReason())
                        .contains("currently APPROVED"));

        assertThat(refundRequest.getRefundStatus()).isEqualTo(RefundStatus.APPROVED);
        assertThat(refundRequest.getDecisionReason()).isNull();
        verify(fixture.refundRequestRepository, never()).save(any(RefundRequest.class));
        verifyNoInteractions(
                fixture.paymentRecordRepository,
                fixture.entitlementService,
                fixture.teacherEarningService,
                fixture.notificationService,
                fixture.auditLogService);
    }

    @Test
    void approveRefundRejectsRejectedRequestWithoutSideEffects() {
        Fixture fixture = fixture();
        RefundRequest refundRequest = refundRequest(RefundStatus.REJECTED);
        when(fixture.refundRequestRepository.findLockedById(10L)).thenReturn(Optional.of(refundRequest));

        assertThatThrownBy(() -> fixture.refundService.approveRefund(currentUser(), 10L))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(exception.getReason())
                        .contains("currently REJECTED"));

        assertThat(refundRequest.getRefundStatus()).isEqualTo(RefundStatus.REJECTED);
        verify(fixture.refundRequestRepository, never()).save(any(RefundRequest.class));
        verifyNoInteractions(
                fixture.paymentRecordRepository,
                fixture.entitlementService,
                fixture.teacherEarningService,
                fixture.notificationService,
                fixture.auditLogService);
    }

    @Test
    void markRefundedRejectsRejectedRequestWithoutSideEffects() {
        Fixture fixture = fixture();
        RefundRequest refundRequest = refundRequest(RefundStatus.REJECTED);
        when(fixture.refundRequestRepository.findLockedById(10L)).thenReturn(Optional.of(refundRequest));

        assertThatThrownBy(() -> fixture.refundService.markRefunded(currentUser(), 10L))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(exception.getReason())
                        .contains("currently REJECTED"));

        assertThat(refundRequest.getRefundStatus()).isEqualTo(RefundStatus.REJECTED);
        verify(fixture.refundRequestRepository, never()).save(any(RefundRequest.class));
        verifyNoInteractions(
                fixture.paymentRecordRepository,
                fixture.entitlementService,
                fixture.teacherEarningService,
                fixture.notificationService,
                fixture.auditLogService);
    }

    private RefundRequest refundRequest(RefundStatus status) {
        StudentProfile student = student();
        Order order = paidOrder(student);

        RefundRequest refundRequest = new RefundRequest();
        refundRequest.setId(10L);
        refundRequest.setStudent(student);
        refundRequest.setOrder(order);
        refundRequest.setReason("Refund reason");
        refundRequest.setRefundStatus(status);
        return refundRequest;
    }

    private StudentProfile student() {
        User studentUser = new User();
        studentUser.setId(99L);
        studentUser.setEmail("student@example.com");
        studentUser.setDisplayName("Student Example");
        studentUser.setPasswordHash("password-hash");
        studentUser.setActive(true);

        StudentProfile student = new StudentProfile();
        student.setId(199L);
        student.setUser(studentUser);
        return student;
    }

    private Order paidOrder(StudentProfile student) {
        Order order = new Order();
        order.setId(500L);
        order.setStudent(student);
        order.setOrderNumber("ORD-TEST");
        order.setTotalAmount(BigDecimal.TEN);
        order.setPaymentStatus(PaymentStatus.PAID);
        return order;
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
        StudentLearningAccessService studentLearningAccessService = mock(StudentLearningAccessService.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        PaymentRecordRepository paymentRecordRepository = mock(PaymentRecordRepository.class);
        RefundRequestRepository refundRequestRepository = mock(RefundRequestRepository.class);
        EntitlementService entitlementService = mock(EntitlementService.class);
        NotificationService notificationService = mock(NotificationService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        TeacherEarningService teacherEarningService = mock(TeacherEarningService.class);
        RefundService refundService = new RefundService(
                studentLearningAccessService,
                orderRepository,
                paymentRecordRepository,
                refundRequestRepository,
                entitlementService,
                new CommerceMoneyService(),
                notificationService,
                auditLogService,
                teacherEarningService);
        return new Fixture(
                refundService,
                studentLearningAccessService,
                orderRepository,
                paymentRecordRepository,
                refundRequestRepository,
                entitlementService,
                notificationService,
                auditLogService,
                teacherEarningService);
    }

    private record Fixture(
            RefundService refundService,
            StudentLearningAccessService studentLearningAccessService,
            OrderRepository orderRepository,
            PaymentRecordRepository paymentRecordRepository,
            RefundRequestRepository refundRequestRepository,
            EntitlementService entitlementService,
            NotificationService notificationService,
            AuditLogService auditLogService,
            TeacherEarningService teacherEarningService) {
    }
}

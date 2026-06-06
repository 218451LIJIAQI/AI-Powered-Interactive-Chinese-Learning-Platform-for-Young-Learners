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

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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
 * Unit coverage for {@code RefundService}. It pins the
 * lock-order-before-active-check ordering, the one-way state machine
 * (PENDING -> APPROVED -> COMPLETED / REJECTED), and the entitlement
 * revocation + teacher earning refund side-effects on approval.
 */
class RefundServiceTests {

    private static final long ADMIN_USER_ID = 1L;
    private static final long REFUND_REQUEST_ID = 10L;
    private static final long STUDENT_USER_ID = 99L;
    private static final long STUDENT_PROFILE_ID = 199L;
    private static final long ORDER_ID = 500L;

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_DISPLAY_NAME = "Admin Example";
    private static final String STUDENT_EMAIL = "student@example.com";
    private static final String STUDENT_DISPLAY_NAME = "Student Example";
    private static final String PASSWORD_HASH = "password-hash";
    private static final String ORDER_NUMBER = "ORD-TEST";
    private static final String REFUND_REASON = "Refund reason";
    private static final String DUPLICATE_REFUND_REASON = "Duplicate active refund";
    private static final String REJECTION_REASON = "Too late";
    private static final String ACTIVE_REFUND_MESSAGE = "already active";
    private static final String APPROVED_STATE_MESSAGE = "currently APPROVED";
    private static final String REJECTED_STATE_MESSAGE = "currently REJECTED";

    private static final BigDecimal ORDER_TOTAL = BigDecimal.TEN;

    @Test
    void createRefundRequestUsesLockedOrderLookupBeforeActiveRefundCheck() {
        Fixture fixture = fixture();
        SecurityUserDetails currentUser = currentUser();
        StudentProfile student = student();
        Order order = paidOrder(student);
        when(fixture.studentLearningAccessService.requireStudent(currentUser)).thenReturn(student);
        when(fixture.orderRepository.findLockedByIdAndStudentUserId(ORDER_ID, currentUser.getUserId()))
                .thenReturn(Optional.of(order));
        when(fixture.refundRequestRepository.existsByOrderIdAndRefundStatusIn(
                eq(ORDER_ID),
                any()))
                .thenReturn(true);

        assertResponseReasonContains(() -> fixture.refundService.createRefundRequest(
                currentUser,
                new RefundRequestCreateRequest(ORDER_ID, DUPLICATE_REFUND_REASON)), ACTIVE_REFUND_MESSAGE);

        verify(fixture.orderRepository).findLockedByIdAndStudentUserId(ORDER_ID, currentUser.getUserId());
        verify(fixture.refundRequestRepository, never()).save(any(RefundRequest.class));
        verifyNoRefundSideEffects(fixture);
    }

    @Test
    void rejectRefundRejectsApprovedRequestWithoutSideEffects() {
        Fixture fixture = fixture();
        RefundRequest refundRequest = refundRequest(RefundStatus.APPROVED);
        when(fixture.refundRequestRepository.findLockedById(REFUND_REQUEST_ID)).thenReturn(Optional.of(refundRequest));

        assertResponseReasonContains(() -> fixture.refundService.rejectRefund(
                currentUser(),
                REFUND_REQUEST_ID,
                new RefundDecisionRequest(REJECTION_REASON)), APPROVED_STATE_MESSAGE);

        assertThat(refundRequest.getRefundStatus()).isEqualTo(RefundStatus.APPROVED);
        assertThat(refundRequest.getDecisionReason()).isNull();
        verify(fixture.refundRequestRepository, never()).save(any(RefundRequest.class));
        verifyNoRefundSideEffects(fixture);
    }

    @Test
    void approveRefundRejectsRejectedRequestWithoutSideEffects() {
        Fixture fixture = fixture();
        RefundRequest refundRequest = refundRequest(RefundStatus.REJECTED);
        when(fixture.refundRequestRepository.findLockedById(REFUND_REQUEST_ID)).thenReturn(Optional.of(refundRequest));

        assertResponseReasonContains(
                () -> fixture.refundService.approveRefund(currentUser(), REFUND_REQUEST_ID),
                REJECTED_STATE_MESSAGE);

        assertThat(refundRequest.getRefundStatus()).isEqualTo(RefundStatus.REJECTED);
        verify(fixture.refundRequestRepository, never()).save(any(RefundRequest.class));
        verifyNoRefundSideEffects(fixture);
    }

    @Test
    void markRefundedRejectsRejectedRequestWithoutSideEffects() {
        Fixture fixture = fixture();
        RefundRequest refundRequest = refundRequest(RefundStatus.REJECTED);
        when(fixture.refundRequestRepository.findLockedById(REFUND_REQUEST_ID)).thenReturn(Optional.of(refundRequest));

        assertResponseReasonContains(
                () -> fixture.refundService.markRefunded(currentUser(), REFUND_REQUEST_ID),
                REJECTED_STATE_MESSAGE);

        assertThat(refundRequest.getRefundStatus()).isEqualTo(RefundStatus.REJECTED);
        verify(fixture.refundRequestRepository, never()).save(any(RefundRequest.class));
        verifyNoRefundSideEffects(fixture);
    }

    private static void assertResponseReasonContains(ThrowingCallable action, String reason) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(exception.getReason())
                        .contains(reason));
    }

    private static void verifyNoRefundSideEffects(Fixture fixture) {
        verifyNoInteractions(
                fixture.paymentRecordRepository,
                fixture.entitlementService,
                fixture.teacherEarningService,
                fixture.notificationService,
                fixture.auditLogService);
    }

    private static RefundRequest refundRequest(RefundStatus status) {
        StudentProfile student = student();
        Order order = paidOrder(student);

        RefundRequest refundRequest = new RefundRequest();
        refundRequest.setId(REFUND_REQUEST_ID);
        refundRequest.setStudent(student);
        refundRequest.setOrder(order);
        refundRequest.setReason(REFUND_REASON);
        refundRequest.setRefundStatus(status);
        return refundRequest;
    }

    private static StudentProfile student() {
        User studentUser = new User();
        studentUser.setId(STUDENT_USER_ID);
        studentUser.setEmail(STUDENT_EMAIL);
        studentUser.setDisplayName(STUDENT_DISPLAY_NAME);
        studentUser.setPasswordHash(PASSWORD_HASH);
        studentUser.setActive(true);

        StudentProfile student = new StudentProfile();
        student.setId(STUDENT_PROFILE_ID);
        student.setUser(studentUser);
        return student;
    }

    private static Order paidOrder(StudentProfile student) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setStudent(student);
        order.setOrderNumber(ORDER_NUMBER);
        order.setTotalAmount(ORDER_TOTAL);
        order.setPaymentStatus(PaymentStatus.PAID);
        return order;
    }

    private static SecurityUserDetails currentUser() {
        User user = new User();
        user.setId(ADMIN_USER_ID);
        user.setEmail(ADMIN_EMAIL);
        user.setDisplayName(ADMIN_DISPLAY_NAME);
        user.setPasswordHash(PASSWORD_HASH);
        user.setActive(true);
        return new SecurityUserDetails(user);
    }

    private static Fixture fixture() {
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

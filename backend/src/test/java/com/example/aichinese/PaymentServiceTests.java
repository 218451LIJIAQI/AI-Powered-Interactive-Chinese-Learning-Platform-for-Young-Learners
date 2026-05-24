package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.example.aichinese.dto.commerce.PaymentProviderStatusResult;
import com.example.aichinese.dto.commerce.StripeWebhookResultResponse;
import com.example.aichinese.dto.commerce.StudentPaymentRecordResponse;
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.entity.Order;
import com.example.aichinese.entity.OrderItem;
import com.example.aichinese.entity.PaymentRecord;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.PaymentStatus;
import com.example.aichinese.provider.PaymentProviderClient;
import com.example.aichinese.repository.OrderItemRepository;
import com.example.aichinese.repository.OrderRepository;
import com.example.aichinese.repository.PaymentRecordRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.CartService;
import com.example.aichinese.service.EntitlementService;
import com.example.aichinese.service.NotificationService;
import com.example.aichinese.service.PaymentProviderService;
import com.example.aichinese.service.PaymentService;
import com.example.aichinese.service.TeacherEarningService;

/**
 * Unit coverage for {@code PaymentService} — pins the lifecycle
 * state machine (PENDING → SUCCESS / FAILED), webhook idempotency
 * under retry, cancellation synchronisation with provider terminal
 * states, and the entitlement-creation side-effect on success.
 */
class PaymentServiceTests {

    @Test
    void verifyPaymentStatusSynchronizesCancelledProviderTerminalState() {
        assertTerminalProviderStatusIsSynchronized(PaymentStatus.CANCELLED);
    }

    @Test
    void verifyPaymentStatusSynchronizesFailedProviderTerminalState() {
        assertTerminalProviderStatusIsSynchronized(PaymentStatus.FAILED);
    }

    @Test
    void createPendingPaymentNormalizesOrderAmountBeforePersistingPaymentRecord() {
        PaymentRecordRepository paymentRecordRepository = mock(PaymentRecordRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        PaymentProviderClient providerClient = mock(PaymentProviderClient.class);
        EntitlementService entitlementService = mock(EntitlementService.class);
        TeacherEarningService teacherEarningService = mock(TeacherEarningService.class);
        Order order = new Order();
        StudentProfile student = new StudentProfile();

        order.setTotalAmount(new BigDecimal("29.999"));
        order.setCurrencyCode("MYR");
        when(providerClient.providerName()).thenReturn("provider");
        when(paymentRecordRepository.save(any(PaymentRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentRecord paymentRecord = service(
                providerClient,
                paymentRecordRepository,
                orderRepository,
                orderItemRepository,
                entitlementService,
                teacherEarningService)
                .createPendingPayment(order, student);

        assertThat(paymentRecord.getAmount()).isEqualByComparingTo("30.00");
        assertThat(paymentRecord.getCurrencyCode()).isEqualTo("MYR");
        assertThat(paymentRecord.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void verifyPaymentStatusDoesNotPollProviderForNonPendingStatuses() {
        for (PaymentStatus status : List.of(PaymentStatus.PAID, PaymentStatus.FAILED, PaymentStatus.CANCELLED, PaymentStatus.REFUNDED)) {
            PaymentRecordRepository paymentRecordRepository = mock(PaymentRecordRepository.class);
            OrderRepository orderRepository = mock(OrderRepository.class);
            OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
            PaymentProviderClient providerClient = mock(PaymentProviderClient.class);
            EntitlementService entitlementService = mock(EntitlementService.class);
            TeacherEarningService teacherEarningService = mock(TeacherEarningService.class);
            PaymentRecord paymentRecord = paymentRecord(status);

            when(paymentRecordRepository.findByIdAndStudentUserId(10L, 99L)).thenReturn(Optional.of(paymentRecord));
            when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(500L)).thenReturn(List.of());

            PaymentService paymentService = service(
                    providerClient,
                    paymentRecordRepository,
                    orderRepository,
                    orderItemRepository,
                    entitlementService,
                    teacherEarningService);

            StudentPaymentRecordResponse response = paymentService.verifyPaymentStatus(currentUser(), 10L);

            assertThat(response.paymentStatus()).isEqualTo(status);
            assertThat(response.providerStatus()).isEqualTo("EXISTING_" + status.name());
            assertThat(paymentRecord.getPaymentStatus()).isEqualTo(status);
            verifyNoInteractions(providerClient, orderRepository, entitlementService, teacherEarningService);
            verify(paymentRecordRepository, never()).save(paymentRecord);
        }
    }

    @Test
    void studentPaymentListBatchLoadsOrderItemsForPage() {
        PaymentRecordRepository paymentRecordRepository = mock(PaymentRecordRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        PaymentProviderClient providerClient = mock(PaymentProviderClient.class);
        EntitlementService entitlementService = mock(EntitlementService.class);
        TeacherEarningService teacherEarningService = mock(TeacherEarningService.class);
        PaymentRecord firstPayment = paymentRecord(PaymentStatus.PAID);
        PaymentRecord secondPayment = paymentRecord(PaymentStatus.PAID);
        secondPayment.setId(11L);
        secondPayment.getOrder().setId(501L);
        secondPayment.getOrder().setOrderNumber("ORD-TEST-2");
        OrderItem firstOrderItem = orderItem(firstPayment.getOrder(), 1L);
        OrderItem secondOrderItem = orderItem(secondPayment.getOrder(), 2L);

        when(paymentRecordRepository.findByStudentUserIdOrderByCreatedAtDesc(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(firstPayment, secondPayment)));
        when(orderItemRepository.findByOrderIdInOrderByOrderIdAscCreatedAtAsc(List.of(500L, 501L)))
                .thenReturn(List.of(firstOrderItem, secondOrderItem));

        PaymentService paymentService = service(
                providerClient,
                paymentRecordRepository,
                orderRepository,
                orderItemRepository,
                entitlementService,
                teacherEarningService);

        PageResponse<StudentPaymentRecordResponse> response = paymentService.getStudentPayments(currentUser(), 0, 20);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).items()).hasSize(1);
        assertThat(response.items().get(1).items()).hasSize(1);
        verify(orderItemRepository, times(1)).findByOrderIdInOrderByOrderIdAscCreatedAtAsc(List.of(500L, 501L));
        verify(orderItemRepository, never()).findByOrderIdOrderByCreatedAtAsc(500L);
        verify(orderItemRepository, never()).findByOrderIdOrderByCreatedAtAsc(501L);
    }

    @Test
    void markPaymentPaidDoesNotReviveNonPendingStatuses() {
        for (PaymentStatus status : List.of(PaymentStatus.PAID, PaymentStatus.FAILED, PaymentStatus.CANCELLED, PaymentStatus.REFUNDED)) {
            PaymentRecordRepository paymentRecordRepository = mock(PaymentRecordRepository.class);
            OrderRepository orderRepository = mock(OrderRepository.class);
            OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
            PaymentProviderClient providerClient = mock(PaymentProviderClient.class);
            EntitlementService entitlementService = mock(EntitlementService.class);
            TeacherEarningService teacherEarningService = mock(TeacherEarningService.class);
            PaymentRecord paymentRecord = paymentRecord(status);
            when(paymentRecordRepository.findLockedById(10L)).thenReturn(Optional.of(paymentRecord));
            LocalDateTime paidAt = paymentRecord.getPaidAt();

            PaymentService paymentService = service(
                    providerClient,
                    paymentRecordRepository,
                    orderRepository,
                    orderItemRepository,
                    entitlementService,
                    teacherEarningService);

            paymentService.markPaymentPaid(paymentRecord, "REPLAY", "PROVIDER_PAID");

            assertThat(paymentRecord.getPaymentStatus()).isEqualTo(status);
            assertThat(paymentRecord.getProviderStatus()).isEqualTo("EXISTING_" + status.name());
            assertThat(paymentRecord.getPaymentMethod()).isEqualTo("EXISTING_METHOD");
            assertThat(paymentRecord.getPaidAt()).isEqualTo(paidAt);
            verifyNoInteractions(orderRepository, entitlementService, teacherEarningService);
        }
    }

    @Test
    void applyProviderPaymentStatusMarksMatchingPendingStripePaymentPaid() {
        PaymentRecordRepository paymentRecordRepository = mock(PaymentRecordRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        PaymentProviderClient providerClient = mock(PaymentProviderClient.class);
        EntitlementService entitlementService = mock(EntitlementService.class);
        TeacherEarningService teacherEarningService = mock(TeacherEarningService.class);
        PaymentRecord paymentRecord = pendingPaymentRecord();
        paymentRecord.setProviderName("stripe");
        paymentRecord.setProviderReference("cs_test_paid");
        when(paymentRecordRepository.findLockedByProviderNameAndProviderReference("stripe", "cs_test_paid"))
                .thenReturn(Optional.of(paymentRecord));
        when(paymentRecordRepository.findLockedById(10L)).thenReturn(Optional.of(paymentRecord));

        PaymentService paymentService = service(
                providerClient,
                paymentRecordRepository,
                orderRepository,
                orderItemRepository,
                entitlementService,
                teacherEarningService);

        Optional<StripeWebhookResultResponse> response = paymentService.applyProviderPaymentStatus(
                "stripe",
                "cs_test_paid",
                new PaymentProviderStatusResult(
                        true,
                        "stripe",
                        "cs_test_paid",
                        PaymentStatus.PAID,
                        "CHECKOUT_SESSION_COMPLETED:COMPLETE:PAID",
                        "Paid via Stripe webhook."));

        assertThat(response).isPresent();
        assertThat(response.orElseThrow().processed()).isTrue();
        assertThat(response.orElseThrow().paymentId()).isEqualTo(10L);
        assertThat(paymentRecord.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paymentRecord.getProviderStatus()).isEqualTo("CHECKOUT_SESSION_COMPLETED:COMPLETE:PAID");
        verify(entitlementService).grantAccessAfterPayment(paymentRecord.getOrder(), paymentRecord);
        verify(teacherEarningService).createEarningsForOrder(500L);
    }

    @Test
    void applyProviderPaymentStatusIgnoresUnknownProviderReference() {
        PaymentRecordRepository paymentRecordRepository = mock(PaymentRecordRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        PaymentProviderClient providerClient = mock(PaymentProviderClient.class);
        EntitlementService entitlementService = mock(EntitlementService.class);
        TeacherEarningService teacherEarningService = mock(TeacherEarningService.class);
        when(paymentRecordRepository.findLockedByProviderNameAndProviderReference("stripe", "cs_missing"))
                .thenReturn(Optional.empty());

        PaymentService paymentService = service(
                providerClient,
                paymentRecordRepository,
                orderRepository,
                orderItemRepository,
                entitlementService,
                teacherEarningService);

        Optional<StripeWebhookResultResponse> response = paymentService.applyProviderPaymentStatus(
                "stripe",
                "cs_missing",
                new PaymentProviderStatusResult(
                        true,
                        "stripe",
                        "cs_missing",
                        PaymentStatus.PAID,
                        "CHECKOUT_SESSION_COMPLETED:COMPLETE:PAID",
                        "Paid via Stripe webhook."));

        assertThat(response).isEmpty();
        verifyNoInteractions(orderRepository, entitlementService, teacherEarningService);
    }

    private void assertTerminalProviderStatusIsSynchronized(PaymentStatus terminalStatus) {
        PaymentRecordRepository paymentRecordRepository = mock(PaymentRecordRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        PaymentProviderClient providerClient = mock(PaymentProviderClient.class);
        EntitlementService entitlementService = mock(EntitlementService.class);
        TeacherEarningService teacherEarningService = mock(TeacherEarningService.class);
        PaymentRecord paymentRecord = pendingPaymentRecord();

        when(paymentRecordRepository.findByIdAndStudentUserId(10L, 99L)).thenReturn(Optional.of(paymentRecord));
        when(paymentRecordRepository.findLockedByIdAndStudentUserId(10L, 99L)).thenReturn(Optional.of(paymentRecord));
        when(paymentRecordRepository.findLockedById(10L)).thenReturn(Optional.of(paymentRecord));
        when(orderItemRepository.findByOrderIdOrderByCreatedAtAsc(500L)).thenReturn(List.of());
        when(providerClient.getPaymentStatus("provider-ref")).thenReturn(new PaymentProviderStatusResult(
                true,
                "provider",
                "provider-ref",
                terminalStatus,
                "PROVIDER_" + terminalStatus.name(),
                "Terminal payment status."));

        PaymentService paymentService = service(
                providerClient,
                paymentRecordRepository,
                orderRepository,
                orderItemRepository,
                entitlementService,
                teacherEarningService);

        StudentPaymentRecordResponse response = paymentService.verifyPaymentStatus(currentUser(), 10L);

        assertThat(response.paymentStatus()).isEqualTo(terminalStatus);
        assertThat(response.providerStatus()).isEqualTo("PROVIDER_" + terminalStatus.name());
        assertThat(paymentRecord.getPaymentStatus()).isEqualTo(terminalStatus);
        Order order = Objects.requireNonNull(paymentRecord.getOrder());
        assertThat(order.getPaymentStatus()).isEqualTo(terminalStatus);
        verify(orderRepository).save(order);
        verify(paymentRecordRepository).save(paymentRecord);
        verifyNoInteractions(entitlementService, teacherEarningService);
    }

    private PaymentService service(
            PaymentProviderClient providerClient,
            PaymentRecordRepository paymentRecordRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            EntitlementService entitlementService,
            TeacherEarningService teacherEarningService) {
        PaymentProviderService paymentProviderService = mock(PaymentProviderService.class);
        when(paymentProviderService.getClient()).thenReturn(providerClient);
        return new PaymentService(
                paymentRecordRepository,
                orderRepository,
                orderItemRepository,
                paymentProviderService,
                entitlementService,
                mock(CartService.class),
                teacherEarningService,
                mock(NotificationService.class),
                mock(AuditLogService.class),
                new com.example.aichinese.service.CommerceMoneyService());
    }

    private PaymentRecord pendingPaymentRecord() {
        return paymentRecord(PaymentStatus.PENDING);
    }

    private PaymentRecord paymentRecord(PaymentStatus paymentStatus) {
        User user = new User();
        user.setId(99L);
        user.setEmail("student@example.com");
        user.setDisplayName("Student Example");
        user.setPasswordHash("password-hash");
        user.setActive(true);

        StudentProfile student = new StudentProfile();
        student.setId(199L);
        student.setUser(user);

        Order order = new Order();
        order.setId(500L);
        order.setStudent(student);
        order.setOrderNumber("ORD-TEST");
        order.setPaymentStatus(paymentStatus);

        PaymentRecord paymentRecord = new PaymentRecord();
        paymentRecord.setId(10L);
        paymentRecord.setOrder(order);
        paymentRecord.setStudent(student);
        paymentRecord.setProviderName("provider");
        paymentRecord.setProviderReference("provider-ref");
        paymentRecord.setProviderStatus(paymentStatus == PaymentStatus.PENDING ? "CREATED" : "EXISTING_" + paymentStatus.name());
        paymentRecord.setPaymentStatus(paymentStatus);
        paymentRecord.setPaymentMethod(paymentStatus == PaymentStatus.PENDING ? null : "EXISTING_METHOD");
        paymentRecord.setPaidAt(paymentStatus == PaymentStatus.PAID ? LocalDateTime.now().minusDays(1) : null);
        paymentRecord.setAmount(new BigDecimal("29.90"));
        paymentRecord.setCurrencyCode("MYR");
        return paymentRecord;
    }

    private OrderItem orderItem(Order order, Long id) {
        OrderItem orderItem = new OrderItem();
        orderItem.setId(id);
        orderItem.setOrder(order);
        orderItem.setItemType("LESSON");
        orderItem.setItemId(100L + id);
        orderItem.setItemTitleSnapshot("Lesson " + id);
        orderItem.setQuantity(1);
        orderItem.setUnitPrice(new BigDecimal("10.00"));
        orderItem.setLineTotal(new BigDecimal("10.00"));
        return orderItem;
    }

    private SecurityUserDetails currentUser() {
        User user = new User();
        user.setId(99L);
        user.setEmail("student@example.com");
        user.setDisplayName("Student Example");
        user.setPasswordHash("password-hash");
        user.setActive(true);
        return new SecurityUserDetails(user);
    }
}

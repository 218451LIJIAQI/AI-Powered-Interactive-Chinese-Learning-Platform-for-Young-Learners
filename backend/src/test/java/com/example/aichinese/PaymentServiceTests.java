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

import com.example.aichinese.dto.commerce.CheckoutResponse;
import com.example.aichinese.dto.commerce.PaymentProviderCheckoutResult;
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
import com.example.aichinese.service.CommerceMoneyService;
import com.example.aichinese.service.EntitlementService;
import com.example.aichinese.service.NotificationService;
import com.example.aichinese.service.PaymentProviderService;
import com.example.aichinese.service.PaymentService;
import com.example.aichinese.service.TeacherEarningService;

/**
 * Unit coverage for {@code PaymentService} - pins the lifecycle
 * state machine (PENDING -> SUCCESS / FAILED), webhook idempotency
 * under retry, cancellation synchronisation with provider terminal
 * states, and the entitlement-creation side-effect on success.
 */
class PaymentServiceTests {

    private static final long PAYMENT_ID = 10L;
    private static final long SECOND_PAYMENT_ID = 11L;
    private static final long STUDENT_USER_ID = 99L;
    private static final long STUDENT_PROFILE_ID = 199L;
    private static final long ORDER_ID = 500L;
    private static final long SECOND_ORDER_ID = 501L;
    private static final long FIRST_ORDER_ITEM_ID = 1L;
    private static final long SECOND_ORDER_ITEM_ID = 2L;
    private static final String STUDENT_EMAIL = "student@example.com";
    private static final String STUDENT_NAME = "Student Example";
    private static final String PASSWORD_HASH = "password-hash";
    private static final String ORDER_NUMBER = "ORD-TEST";
    private static final String SECOND_ORDER_NUMBER = "ORD-TEST-2";
    private static final String CURRENCY = "MYR";
    private static final String PROVIDER_NAME = "provider";
    private static final String STRIPE_PROVIDER = "stripe";
    private static final String PROVIDER_REFERENCE = "provider-ref";
    private static final String STRIPE_PAID_REFERENCE = "cs_test_paid";
    private static final String MISSING_PROVIDER_REFERENCE = "cs_missing";
    private static final String PROVIDER_CREATED_STATUS = "CREATED";
    private static final String EXISTING_PAYMENT_METHOD = "EXISTING_METHOD";
    private static final String PAYMENT_REPLAY_REFERENCE = "REPLAY";
    private static final String PROVIDER_PAID_STATUS = "PROVIDER_PAID";
    private static final String STRIPE_PAID_PROVIDER_STATUS = "CHECKOUT_SESSION_COMPLETED:COMPLETE:PAID";
    private static final String PAYMENT_UNAVAILABLE_MESSAGE =
            "Payment service is currently unavailable. Please try again later.";
    private static final String SUCCESS_URL = "https://app.example.com/payment/success?paymentId=10";
    private static final String CANCEL_URL = "https://app.example.com/payment/cancel?paymentId=10&cancelled=true";
    private static final BigDecimal RAW_ORDER_AMOUNT = new BigDecimal("29.999");
    private static final BigDecimal ORDER_AMOUNT = new BigDecimal("29.90");
    private static final BigDecimal NORMALIZED_ORDER_AMOUNT = new BigDecimal("30.00");
    private static final BigDecimal ORDER_ITEM_AMOUNT = new BigDecimal("10.00");

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
        ServiceFixture fixture = fixture();
        Order order = new Order();
        StudentProfile student = new StudentProfile();

        order.setTotalAmount(RAW_ORDER_AMOUNT);
        order.setCurrencyCode(CURRENCY);
        when(fixture.providerClient().providerName()).thenReturn(PROVIDER_NAME);
        when(fixture.paymentRecordRepository().save(any(PaymentRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentRecord paymentRecord = fixture.service().createPendingPayment(order, student);

        assertThat(paymentRecord.getAmount()).isEqualByComparingTo(NORMALIZED_ORDER_AMOUNT);
        assertThat(paymentRecord.getCurrencyCode()).isEqualTo(CURRENCY);
        assertThat(paymentRecord.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void startCheckoutMarksPaymentFailedWhenProviderCannotCreateSession() {
        PaymentRecordRepository paymentRecordRepository = mock(PaymentRecordRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        PaymentProviderService paymentProviderService = mock(PaymentProviderService.class);
        PaymentProviderClient providerClient = mock(PaymentProviderClient.class);
        EntitlementService entitlementService = mock(EntitlementService.class);
        CartService cartService = mock(CartService.class);
        TeacherEarningService teacherEarningService = mock(TeacherEarningService.class);
        NotificationService notificationService = mock(NotificationService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        PaymentRecord paymentRecord = pendingPaymentRecord();
        paymentRecord.setProviderName(STRIPE_PROVIDER);
        Order order = Objects.requireNonNull(paymentRecord.getOrder());
        List<OrderItem> orderItems = List.of(orderItem(order, FIRST_ORDER_ITEM_ID));

        when(paymentProviderService.getClient()).thenReturn(providerClient);
        when(providerClient.createCheckoutSession(any()))
                .thenReturn(PaymentProviderCheckoutResult.unavailable(
                        STRIPE_PROVIDER,
                        PAYMENT_UNAVAILABLE_MESSAGE));
        when(paymentRecordRepository.save(any(PaymentRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentService paymentService = new PaymentService(
                paymentRecordRepository,
                orderRepository,
                orderItemRepository,
                paymentProviderService,
                entitlementService,
                cartService,
                teacherEarningService,
                notificationService,
                auditLogService,
                new CommerceMoneyService());

        CheckoutResponse response = paymentService.startCheckout(
                currentUser(),
                paymentRecord.getStudent(),
                order,
                paymentRecord,
                orderItems,
                SUCCESS_URL,
                CANCEL_URL);

        assertThat(response.paymentAvailable()).isFalse();
        assertThat(response.checkoutUrl()).isNull();
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.providerStatus()).isEqualTo(PaymentProviderCheckoutResult.UNAVAILABLE_PROVIDER_STATUS);
        assertThat(paymentRecord.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(orderRepository).save(order);
        verifyNoInteractions(entitlementService, cartService, teacherEarningService, notificationService);
    }

    @Test
    void verifyPaymentStatusDoesNotPollProviderForNonPendingStatuses() {
        // Iterate over the canonical PaymentStatus.TERMINAL set rather than
        // re-listing its members so the coverage self-updates if a new
        // terminal status is ever introduced on the enum.
        for (PaymentStatus status : PaymentStatus.TERMINAL) {
            ServiceFixture fixture = fixture();
            PaymentRecord paymentRecord = paymentRecord(status);

            when(fixture.paymentRecordRepository().findByIdAndStudentUserId(PAYMENT_ID, STUDENT_USER_ID))
                    .thenReturn(Optional.of(paymentRecord));
            when(fixture.orderItemRepository().findByOrderIdOrderByCreatedAtAsc(ORDER_ID)).thenReturn(List.of());

            StudentPaymentRecordResponse response = fixture.service().verifyPaymentStatus(currentUser(), PAYMENT_ID);

            assertThat(response.paymentStatus()).isEqualTo(status);
            assertThat(response.providerStatus()).isEqualTo(existingProviderStatus(status));
            assertThat(paymentRecord.getPaymentStatus()).isEqualTo(status);
            verifyNoInteractions(
                    fixture.providerClient(),
                    fixture.orderRepository(),
                    fixture.entitlementService(),
                    fixture.teacherEarningService());
            verify(fixture.paymentRecordRepository(), never()).save(paymentRecord);
        }
    }

    @Test
    void studentPaymentListBatchLoadsOrderItemsForPage() {
        ServiceFixture fixture = fixture();
        PaymentRecord firstPayment = paymentRecord(PaymentStatus.PAID);
        PaymentRecord secondPayment = paymentRecord(PaymentStatus.PAID);
        secondPayment.setId(SECOND_PAYMENT_ID);
        secondPayment.getOrder().setId(SECOND_ORDER_ID);
        secondPayment.getOrder().setOrderNumber(SECOND_ORDER_NUMBER);
        OrderItem firstOrderItem = orderItem(firstPayment.getOrder(), FIRST_ORDER_ITEM_ID);
        OrderItem secondOrderItem = orderItem(secondPayment.getOrder(), SECOND_ORDER_ITEM_ID);

        when(fixture.paymentRecordRepository().findByStudentUserIdOrderByCreatedAtDesc(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(firstPayment, secondPayment)));
        when(fixture.orderItemRepository().findByOrderIdInOrderByOrderIdAscCreatedAtAsc(List.of(
                ORDER_ID,
                SECOND_ORDER_ID)))
                .thenReturn(List.of(firstOrderItem, secondOrderItem));

        PageResponse<StudentPaymentRecordResponse> response = fixture.service()
                .getStudentPayments(currentUser(), 0, 20);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).items()).hasSize(1);
        assertThat(response.items().get(1).items()).hasSize(1);
        verify(fixture.orderItemRepository(), times(1)).findByOrderIdInOrderByOrderIdAscCreatedAtAsc(List.of(
                ORDER_ID,
                SECOND_ORDER_ID));
        verify(fixture.orderItemRepository(), never()).findByOrderIdOrderByCreatedAtAsc(ORDER_ID);
        verify(fixture.orderItemRepository(), never()).findByOrderIdOrderByCreatedAtAsc(SECOND_ORDER_ID);
    }

    @Test
    void markPaymentPaidDoesNotReviveNonPendingStatuses() {
        // Iterate over the canonical PaymentStatus.TERMINAL set so this
        // regression guard automatically expands when a new terminal status
        // is introduced (e.g. a hypothetical PARTIALLY_REFUNDED).
        for (PaymentStatus status : PaymentStatus.TERMINAL) {
            ServiceFixture fixture = fixture();
            PaymentRecord paymentRecord = paymentRecord(status);
            when(fixture.paymentRecordRepository().findLockedById(PAYMENT_ID)).thenReturn(Optional.of(paymentRecord));
            LocalDateTime paidAt = paymentRecord.getPaidAt();

            fixture.service().markPaymentPaid(paymentRecord, PAYMENT_REPLAY_REFERENCE, PROVIDER_PAID_STATUS);

            assertThat(paymentRecord.getPaymentStatus()).isEqualTo(status);
            assertThat(paymentRecord.getProviderStatus()).isEqualTo(existingProviderStatus(status));
            assertThat(paymentRecord.getPaymentMethod()).isEqualTo(EXISTING_PAYMENT_METHOD);
            assertThat(paymentRecord.getPaidAt()).isEqualTo(paidAt);
            verifyNoInteractions(
                    fixture.orderRepository(),
                    fixture.entitlementService(),
                    fixture.teacherEarningService());
        }
    }

    @Test
    void applyProviderPaymentStatusMarksMatchingPendingStripePaymentPaid() {
        ServiceFixture fixture = fixture();
        PaymentRecord paymentRecord = pendingPaymentRecord();
        paymentRecord.setProviderName(STRIPE_PROVIDER);
        paymentRecord.setProviderReference(STRIPE_PAID_REFERENCE);
        when(fixture.paymentRecordRepository().findLockedByProviderNameAndProviderReference(
                STRIPE_PROVIDER,
                STRIPE_PAID_REFERENCE))
                .thenReturn(Optional.of(paymentRecord));
        when(fixture.paymentRecordRepository().findLockedById(PAYMENT_ID)).thenReturn(Optional.of(paymentRecord));

        Optional<StripeWebhookResultResponse> response = fixture.service().applyProviderPaymentStatus(
                STRIPE_PROVIDER,
                STRIPE_PAID_REFERENCE,
                new PaymentProviderStatusResult(
                        true,
                        STRIPE_PROVIDER,
                        STRIPE_PAID_REFERENCE,
                        PaymentStatus.PAID,
                        STRIPE_PAID_PROVIDER_STATUS,
                        "Paid via Stripe webhook."));

        assertThat(response).isPresent();
        assertThat(response.orElseThrow().processed()).isTrue();
        assertThat(response.orElseThrow().paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(paymentRecord.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paymentRecord.getProviderStatus()).isEqualTo(STRIPE_PAID_PROVIDER_STATUS);
        verify(fixture.entitlementService()).grantAccessAfterPayment(paymentRecord.getOrder(), paymentRecord);
        verify(fixture.teacherEarningService()).createEarningsForOrder(ORDER_ID);
    }

    @Test
    void applyProviderPaymentStatusIgnoresUnknownProviderReference() {
        ServiceFixture fixture = fixture();
        when(fixture.paymentRecordRepository().findLockedByProviderNameAndProviderReference(
                STRIPE_PROVIDER,
                MISSING_PROVIDER_REFERENCE))
                .thenReturn(Optional.empty());

        Optional<StripeWebhookResultResponse> response = fixture.service().applyProviderPaymentStatus(
                STRIPE_PROVIDER,
                MISSING_PROVIDER_REFERENCE,
                new PaymentProviderStatusResult(
                        true,
                        STRIPE_PROVIDER,
                        MISSING_PROVIDER_REFERENCE,
                        PaymentStatus.PAID,
                        STRIPE_PAID_PROVIDER_STATUS,
                        "Paid via Stripe webhook."));

        assertThat(response).isEmpty();
        verifyNoInteractions(fixture.orderRepository(), fixture.entitlementService(), fixture.teacherEarningService());
    }

    private void assertTerminalProviderStatusIsSynchronized(PaymentStatus terminalStatus) {
        ServiceFixture fixture = fixture();
        PaymentRecord paymentRecord = pendingPaymentRecord();

        when(fixture.paymentRecordRepository().findByIdAndStudentUserId(PAYMENT_ID, STUDENT_USER_ID))
                .thenReturn(Optional.of(paymentRecord));
        when(fixture.paymentRecordRepository().findLockedByIdAndStudentUserId(PAYMENT_ID, STUDENT_USER_ID))
                .thenReturn(Optional.of(paymentRecord));
        when(fixture.paymentRecordRepository().findLockedById(PAYMENT_ID)).thenReturn(Optional.of(paymentRecord));
        when(fixture.orderItemRepository().findByOrderIdOrderByCreatedAtAsc(ORDER_ID)).thenReturn(List.of());
        when(fixture.providerClient().getPaymentStatus(PROVIDER_REFERENCE)).thenReturn(new PaymentProviderStatusResult(
                true,
                PROVIDER_NAME,
                PROVIDER_REFERENCE,
                terminalStatus,
                "PROVIDER_" + terminalStatus.name(),
                "Terminal payment status."));

        StudentPaymentRecordResponse response = fixture.service().verifyPaymentStatus(currentUser(), PAYMENT_ID);

        assertThat(response.paymentStatus()).isEqualTo(terminalStatus);
        assertThat(response.providerStatus()).isEqualTo("PROVIDER_" + terminalStatus.name());
        assertThat(paymentRecord.getPaymentStatus()).isEqualTo(terminalStatus);
        Order order = Objects.requireNonNull(paymentRecord.getOrder());
        assertThat(order.getPaymentStatus()).isEqualTo(terminalStatus);
        verify(fixture.orderRepository()).save(order);
        verify(fixture.paymentRecordRepository()).save(paymentRecord);
        verifyNoInteractions(fixture.entitlementService(), fixture.teacherEarningService());
    }

    private ServiceFixture fixture() {
        return new ServiceFixture(
                mock(PaymentRecordRepository.class),
                mock(OrderRepository.class),
                mock(OrderItemRepository.class),
                mock(PaymentProviderClient.class),
                mock(EntitlementService.class),
                mock(TeacherEarningService.class));
    }

    private static PaymentService service(
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
                new CommerceMoneyService());
    }

    private record ServiceFixture(
            PaymentRecordRepository paymentRecordRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentProviderClient providerClient,
            EntitlementService entitlementService,
            TeacherEarningService teacherEarningService) {

        PaymentService service() {
            return PaymentServiceTests.service(
                    providerClient,
                    paymentRecordRepository,
                    orderRepository,
                    orderItemRepository,
                    entitlementService,
                    teacherEarningService);
        }
    }

    private PaymentRecord pendingPaymentRecord() {
        return paymentRecord(PaymentStatus.PENDING);
    }

    private PaymentRecord paymentRecord(PaymentStatus paymentStatus) {
        User user = new User();
        user.setId(STUDENT_USER_ID);
        user.setEmail(STUDENT_EMAIL);
        user.setDisplayName(STUDENT_NAME);
        user.setPasswordHash(PASSWORD_HASH);
        user.setActive(true);

        StudentProfile student = new StudentProfile();
        student.setId(STUDENT_PROFILE_ID);
        student.setUser(user);

        Order order = new Order();
        order.setId(ORDER_ID);
        order.setStudent(student);
        order.setOrderNumber(ORDER_NUMBER);
        order.setPaymentStatus(paymentStatus);
        order.setTotalAmount(ORDER_AMOUNT);
        order.setCurrencyCode(CURRENCY);

        PaymentRecord paymentRecord = new PaymentRecord();
        paymentRecord.setId(PAYMENT_ID);
        paymentRecord.setOrder(order);
        paymentRecord.setStudent(student);
        paymentRecord.setProviderName(PROVIDER_NAME);
        paymentRecord.setProviderReference(PROVIDER_REFERENCE);
        paymentRecord.setProviderStatus(paymentStatus == PaymentStatus.PENDING
                ? PROVIDER_CREATED_STATUS
                : existingProviderStatus(paymentStatus));
        paymentRecord.setPaymentStatus(paymentStatus);
        paymentRecord.setPaymentMethod(paymentStatus == PaymentStatus.PENDING ? null : EXISTING_PAYMENT_METHOD);
        paymentRecord.setPaidAt(paymentStatus == PaymentStatus.PAID ? LocalDateTime.now().minusDays(1) : null);
        paymentRecord.setAmount(ORDER_AMOUNT);
        paymentRecord.setCurrencyCode(CURRENCY);
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
        orderItem.setUnitPrice(ORDER_ITEM_AMOUNT);
        orderItem.setLineTotal(ORDER_ITEM_AMOUNT);
        return orderItem;
    }

    private SecurityUserDetails currentUser() {
        User user = new User();
        user.setId(STUDENT_USER_ID);
        user.setEmail(STUDENT_EMAIL);
        user.setDisplayName(STUDENT_NAME);
        user.setPasswordHash(PASSWORD_HASH);
        user.setActive(true);
        return new SecurityUserDetails(user);
    }

    private static String existingProviderStatus(PaymentStatus status) {
        return "EXISTING_" + status.name();
    }
}

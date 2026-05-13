package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.commerce.CartItemResponse;
import com.example.aichinese.dto.commerce.CheckoutResponse;
import com.example.aichinese.dto.commerce.PaymentProviderCheckoutResult;
import com.example.aichinese.dto.commerce.PaymentProviderStatusResult;
import com.example.aichinese.dto.commerce.PaymentRecordResponse;
import com.example.aichinese.entity.Order;
import com.example.aichinese.entity.OrderItem;
import com.example.aichinese.entity.PaymentRecord;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.enums.PaymentStatus;
import com.example.aichinese.provider.PaymentProviderClient;
import com.example.aichinese.repository.OrderItemRepository;
import com.example.aichinese.repository.OrderRepository;
import com.example.aichinese.repository.PaymentRecordRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.model.CommerceCheckout;

@Service
public class PaymentService {

    private final PaymentRecordRepository paymentRecordRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentProviderService paymentProviderService;
    private final EntitlementService entitlementService;
    private final CartService cartService;
    private final TeacherEarningService teacherEarningService;
    private final SubscriptionService subscriptionService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public PaymentService(
            PaymentRecordRepository paymentRecordRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentProviderService paymentProviderService,
            EntitlementService entitlementService,
            CartService cartService,
            TeacherEarningService teacherEarningService,
            SubscriptionService subscriptionService,
            NotificationService notificationService,
            AuditLogService auditLogService) {
        this.paymentRecordRepository = paymentRecordRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentProviderService = paymentProviderService;
        this.entitlementService = entitlementService;
        this.cartService = cartService;
        this.teacherEarningService = teacherEarningService;
        this.subscriptionService = subscriptionService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public PaymentRecord createPendingPayment(Order order, StudentProfile student) {
        PaymentProviderClient providerClient = paymentProviderService.getClient();

        PaymentRecord paymentRecord = new PaymentRecord();
        paymentRecord.setOrder(order);
        paymentRecord.setStudent(student);
        paymentRecord.setProviderName(providerClient.providerName());
        paymentRecord.setAmount(order.getTotalAmount());
        paymentRecord.setCurrencyCode(order.getCurrencyCode());
        paymentRecord.setPaymentStatus(PaymentStatus.PENDING);
        paymentRecord.setProviderStatus("CREATED");
        return paymentRecordRepository.save(paymentRecord);
    }

    @Transactional
    public CheckoutResponse startCheckout(
            SecurityUserDetails currentUser,
            StudentProfile student,
            Order order,
            PaymentRecord paymentRecord,
            List<OrderItem> orderItems,
            String successUrl,
            String cancelUrl) {
        PaymentProviderClient providerClient = paymentProviderService.getClient();
        CommerceCheckout checkout = new CommerceCheckout(
                student,
                order,
                paymentRecord,
                orderItems,
                order.getTotalAmount(),
                order.getCurrencyCode(),
                successUrl,
                cancelUrl);

        PaymentProviderCheckoutResult result = providerClient.createCheckoutSession(checkout);
        paymentRecord.setProviderReference(result.sessionReference());
        paymentRecord.setProviderStatus(result.providerStatus());
        paymentRecordRepository.save(paymentRecord);

        if (result.available() && "MOCK_CREATED".equals(result.providerStatus())) {
            markPaymentPaid(paymentRecord, "MOCK", result.providerStatus());
        }

        auditLogService.record(
                currentUser.getUser(),
                "PAYMENT_SESSION_CREATED",
                "PAYMENT_RECORD",
                paymentRecord.getId(),
                "Payment session created for order " + order.getOrderNumber());

        return new CheckoutResponse(
                order.getId(),
                order.getOrderNumber(),
                paymentRecord.getId(),
                paymentRecord.getPaymentStatus(),
                paymentRecord.getAmount(),
                paymentRecord.getCurrencyCode(),
                result.checkoutUrl(),
                paymentRecord.getProviderName(),
                paymentRecord.getProviderStatus(),
                result.available(),
                result.message(),
                mapOrderItems(orderItems));
    }

    @Transactional
    public PaymentRecordResponse verifyPaymentStatus(SecurityUserDetails currentUser, Long paymentId) {
        PaymentRecord paymentRecord = paymentRecordRepository.findByIdAndStudentUserId(paymentId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Payment record was not found."));

        if (paymentRecord.getPaymentStatus() == PaymentStatus.PAID || paymentRecord.getPaymentStatus() == PaymentStatus.REFUNDED) {
            return mapPaymentResponse(paymentRecord);
        }

        PaymentProviderStatusResult providerStatus = paymentProviderService.getClient()
                .getPaymentStatus(paymentRecord.getProviderReference());

        if (providerStatus.available() && providerStatus.paymentStatus() == PaymentStatus.PAID) {
            markPaymentPaid(paymentRecord, null, providerStatus.providerStatus());
        } else if (!providerStatus.available()) {
            paymentRecord.setProviderStatus("UNAVAILABLE");
        } else {
            paymentRecord.setProviderStatus(providerStatus.providerStatus());
        }

        paymentRecordRepository.save(paymentRecord);
        return mapPaymentResponse(paymentRecord);
    }

    @Transactional(readOnly = true)
    public List<PaymentRecordResponse> getStudentPayments(SecurityUserDetails currentUser) {
        return paymentRecordRepository.findByStudentUserIdOrderByCreatedAtDesc(currentUser.getUserId()).stream()
                .map(this::mapPaymentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentRecordResponse> getAllPayments() {
        // Use a DB-side ORDER BY so we don't have to sort the entire result set
        // in-memory after fetching.
        return paymentRecordRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapPaymentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PaymentRecordResponse getAdminPayment(Long paymentId) {
        PaymentRecord paymentRecord = paymentRecordRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Payment record was not found."));
        return mapPaymentResponse(paymentRecord);
    }

    @Transactional(readOnly = true)
    public PaymentRecordResponse getStudentPayment(SecurityUserDetails currentUser, Long paymentId) {
        PaymentRecord paymentRecord = paymentRecordRepository.findByIdAndStudentUserId(paymentId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Payment record was not found."));
        return mapPaymentResponse(paymentRecord);
    }

    @Transactional
    public void markPaymentPaid(PaymentRecord paymentRecord, String paymentMethod, String providerStatus) {
        paymentRecord.setPaymentStatus(PaymentStatus.PAID);
        paymentRecord.setPaymentMethod(paymentMethod);
        paymentRecord.setProviderStatus(providerStatus);
        paymentRecord.setPaidAt(LocalDateTime.now());

        Order order = paymentRecord.getOrder();
        order.setPaymentStatus(PaymentStatus.PAID);
        orderRepository.save(order);

        entitlementService.grantAccessAfterPayment(order, paymentRecord);
        teacherEarningService.createEarningsForOrder(order.getId());
        subscriptionService.activateSubscription(paymentRecord.getId());

        if (paymentRecord.getStudent() != null && paymentRecord.getStudent().getUser() != null) {
            notificationService.createNotification(
                    paymentRecord.getStudent().getUser(),
                    "Payment successful",
                    "Your payment was successful and access has been unlocked.",
                    "/student/payments");
        }
    }

    private PaymentRecordResponse mapPaymentResponse(PaymentRecord paymentRecord) {
        List<OrderItem> orderItems = paymentRecord.getOrder() != null
                ? orderItemRepository.findByOrderIdOrderByCreatedAtAsc(paymentRecord.getOrder().getId())
                : List.of();
        return new PaymentRecordResponse(
                paymentRecord.getId(),
                paymentRecord.getOrder() != null ? paymentRecord.getOrder().getId() : null,
                paymentRecord.getOrder() != null ? paymentRecord.getOrder().getOrderNumber() : null,
                paymentRecord.getStudent() != null ? paymentRecord.getStudent().getId() : null,
                paymentRecord.getStudent() != null && paymentRecord.getStudent().getUser() != null
                        ? paymentRecord.getStudent().getUser().getDisplayName()
                        : null,
                paymentRecord.getStudent() != null && paymentRecord.getStudent().getUser() != null
                        ? paymentRecord.getStudent().getUser().getEmail()
                        : null,
                paymentRecord.getProviderName(),
                paymentRecord.getProviderReference(),
                paymentRecord.getAmount(),
                paymentRecord.getCurrencyCode(),
                paymentRecord.getPaymentStatus(),
                paymentRecord.getPaymentMethod(),
                paymentRecord.getProviderStatus(),
                paymentRecord.getPaidAt(),
                paymentRecord.getCreatedAt(),
                mapOrderItems(orderItems));
    }

    private List<CartItemResponse> mapOrderItems(List<OrderItem> orderItems) {
        return orderItems.stream()
                .map(orderItem -> new CartItemResponse(
                        orderItem.getId(),
                        orderItem.getItemType(),
                        orderItem.getItemId(),
                        orderItem.getItemTitleSnapshot(),
                        orderItem.getUnitPrice(),
                        orderItem.getOrder().getCurrencyCode(),
                        orderItem.getQuantity(),
                        orderItem.getLineTotal(),
                        orderItem.getTeacher() != null))
                .toList();
    }
}

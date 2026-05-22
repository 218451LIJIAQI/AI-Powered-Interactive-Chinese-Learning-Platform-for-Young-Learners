package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.commerce.AdminPaymentRecordResponse;
import com.example.aichinese.dto.commerce.CartItemResponse;
import com.example.aichinese.dto.commerce.CheckoutResponse;
import com.example.aichinese.dto.commerce.PaymentProviderCheckoutResult;
import com.example.aichinese.dto.commerce.PaymentProviderStatusResult;
import com.example.aichinese.dto.commerce.StudentPaymentRecordResponse;
import com.example.aichinese.dto.commerce.StripeWebhookResultResponse;
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
import com.example.aichinese.util.PaginationUtils;

@Service
public class PaymentService {

    private final PaymentRecordRepository paymentRecordRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentProviderService paymentProviderService;
    private final EntitlementService entitlementService;
    private final CartService cartService;
    private final TeacherEarningService teacherEarningService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final CommerceMoneyService commerceMoneyService;

    public PaymentService(
            PaymentRecordRepository paymentRecordRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentProviderService paymentProviderService,
            EntitlementService entitlementService,
            CartService cartService,
            TeacherEarningService teacherEarningService,
            NotificationService notificationService,
            AuditLogService auditLogService,
            CommerceMoneyService commerceMoneyService) {
        this.paymentRecordRepository = paymentRecordRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentProviderService = paymentProviderService;
        this.entitlementService = entitlementService;
        this.cartService = cartService;
        this.teacherEarningService = teacherEarningService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.commerceMoneyService = commerceMoneyService;
    }

    @Transactional
    public PaymentRecord createPendingPayment(Order order, StudentProfile student) {
        PaymentProviderClient providerClient = paymentProviderService.getClient();

        PaymentRecord paymentRecord = new PaymentRecord();
        paymentRecord.setOrder(order);
        paymentRecord.setStudent(student);
        paymentRecord.setProviderName(providerClient.providerName());
        paymentRecord.setAmount(commerceMoneyService.normalize(order.getTotalAmount()));
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
    public StudentPaymentRecordResponse verifyPaymentStatus(SecurityUserDetails currentUser, Long paymentId) {
        PaymentRecord paymentRecord = paymentRecordRepository.findByIdAndStudentUserId(paymentId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Payment record was not found."));

        if (paymentRecord.getPaymentStatus() != PaymentStatus.PENDING) {
            return mapStudentPaymentResponse(paymentRecord);
        }

        PaymentProviderStatusResult providerStatus = paymentProviderService.getClient()
                .getPaymentStatus(paymentRecord.getProviderReference());

        PaymentRecord lockedPaymentRecord = paymentRecordRepository.findLockedByIdAndStudentUserId(paymentId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Payment record was not found."));

        if (lockedPaymentRecord.getPaymentStatus() != PaymentStatus.PENDING) {
            return mapStudentPaymentResponse(lockedPaymentRecord);
        }

        if (providerStatus.available() && providerStatus.paymentStatus() == PaymentStatus.PAID) {
            markPaymentPaid(lockedPaymentRecord, null, providerStatus.providerStatus());
        } else if (providerStatus.available()
                && (providerStatus.paymentStatus() == PaymentStatus.FAILED
                        || providerStatus.paymentStatus() == PaymentStatus.CANCELLED)) {
            lockedPaymentRecord.setPaymentStatus(providerStatus.paymentStatus());
            lockedPaymentRecord.setProviderStatus(providerStatus.providerStatus());
            if (lockedPaymentRecord.getOrder() != null) {
                lockedPaymentRecord.getOrder().setPaymentStatus(providerStatus.paymentStatus());
                orderRepository.save(lockedPaymentRecord.getOrder());
            }
        } else if (!providerStatus.available()) {
            lockedPaymentRecord.setProviderStatus("UNAVAILABLE");
        } else {
            lockedPaymentRecord.setProviderStatus(providerStatus.providerStatus());
        }

        paymentRecordRepository.save(lockedPaymentRecord);
        return mapStudentPaymentResponse(lockedPaymentRecord);
    }

    @Transactional
    public Optional<StripeWebhookResultResponse> applyProviderPaymentStatus(
            String providerName,
            String providerReference,
            PaymentProviderStatusResult providerStatus) {
        if (providerName == null || providerName.isBlank()
                || providerReference == null || providerReference.isBlank()
                || providerStatus == null || !providerStatus.available()) {
            return Optional.empty();
        }

        PaymentRecord lockedPaymentRecord = paymentRecordRepository
                .findLockedByProviderNameAndProviderReference(providerName, providerReference)
                .orElse(null);
        if (lockedPaymentRecord == null) {
            return Optional.empty();
        }

        if (lockedPaymentRecord.getPaymentStatus() == PaymentStatus.PENDING) {
            if (providerStatus.paymentStatus() == PaymentStatus.PAID) {
                markPaymentPaid(lockedPaymentRecord, providerName.toUpperCase(), providerStatus.providerStatus());
            } else if (providerStatus.paymentStatus() == PaymentStatus.FAILED
                    || providerStatus.paymentStatus() == PaymentStatus.CANCELLED) {
                lockedPaymentRecord.setPaymentStatus(providerStatus.paymentStatus());
                lockedPaymentRecord.setProviderStatus(providerStatus.providerStatus());
                if (lockedPaymentRecord.getOrder() != null) {
                    lockedPaymentRecord.getOrder().setPaymentStatus(providerStatus.paymentStatus());
                    orderRepository.save(lockedPaymentRecord.getOrder());
                }
                paymentRecordRepository.save(lockedPaymentRecord);
            } else {
                lockedPaymentRecord.setProviderStatus(providerStatus.providerStatus());
                paymentRecordRepository.save(lockedPaymentRecord);
            }
        }

        return Optional.of(new StripeWebhookResultResponse(
                null,
                null,
                lockedPaymentRecord.getId(),
                lockedPaymentRecord.getPaymentStatus(),
                lockedPaymentRecord.getProviderStatus(),
                true));
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentPaymentRecordResponse> getStudentPayments(SecurityUserDetails currentUser, int page, int size) {
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<PaymentRecord> paymentPage = paymentRecordRepository.findByStudentUserIdOrderByCreatedAtDesc(
                currentUser.getUserId(),
                PageRequest.of(safePage, safeSize));
        return mapStudentPaymentPage(paymentPage, safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminPaymentRecordResponse> getAllPayments(int page, int size) {
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<PaymentRecord> paymentPage = paymentRecordRepository.findAll(newestPageable(safePage, safeSize));
        return mapAdminPaymentPage(paymentPage, safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public AdminPaymentRecordResponse getAdminPayment(Long paymentId) {
        PaymentRecord paymentRecord = paymentRecordRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Payment record was not found."));
        return mapAdminPaymentResponse(paymentRecord);
    }

    @Transactional(readOnly = true)
    public StudentPaymentRecordResponse getStudentPayment(SecurityUserDetails currentUser, Long paymentId) {
        PaymentRecord paymentRecord = paymentRecordRepository.findByIdAndStudentUserId(paymentId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Payment record was not found."));
        return mapStudentPaymentResponse(paymentRecord);
    }

    @Transactional
    public void markPaymentPaid(PaymentRecord paymentRecord, String paymentMethod, String providerStatus) {
        PaymentRecord lockedPaymentRecord = paymentRecord.getId() == null
                ? paymentRecord
                : paymentRecordRepository.findLockedById(paymentRecord.getId()).orElse(paymentRecord);
        // Idempotency guard: if the payment is no longer PENDING this is a
        // redundant or invalid replay from a concurrent verifyPaymentStatus
        // invocation (e.g. the student opened two browser tabs and refreshed
        // payment status on each). Without this short-circuit we would
        // double-emit the "Payment successful" notification, re-run the
        // order/payment update for no reason, and burn provider quota
        // refreshing teacherEarningService side effects (already idempotent —
        // only the notification was not).
        if (lockedPaymentRecord.getPaymentStatus() != PaymentStatus.PENDING) {
            return;
        }

        lockedPaymentRecord.setPaymentStatus(PaymentStatus.PAID);
        lockedPaymentRecord.setPaymentMethod(paymentMethod);
        lockedPaymentRecord.setProviderStatus(providerStatus);
        lockedPaymentRecord.setPaidAt(LocalDateTime.now());

        Order order = lockedPaymentRecord.getOrder();
        order.setPaymentStatus(PaymentStatus.PAID);
        orderRepository.save(order);

        entitlementService.grantAccessAfterPayment(order, lockedPaymentRecord);
        teacherEarningService.createEarningsForOrder(order.getId());
        if ("CART".equalsIgnoreCase(order.getCheckoutSource())) {
            if (order.getStudent() != null && order.getStudent().getUser() != null) {
                cartService.clearCartIfExists(order.getStudent().getUser().getId());
            }
        }

        if (lockedPaymentRecord.getStudent() != null && lockedPaymentRecord.getStudent().getUser() != null) {
            notificationService.safeCreateNotification(
                    lockedPaymentRecord.getStudent().getUser(),
                    "Payment successful",
                    "Your payment was successful and access has been unlocked.",
                    "/student/profile");
        }
    }

    private Pageable newestPageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private PageResponse<StudentPaymentRecordResponse> mapStudentPaymentPage(Page<PaymentRecord> paymentPage, int page, int size) {
        Map<Long, List<OrderItem>> orderItemsByOrderId = loadOrderItemsByOrderId(paymentPage.getContent());
        return new PageResponse<>(
                paymentPage.getContent().stream()
                        .map(paymentRecord -> mapStudentPaymentResponse(paymentRecord, orderItemsByOrderId))
                        .toList(),
                page,
                size,
                paymentPage.getTotalElements(),
                PaginationUtils.totalPages(paymentPage.getTotalElements(), size));
    }

    private PageResponse<AdminPaymentRecordResponse> mapAdminPaymentPage(Page<PaymentRecord> paymentPage, int page, int size) {
        Map<Long, List<OrderItem>> orderItemsByOrderId = loadOrderItemsByOrderId(paymentPage.getContent());
        return new PageResponse<>(
                paymentPage.getContent().stream()
                        .map(paymentRecord -> mapAdminPaymentResponse(paymentRecord, orderItemsByOrderId))
                        .toList(),
                page,
                size,
                paymentPage.getTotalElements(),
                PaginationUtils.totalPages(paymentPage.getTotalElements(), size));
    }

    private StudentPaymentRecordResponse mapStudentPaymentResponse(PaymentRecord paymentRecord) {
        return mapStudentPaymentResponse(paymentRecord, Map.of());
    }

    private StudentPaymentRecordResponse mapStudentPaymentResponse(
            PaymentRecord paymentRecord,
            Map<Long, List<OrderItem>> preloadedOrderItemsByOrderId) {
        List<OrderItem> orderItems = resolveOrderItems(paymentRecord, preloadedOrderItemsByOrderId);
        return new StudentPaymentRecordResponse(
                paymentRecord.getId(),
                paymentRecord.getOrder() != null ? paymentRecord.getOrder().getId() : null,
                paymentRecord.getOrder() != null ? paymentRecord.getOrder().getOrderNumber() : null,
                paymentRecord.getProviderName(),
                paymentRecord.getAmount(),
                paymentRecord.getCurrencyCode(),
                paymentRecord.getPaymentStatus(),
                paymentRecord.getPaymentMethod(),
                paymentRecord.getProviderStatus(),
                paymentRecord.getPaidAt(),
                paymentRecord.getCreatedAt(),
                mapOrderItems(orderItems));
    }

    private AdminPaymentRecordResponse mapAdminPaymentResponse(PaymentRecord paymentRecord) {
        return mapAdminPaymentResponse(paymentRecord, Map.of());
    }

    private AdminPaymentRecordResponse mapAdminPaymentResponse(
            PaymentRecord paymentRecord,
            Map<Long, List<OrderItem>> preloadedOrderItemsByOrderId) {
        List<OrderItem> orderItems = resolveOrderItems(paymentRecord, preloadedOrderItemsByOrderId);
        return new AdminPaymentRecordResponse(
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
                paymentRecord.getAmount(),
                paymentRecord.getCurrencyCode(),
                paymentRecord.getPaymentStatus(),
                paymentRecord.getPaymentMethod(),
                paymentRecord.getProviderStatus(),
                paymentRecord.getPaidAt(),
                paymentRecord.getCreatedAt(),
                mapOrderItems(orderItems));
    }

    private Map<Long, List<OrderItem>> loadOrderItemsByOrderId(List<PaymentRecord> paymentRecords) {
        List<Long> orderIds = paymentRecords.stream()
                .map(PaymentRecord::getOrder)
                .filter(Objects::nonNull)
                .map(Order::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (orderIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<OrderItem>> grouped = new LinkedHashMap<>();
        orderIds.forEach(orderId -> grouped.put(orderId, new java.util.ArrayList<>()));
        orderItemRepository.findByOrderIdInOrderByOrderIdAscCreatedAtAsc(orderIds)
                .forEach(orderItem -> {
                    if (orderItem.getOrder() != null && orderItem.getOrder().getId() != null) {
                        grouped.computeIfAbsent(orderItem.getOrder().getId(), ignored -> new java.util.ArrayList<>())
                                .add(orderItem);
                    }
                });
        return Collections.unmodifiableMap(grouped);
    }

    private List<OrderItem> resolveOrderItems(
            PaymentRecord paymentRecord,
            Map<Long, List<OrderItem>> preloadedOrderItemsByOrderId) {
        if (paymentRecord.getOrder() == null || paymentRecord.getOrder().getId() == null) {
            return List.of();
        }

        Long orderId = paymentRecord.getOrder().getId();
        if (preloadedOrderItemsByOrderId.containsKey(orderId)) {
            return preloadedOrderItemsByOrderId.get(orderId);
        }
        return orderItemRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
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

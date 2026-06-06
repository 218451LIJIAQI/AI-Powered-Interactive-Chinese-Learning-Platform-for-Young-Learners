package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.example.aichinese.dto.commerce.CheckoutSessionRequest.CheckoutSource;
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

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String PROVIDER_STATUS_CREATED = "CREATED";
    private static final String PROVIDER_STATUS_UNAVAILABLE = "UNAVAILABLE";
    private static final String MOCK_CREATED_STATUS = "MOCK_CREATED";
    private static final String PAYMENT_RECORD_TARGET = "PAYMENT_RECORD";
    private static final String PAYMENT_SUCCESS_PATH = "/student/profile";

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
        this.paymentRecordRepository = Objects.requireNonNull(paymentRecordRepository, "paymentRecordRepository");
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository");
        this.orderItemRepository = Objects.requireNonNull(orderItemRepository, "orderItemRepository");
        this.paymentProviderService = Objects.requireNonNull(paymentProviderService, "paymentProviderService");
        this.entitlementService = Objects.requireNonNull(entitlementService, "entitlementService");
        this.cartService = Objects.requireNonNull(cartService, "cartService");
        this.teacherEarningService = Objects.requireNonNull(teacherEarningService, "teacherEarningService");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService");
        this.auditLogService = Objects.requireNonNull(auditLogService, "auditLogService");
        this.commerceMoneyService = Objects.requireNonNull(commerceMoneyService, "commerceMoneyService");
    }

    @Transactional
    public PaymentRecord createPendingPayment(Order order, StudentProfile student) {
        if (order == null || student == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Order and student are required to create a payment.");
        }
        PaymentProviderClient providerClient = paymentProviderService.getClient();

        PaymentRecord paymentRecord = new PaymentRecord();
        paymentRecord.setOrder(order);
        paymentRecord.setStudent(student);
        paymentRecord.setProviderName(providerClient.providerName());
        paymentRecord.setAmount(commerceMoneyService.normalizePersistableAmount(
                order.getTotalAmount(),
                "Payment amount is too large."));
        paymentRecord.setCurrencyCode(order.getCurrencyCode());
        paymentRecord.setPaymentStatus(PaymentStatus.PENDING);
        paymentRecord.setProviderStatus(PROVIDER_STATUS_CREATED);
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
        if (currentUser == null || student == null || order == null || paymentRecord == null || orderItems == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Checkout payment context is incomplete.");
        }
        if (order.getTotalAmount() == null
                || order.getCurrencyCode() == null
                || order.getCurrencyCode().isBlank()
                || successUrl == null
                || successUrl.isBlank()
                || cancelUrl == null
                || cancelUrl.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Checkout payment context is incomplete.");
        }
        PaymentProviderClient providerClient = paymentProviderService.getClient();
        paymentRecord.setProviderName(providerClient.providerName());
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
        if (result == null) {
            result = PaymentProviderCheckoutResult.unavailable(
                    providerClient.providerName(),
                    PaymentProviderClient.PAYMENT_UNAVAILABLE_MESSAGE);
        }
        paymentRecord.setProviderReference(result.sessionReference());
        paymentRecord.setProviderStatus(result.providerStatus());

        if (!result.available()) {
            markCheckoutUnavailable(paymentRecord, result);
            auditLogService.record(
                    currentUser,
                    "PAYMENT_SESSION_UNAVAILABLE",
                    PAYMENT_RECORD_TARGET,
                    paymentRecord.getId(),
                    "Payment session could not be created for order " + order.getOrderNumber());
            return checkoutResponse(order, paymentRecord, null, false, result.message(), orderItems);
        }

        paymentRecordRepository.save(paymentRecord);

        if (MOCK_CREATED_STATUS.equals(result.providerStatus())) {
            markPaymentPaid(paymentRecord, "MOCK", result.providerStatus());
        }

        auditLogService.record(
                currentUser,
                "PAYMENT_SESSION_CREATED",
                PAYMENT_RECORD_TARGET,
                paymentRecord.getId(),
                "Payment session created for order " + order.getOrderNumber());

        return checkoutResponse(order, paymentRecord, result.checkoutUrl(), result.available(), result.message(), orderItems);
    }

    @Transactional
    public StudentPaymentRecordResponse verifyPaymentStatus(SecurityUserDetails currentUser, Long paymentId) {
        if (currentUser == null || paymentId == null) {
            throw new ResponseStatusException(NOT_FOUND, "Payment record was not found.");
        }
        PaymentRecord paymentRecord = paymentRecordRepository.findByIdAndStudentUserId(paymentId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Payment record was not found."));

        if (paymentRecord.getPaymentStatus() != PaymentStatus.PENDING) {
            return mapStudentPaymentResponse(paymentRecord);
        }

        PaymentProviderStatusResult providerStatus = paymentProviderService.getClient()
                .getPaymentStatus(paymentRecord.getProviderReference());
        if (providerStatus == null) {
            providerStatus = PaymentProviderStatusResult.unavailable(
                    paymentRecord.getProviderName(),
                    PaymentProviderClient.PAYMENT_UNAVAILABLE_MESSAGE);
        }

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
            applyTerminalStatus(lockedPaymentRecord, providerStatus);
        } else if (!providerStatus.available()) {
            lockedPaymentRecord.setProviderStatus(PROVIDER_STATUS_UNAVAILABLE);
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
                applyTerminalStatus(lockedPaymentRecord, providerStatus);
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
        var student = lockedPaymentRecord.getStudent();
        var studentUser = student == null ? null : student.getUser();
        if (CheckoutSource.CART.name().equalsIgnoreCase(order.getCheckoutSource()) && studentUser != null) {
            cartService.clearCartIfExists(studentUser.getId());
        }

        if (studentUser != null) {
            notificationService.safeCreateNotification(
                    studentUser,
                    "Payment successful",
                    "Your payment was successful and access has been unlocked.",
                    PAYMENT_SUCCESS_PATH);
        }

        log.info("Payment marked PAID: paymentId={}, orderId={}, amount={} {}, method={}",
                lockedPaymentRecord.getId(), order.getId(),
                lockedPaymentRecord.getAmount(), lockedPaymentRecord.getCurrencyCode(),
                paymentMethod);
    }

    private void applyTerminalStatus(PaymentRecord record, PaymentProviderStatusResult status) {
        record.setPaymentStatus(status.paymentStatus());
        record.setProviderStatus(status.providerStatus());
        Order order = record.getOrder();
        if (order != null) {
            order.setPaymentStatus(status.paymentStatus());
            orderRepository.save(order);
        }
        log.info("Payment terminal status applied: paymentId={}, status={}, providerStatus={}",
                record.getId(), status.paymentStatus(), status.providerStatus());
    }

    private void markCheckoutUnavailable(PaymentRecord record, PaymentProviderCheckoutResult result) {
        record.setPaymentStatus(PaymentStatus.FAILED);
        record.setProviderStatus(result.providerStatus());
        Order order = record.getOrder();
        if (order != null) {
            order.setPaymentStatus(PaymentStatus.FAILED);
            orderRepository.save(order);
        }
        paymentRecordRepository.save(record);
        log.warn("Payment checkout unavailable: paymentId={}, orderId={}, providerStatus={}",
                record.getId(), order != null ? order.getId() : null, result.providerStatus());
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
                studentDisplayName(paymentRecord),
                studentEmail(paymentRecord),
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
        orderIds.forEach(orderId -> grouped.put(orderId, new ArrayList<>()));
        orderItemRepository.findByOrderIdInOrderByOrderIdAscCreatedAtAsc(orderIds)
                .forEach(orderItem -> {
                    if (orderItem.getOrder() != null && orderItem.getOrder().getId() != null) {
                        grouped.computeIfAbsent(orderItem.getOrder().getId(), ignored -> new ArrayList<>())
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
                        orderItem.getOrder() != null ? orderItem.getOrder().getCurrencyCode() : null,
                        orderItem.getQuantity(),
                        orderItem.getLineTotal(),
                        orderItem.getTeacher() != null))
                .toList();
    }

    private CheckoutResponse checkoutResponse(
            Order order,
            PaymentRecord paymentRecord,
            String checkoutUrl,
            boolean available,
            String message,
            List<OrderItem> orderItems) {
        return new CheckoutResponse(
                order.getId(),
                order.getOrderNumber(),
                paymentRecord.getId(),
                paymentRecord.getPaymentStatus(),
                paymentRecord.getAmount(),
                paymentRecord.getCurrencyCode(),
                checkoutUrl,
                paymentRecord.getProviderName(),
                paymentRecord.getProviderStatus(),
                available,
                message,
                mapOrderItems(orderItems));
    }

    private static String studentDisplayName(PaymentRecord paymentRecord) {
        return paymentRecord.getStudent() != null && paymentRecord.getStudent().getUser() != null
                ? paymentRecord.getStudent().getUser().getDisplayName()
                : null;
    }

    private static String studentEmail(PaymentRecord paymentRecord) {
        return paymentRecord.getStudent() != null && paymentRecord.getStudent().getUser() != null
                ? paymentRecord.getStudent().getUser().getEmail()
                : null;
    }
}

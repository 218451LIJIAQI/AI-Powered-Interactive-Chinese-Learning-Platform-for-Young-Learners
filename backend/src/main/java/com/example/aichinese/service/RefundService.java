package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.Objects;
import java.util.function.Function;

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
import com.example.aichinese.dto.commerce.AdminRefundRequestResponse;
import com.example.aichinese.dto.commerce.RefundDecisionRequest;
import com.example.aichinese.dto.commerce.RefundRequestCreateRequest;
import com.example.aichinese.dto.commerce.StudentRefundRequestResponse;
import com.example.aichinese.entity.Order;
import com.example.aichinese.entity.PaymentRecord;
import com.example.aichinese.entity.RefundRequest;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.enums.PaymentStatus;
import com.example.aichinese.entity.enums.RefundStatus;
import com.example.aichinese.repository.OrderRepository;
import com.example.aichinese.repository.PaymentRecordRepository;
import com.example.aichinese.repository.RefundRequestRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.PaginationUtils;

@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final StudentLearningAccessService studentLearningAccessService;
    private final OrderRepository orderRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final RefundRequestRepository refundRequestRepository;
    private final EntitlementService entitlementService;
    private final CommerceMoneyService commerceMoneyService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final TeacherEarningService teacherEarningService;

    public RefundService(
            StudentLearningAccessService studentLearningAccessService,
            OrderRepository orderRepository,
            PaymentRecordRepository paymentRecordRepository,
            RefundRequestRepository refundRequestRepository,
            EntitlementService entitlementService,
            CommerceMoneyService commerceMoneyService,
            NotificationService notificationService,
            AuditLogService auditLogService,
            TeacherEarningService teacherEarningService) {
        this.studentLearningAccessService = Objects.requireNonNull(studentLearningAccessService, "studentLearningAccessService");
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository");
        this.paymentRecordRepository = Objects.requireNonNull(paymentRecordRepository, "paymentRecordRepository");
        this.refundRequestRepository = Objects.requireNonNull(refundRequestRepository, "refundRequestRepository");
        this.entitlementService = Objects.requireNonNull(entitlementService, "entitlementService");
        this.commerceMoneyService = Objects.requireNonNull(commerceMoneyService, "commerceMoneyService");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService");
        this.auditLogService = Objects.requireNonNull(auditLogService, "auditLogService");
        this.teacherEarningService = Objects.requireNonNull(teacherEarningService, "teacherEarningService");
    }

    @Transactional
    public StudentRefundRequestResponse createRefundRequest(SecurityUserDetails currentUser, RefundRequestCreateRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Refund request is required.");
        }
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        Order order = orderRepository.findLockedByIdAndStudentUserId(request.orderId(), currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Order was not found."));
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new ResponseStatusException(BAD_REQUEST, "Only paid orders can be refunded.");
        }

        if (refundRequestRepository.existsByOrderIdAndRefundStatusIn(order.getId(), RefundStatus.ACTIONABLE)) {
            throw new ResponseStatusException(BAD_REQUEST, "A refund request is already active for this order.");
        }

        RefundRequest refundRequest = new RefundRequest();
        refundRequest.setStudent(student);
        refundRequest.setOrder(order);
        refundRequest.setAmount(commerceMoneyService.normalizePersistableAmount(
                order.getTotalAmount(),
                "Refund amount is too large."));
        refundRequest.setReason(request.reason() != null ? request.reason().trim() : null);
        refundRequest.setRefundStatus(RefundStatus.PENDING);
        RefundRequest saved = refundRequestRepository.save(refundRequest);

        notificationService.safeCreateNotification(
                student.getUser(),
                "Refund request submitted",
                "Your refund request has been submitted for review.",
                "/student/profile");
        log.info("Refund request created: refundId={}, orderId={}, amount={}",
                saved.getId(), order.getId(), saved.getAmount());
        auditLogService.record(currentUser.getUser(), "REFUND_REQUESTED", "REFUND_REQUEST", saved.getId(),
                "Refund request submitted for " + saved.getAmount().toPlainString());
        return mapStudentRefund(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentRefundRequestResponse> getStudentRefundRequests(SecurityUserDetails currentUser, int page, int size) {
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<RefundRequest> refundPage = refundRequestRepository.findByStudentUserIdOrderByCreatedAtDesc(
                currentUser.getUserId(),
                PageRequest.of(safePage, safeSize));
        return mapRefundPage(refundPage, safePage, safeSize, this::mapStudentRefund);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminRefundRequestResponse> getAllRefundRequests(int page, int size) {
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<RefundRequest> refundPage = refundRequestRepository.findAll(newestPageable(safePage, safeSize));
        return mapRefundPage(refundPage, safePage, safeSize, this::mapAdminRefund);
    }

    @Transactional
    public AdminRefundRequestResponse approveRefund(SecurityUserDetails currentUser, Long id) {
        Long refundId = Objects.requireNonNull(id, "Refund id must not be null.");
        RefundRequest refundRequest = refundRequestRepository.findLockedById(refundId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Refund request was not found."));
        requireTransition(refundRequest.getRefundStatus(), RefundStatus.PENDING, "approved");
        refundRequest.setRefundStatus(RefundStatus.APPROVED);
        RefundRequest saved = refundRequestRepository.save(refundRequest);
        notifyRefundStatus(saved, "Refund approved", "Your refund request has been approved.");
        log.info("Refund approved: refundId={}, amount={}", refundId, saved.getAmount());
        auditLogService.record(currentUser.getUser(), "REFUND_APPROVED", "REFUND_REQUEST", refundId,
                "Refund approved for " + saved.getAmount().toPlainString());
        return mapAdminRefund(saved);
    }

    @Transactional
    public AdminRefundRequestResponse rejectRefund(SecurityUserDetails currentUser, Long id, RefundDecisionRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Refund decision request is required.");
        }
        Long refundId = Objects.requireNonNull(id, "Refund id must not be null.");
        RefundRequest refundRequest = refundRequestRepository.findLockedById(refundId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Refund request was not found."));
        requireTransition(refundRequest.getRefundStatus(), RefundStatus.PENDING, "rejected");
        refundRequest.setRefundStatus(RefundStatus.REJECTED);
        refundRequest.setDecisionReason(request.reason());
        RefundRequest saved = refundRequestRepository.save(refundRequest);
        notifyRefundStatus(saved, "Refund rejected", "Your refund request was rejected.");
        log.info("Refund rejected: refundId={}, amount={}, reason={}", refundId, saved.getAmount(), request.reason());
        auditLogService.record(currentUser.getUser(), "REFUND_REJECTED", "REFUND_REQUEST", refundId,
                "Refund rejected for " + saved.getAmount().toPlainString()
                        + (request.reason() != null ? ". Reason: " + request.reason() : ""));
        return mapAdminRefund(saved);
    }

    @Transactional
    public AdminRefundRequestResponse markRefunded(SecurityUserDetails currentUser, Long id) {
        Long refundId = Objects.requireNonNull(id, "Refund id must not be null.");
        RefundRequest refundRequest = refundRequestRepository.findLockedById(refundId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Refund request was not found."));
        requireTransition(refundRequest.getRefundStatus(), RefundStatus.APPROVED, "marked as refunded");
        refundRequest.setRefundStatus(RefundStatus.REFUNDED);
        if (refundRequest.getOrder() != null) {
            Long orderId = refundRequest.getOrder().getId();
            refundRequest.getOrder().setPaymentStatus(PaymentStatus.REFUNDED);
            for (PaymentRecord paymentRecord : paymentRecordRepository.findByOrderId(orderId)) {
                paymentRecord.setPaymentStatus(PaymentStatus.REFUNDED);
                paymentRecord.setProviderStatus("REFUNDED");
            }
            entitlementService.revokeAccessAfterRefund(refundRequest.getOrder());
            teacherEarningService.markRefundedForOrder(orderId);
        }
        RefundRequest saved = refundRequestRepository.save(refundRequest);
        notifyRefundStatus(saved, "Refund completed", "Your refund has been marked as completed.");
        log.info("Refund completed: refundId={}, orderId={}, amount={}",
                id, refundRequest.getOrder() != null ? refundRequest.getOrder().getId() : null, saved.getAmount());
        auditLogService.record(currentUser.getUser(), "REFUND_MARKED_REFUNDED", "REFUND_REQUEST", id,
                "Refund marked refunded for " + saved.getAmount().toPlainString());
        return mapAdminRefund(saved);
    }

    private void requireTransition(RefundStatus current, RefundStatus expected, String action) {
        if (current != expected) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Refund cannot be " + action + " because it is currently " + current + ".");
        }
    }

    private Pageable newestPageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private <T> PageResponse<T> mapRefundPage(
            Page<RefundRequest> refundPage,
            int page,
            int size,
            Function<RefundRequest, T> mapper) {
        return new PageResponse<>(
                refundPage.getContent().stream()
                        .map(mapper)
                        .toList(),
                page,
                size,
                refundPage.getTotalElements(),
                PaginationUtils.totalPages(refundPage.getTotalElements(), size));
    }

    private StudentRefundRequestResponse mapStudentRefund(RefundRequest refundRequest) {
        return new StudentRefundRequestResponse(
                refundRequest.getId(),
                refundRequest.getOrder() != null ? refundRequest.getOrder().getId() : null,
                refundRequest.getOrder() != null ? refundRequest.getOrder().getOrderNumber() : null,
                commerceMoneyService.normalize(refundRequest.getAmount()),
                refundRequest.getReason(),
                refundRequest.getDecisionReason(),
                refundRequest.getRefundStatus(),
                refundRequest.getCreatedAt());
    }

    private AdminRefundRequestResponse mapAdminRefund(RefundRequest refundRequest) {
        return new AdminRefundRequestResponse(
                refundRequest.getId(),
                refundRequest.getOrder() != null ? refundRequest.getOrder().getId() : null,
                refundRequest.getOrder() != null ? refundRequest.getOrder().getOrderNumber() : null,
                refundRequest.getStudent() != null ? refundRequest.getStudent().getId() : null,
                refundRequest.getStudent() != null && refundRequest.getStudent().getUser() != null
                        ? refundRequest.getStudent().getUser().getDisplayName()
                        : refundRequest.getStudent() != null ? refundRequest.getStudent().getDisplayName() : null,
                refundRequest.getStudent() != null && refundRequest.getStudent().getUser() != null
                        ? refundRequest.getStudent().getUser().getEmail()
                        : null,
                commerceMoneyService.normalize(refundRequest.getAmount()),
                refundRequest.getReason(),
                refundRequest.getDecisionReason(),
                refundRequest.getRefundStatus(),
                refundRequest.getCreatedAt());
    }

    private void notifyRefundStatus(RefundRequest refundRequest, String title, String message) {
        if (refundRequest.getStudent() != null && refundRequest.getStudent().getUser() != null) {
            notificationService.safeCreateNotification(refundRequest.getStudent().getUser(), title, message, "/student/profile");
        }
    }
}

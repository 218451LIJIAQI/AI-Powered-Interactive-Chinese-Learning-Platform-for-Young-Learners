package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.commerce.RefundDecisionRequest;
import com.example.aichinese.dto.commerce.RefundRequestCreateRequest;
import com.example.aichinese.dto.commerce.RefundRequestResponse;
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

@Service
public class RefundService {

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
        this.studentLearningAccessService = studentLearningAccessService;
        this.orderRepository = orderRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.refundRequestRepository = refundRequestRepository;
        this.entitlementService = entitlementService;
        this.commerceMoneyService = commerceMoneyService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.teacherEarningService = teacherEarningService;
    }

    @Transactional
    public RefundRequestResponse createRefundRequest(SecurityUserDetails currentUser, RefundRequestCreateRequest request) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        Order order = orderRepository.findByIdAndStudentUserId(request.orderId(), currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Order was not found."));
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new ResponseStatusException(BAD_REQUEST, "Only paid orders can be refunded.");
        }

        if (refundRequestRepository.existsByOrderIdAndRefundStatusIn(order.getId(), List.of(RefundStatus.PENDING, RefundStatus.APPROVED))) {
            throw new ResponseStatusException(BAD_REQUEST, "A refund request is already active for this order.");
        }

        RefundRequest refundRequest = new RefundRequest();
        refundRequest.setStudent(student);
        refundRequest.setOrder(order);
        refundRequest.setAmount(commerceMoneyService.normalize(order.getTotalAmount()));
        refundRequest.setReason(request.reason().trim());
        refundRequest.setRefundStatus(RefundStatus.PENDING);
        RefundRequest saved = refundRequestRepository.save(refundRequest);

        notificationService.createNotification(
                student.getUser(),
                "Refund request submitted",
                "Your refund request has been submitted for review.",
                "/student/refunds");
        auditLogService.record(currentUser.getUser(), "REFUND_REQUESTED", "REFUND_REQUEST", saved.getId(), "Refund request submitted.");
        return mapRefund(saved);
    }

    @Transactional(readOnly = true)
    public List<RefundRequestResponse> getStudentRefundRequests(SecurityUserDetails currentUser) {
        return refundRequestRepository.findByStudentUserIdOrderByCreatedAtDesc(currentUser.getUserId()).stream()
                .map(this::mapRefund)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RefundRequestResponse> getAllRefundRequests() {
        // Two improvements compared to the previous version:
        //   * Newest-first ordering is delegated to the DB so the admin
        //     refund queue is consistent with every other admin list.
        //   * The repository's EntityGraph override prevents a hidden N+1 on
        //     student/order accesses inside the mapper.
        return refundRequestRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(this::mapRefund)
                .toList();
    }

    @Transactional
    public RefundRequestResponse approveRefund(SecurityUserDetails currentUser, Long id) {
        Long refundId = Objects.requireNonNull(id, "Refund id must not be null.");
        RefundRequest refundRequest = refundRequestRepository.findById(refundId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Refund request was not found."));
        // Only PENDING refunds may be approved. Without this guard an admin
        // double-clicking "Approve" would emit a second notification and a
        // second audit-log row for the exact same decision, and worse, would
        // let a REJECTED or already-REFUNDED request slip back into APPROVED.
        requireTransition(refundRequest.getRefundStatus(), RefundStatus.PENDING, "approved");
        refundRequest.setRefundStatus(RefundStatus.APPROVED);
        RefundRequest saved = refundRequestRepository.save(refundRequest);
        notifyRefundStatus(saved, "Refund approved", "Your refund request has been approved.");
        auditLogService.record(currentUser.getUser(), "REFUND_APPROVED", "REFUND_REQUEST", refundId, "Refund approved.");
        return mapRefund(saved);
    }

    @Transactional
    public RefundRequestResponse rejectRefund(SecurityUserDetails currentUser, Long id, RefundDecisionRequest request) {
        Long refundId = Objects.requireNonNull(id, "Refund id must not be null.");
        RefundRequest refundRequest = refundRequestRepository.findById(refundId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Refund request was not found."));
        requireTransition(refundRequest.getRefundStatus(), RefundStatus.PENDING, "rejected");
        refundRequest.setRefundStatus(RefundStatus.REJECTED);
        refundRequest.setDecisionReason(request.reason());
        RefundRequest saved = refundRequestRepository.save(refundRequest);
        notifyRefundStatus(saved, "Refund rejected", "Your refund request was rejected.");
        auditLogService.record(currentUser.getUser(), "REFUND_REJECTED", "REFUND_REQUEST", refundId, "Refund rejected.");
        return mapRefund(saved);
    }

    @Transactional
    public RefundRequestResponse markRefunded(SecurityUserDetails currentUser, Long id) {
        Long refundId = Objects.requireNonNull(id, "Refund id must not be null.");
        RefundRequest refundRequest = refundRequestRepository.findById(refundId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Refund request was not found."));
        // Only APPROVED refunds may transition to REFUNDED. Guards the same
        // double-click / state-machine-violation cases described on
        // approveRefund: refusing the second call here keeps entitlements
        // from being revoked twice and prevents a spurious "Refund completed"
        // notification.
        requireTransition(refundRequest.getRefundStatus(), RefundStatus.APPROVED, "marked as refunded");
        refundRequest.setRefundStatus(RefundStatus.REFUNDED);
        if (refundRequest.getOrder() != null) {
            Long orderId = refundRequest.getOrder().getId();
            refundRequest.getOrder().setPaymentStatus(PaymentStatus.REFUNDED);
            // Targeted lookup by order id instead of pulling every PaymentRecord row
            // into memory just to find the one(s) attached to this refund's order.
            for (PaymentRecord paymentRecord : paymentRecordRepository.findByOrderId(orderId)) {
                paymentRecord.setPaymentStatus(PaymentStatus.REFUNDED);
                paymentRecord.setProviderStatus("REFUNDED");
            }
            entitlementService.revokeAccessAfterRefund(refundRequest.getOrder());
            teacherEarningService.markRefundedForOrder(orderId);
        }
        RefundRequest saved = refundRequestRepository.save(refundRequest);
        notifyRefundStatus(saved, "Refund completed", "Your refund has been marked as completed.");
        auditLogService.record(currentUser.getUser(), "REFUND_MARKED_REFUNDED", "REFUND_REQUEST", id, "Refund marked refunded.");
        return mapRefund(saved);
    }

    /**
     * Enforces the refund state machine: a transition is only valid when the
     * refund is currently in {@code expected}. Short-circuits the common
     * mistake of an admin double-clicking the action button by returning a
     * 400 with a human-readable message instead of silently duplicating
     * notifications and audit rows.
     */
    private void requireTransition(RefundStatus current, RefundStatus expected, String action) {
        if (current != expected) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Refund cannot be " + action + " because it is currently " + current + ".");
        }
    }

    private RefundRequestResponse mapRefund(RefundRequest refundRequest) {
        return new RefundRequestResponse(
                refundRequest.getId(),
                refundRequest.getOrder() != null ? refundRequest.getOrder().getId() : null,
                refundRequest.getOrder() != null ? refundRequest.getOrder().getOrderNumber() : null,
                commerceMoneyService.normalize(refundRequest.getAmount()),
                refundRequest.getReason(),
                refundRequest.getDecisionReason(),
                refundRequest.getRefundStatus(),
                refundRequest.getCreatedAt());
    }

    private void notifyRefundStatus(RefundRequest refundRequest, String title, String message) {
        if (refundRequest.getStudent() != null && refundRequest.getStudent().getUser() != null) {
            notificationService.createNotification(refundRequest.getStudent().getUser(), title, message, "/student/refunds");
        }
    }
}

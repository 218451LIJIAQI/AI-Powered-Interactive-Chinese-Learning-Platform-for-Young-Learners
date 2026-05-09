package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.List;

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
    private final CommerceNotificationService commerceNotificationService;
    private final CommerceAuditService commerceAuditService;
    private final TeacherEarningService teacherEarningService;

    public RefundService(
            StudentLearningAccessService studentLearningAccessService,
            OrderRepository orderRepository,
            PaymentRecordRepository paymentRecordRepository,
            RefundRequestRepository refundRequestRepository,
            EntitlementService entitlementService,
            CommerceMoneyService commerceMoneyService,
            CommerceNotificationService commerceNotificationService,
            CommerceAuditService commerceAuditService,
            TeacherEarningService teacherEarningService) {
        this.studentLearningAccessService = studentLearningAccessService;
        this.orderRepository = orderRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.refundRequestRepository = refundRequestRepository;
        this.entitlementService = entitlementService;
        this.commerceMoneyService = commerceMoneyService;
        this.commerceNotificationService = commerceNotificationService;
        this.commerceAuditService = commerceAuditService;
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

        commerceNotificationService.notifyUser(
                student.getUser(),
                "Refund request submitted",
                "Your refund request has been submitted for review.",
                "/student/refunds");
        commerceAuditService.record(currentUser.getUser(), "REFUND_REQUESTED", "REFUND_REQUEST", saved.getId(), "Refund request submitted.");
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
        return refundRequestRepository.findAll().stream()
                .map(this::mapRefund)
                .toList();
    }

    @Transactional
    public RefundRequestResponse approveRefund(SecurityUserDetails currentUser, Long id) {
        RefundRequest refundRequest = refundRequestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Refund request was not found."));
        refundRequest.setRefundStatus(RefundStatus.APPROVED);
        RefundRequest saved = refundRequestRepository.save(refundRequest);
        notifyRefundStatus(saved, "Refund approved", "Your refund request has been approved.");
        commerceAuditService.record(currentUser.getUser(), "REFUND_APPROVED", "REFUND_REQUEST", id, "Refund approved.");
        return mapRefund(saved);
    }

    @Transactional
    public RefundRequestResponse rejectRefund(SecurityUserDetails currentUser, Long id, RefundDecisionRequest request) {
        RefundRequest refundRequest = refundRequestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Refund request was not found."));
        refundRequest.setRefundStatus(RefundStatus.REJECTED);
        refundRequest.setDecisionReason(request.reason());
        RefundRequest saved = refundRequestRepository.save(refundRequest);
        notifyRefundStatus(saved, "Refund rejected", "Your refund request was rejected.");
        commerceAuditService.record(currentUser.getUser(), "REFUND_REJECTED", "REFUND_REQUEST", id, "Refund rejected.");
        return mapRefund(saved);
    }

    @Transactional
    public RefundRequestResponse markRefunded(SecurityUserDetails currentUser, Long id) {
        RefundRequest refundRequest = refundRequestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Refund request was not found."));
        refundRequest.setRefundStatus(RefundStatus.REFUNDED);
        if (refundRequest.getOrder() != null) {
            refundRequest.getOrder().setPaymentStatus(PaymentStatus.REFUNDED);
            for (PaymentRecord paymentRecord : paymentRecordRepository.findAll()) {
                if (paymentRecord.getOrder() != null
                        && java.util.Objects.equals(paymentRecord.getOrder().getId(), refundRequest.getOrder().getId())) {
                    paymentRecord.setPaymentStatus(PaymentStatus.REFUNDED);
                    paymentRecord.setProviderStatus("REFUNDED");
                }
            }
            entitlementService.revokeAccessAfterRefund(refundRequest.getOrder());
            teacherEarningService.markRefundedForOrder(refundRequest.getOrder().getId());
        }
        RefundRequest saved = refundRequestRepository.save(refundRequest);
        notifyRefundStatus(saved, "Refund completed", "Your refund has been marked as completed.");
        commerceAuditService.record(currentUser.getUser(), "REFUND_MARKED_REFUNDED", "REFUND_REQUEST", id, "Refund marked refunded.");
        return mapRefund(saved);
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
            commerceNotificationService.notifyUser(refundRequest.getStudent().getUser(), title, message, "/student/refunds");
        }
    }
}

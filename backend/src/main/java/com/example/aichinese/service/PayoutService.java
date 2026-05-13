package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.commerce.PayoutDecisionRequest;
import com.example.aichinese.dto.commerce.PayoutRequestCreateRequest;
import com.example.aichinese.dto.commerce.PayoutRequestResponse;
import com.example.aichinese.dto.commerce.TeacherEarningResponse;
import com.example.aichinese.dto.commerce.TeacherEarningSummaryResponse;
import com.example.aichinese.entity.PayoutRequest;
import com.example.aichinese.entity.TeacherEarning;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.PayoutStatus;
import com.example.aichinese.repository.PayoutRequestRepository;
import com.example.aichinese.repository.TeacherEarningRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.security.SecurityUserDetails;

@Service
public class PayoutService {

    private final TeacherProfileRepository teacherProfileRepository;
    private final TeacherEarningRepository teacherEarningRepository;
    private final PayoutRequestRepository payoutRequestRepository;
    private final CommerceMoneyService commerceMoneyService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public PayoutService(
            TeacherProfileRepository teacherProfileRepository,
            TeacherEarningRepository teacherEarningRepository,
            PayoutRequestRepository payoutRequestRepository,
            CommerceMoneyService commerceMoneyService,
            NotificationService notificationService,
            AuditLogService auditLogService) {
        this.teacherProfileRepository = teacherProfileRepository;
        this.teacherEarningRepository = teacherEarningRepository;
        this.payoutRequestRepository = payoutRequestRepository;
        this.commerceMoneyService = commerceMoneyService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<TeacherEarningResponse> getTeacherEarnings(SecurityUserDetails currentUser) {
        return teacherEarningRepository.findByTeacherUserIdOrderByCreatedAtDesc(currentUser.getUserId()).stream()
                .map(this::mapEarning)
                .toList();
    }

    @Transactional(readOnly = true)
    public TeacherEarningSummaryResponse getTeacherEarningSummary(SecurityUserDetails currentUser) {
        TeacherProfile teacher = teacherProfileRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Teacher profile could not be found."));
        java.math.BigDecimal grossAmount = commerceMoneyService.normalize(
                teacherEarningRepository.sumGrossAmountByTeacherIdAndEarningStatusNot(teacher.getId(), "REFUNDED"));
        java.math.BigDecimal totalNetAmount = commerceMoneyService.normalize(
                teacherEarningRepository.sumNetAmountByTeacherIdAndEarningStatusNot(teacher.getId(), "REFUNDED"));
        java.math.BigDecimal pendingPayoutAmount = commerceMoneyService.normalize(
                payoutRequestRepository.sumAmountByTeacherIdAndPayoutStatusIn(
                        teacher.getId(),
                        java.util.EnumSet.of(PayoutStatus.PENDING, PayoutStatus.APPROVED)));
        java.math.BigDecimal committedPayoutAmount = commerceMoneyService.normalize(
                payoutRequestRepository.sumAmountByTeacherIdAndPayoutStatusIn(
                        teacher.getId(),
                        java.util.EnumSet.of(PayoutStatus.PENDING, PayoutStatus.APPROVED, PayoutStatus.PAID)));
        return new TeacherEarningSummaryResponse(
                grossAmount,
                totalNetAmount,
                commerceMoneyService.subtract(totalNetAmount, committedPayoutAmount),
                pendingPayoutAmount);
    }

    @Transactional
    public PayoutRequestResponse createPayoutRequest(SecurityUserDetails currentUser, PayoutRequestCreateRequest request) {
        TeacherProfile teacher = teacherProfileRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Teacher profile could not be found."));
        java.math.BigDecimal normalizedAmount = commerceMoneyService.normalize(request.amount());
        if (getTeacherEarningSummary(currentUser).availableBalance().compareTo(normalizedAmount) < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Requested payout exceeds available balance.");
        }

        PayoutRequest payoutRequest = new PayoutRequest();
        payoutRequest.setTeacher(teacher);
        payoutRequest.setAmount(normalizedAmount);
        payoutRequest.setRequestedNote(request.requestedNote());
        payoutRequest.setPayoutStatus(PayoutStatus.PENDING);
        PayoutRequest saved = payoutRequestRepository.save(payoutRequest);

        notificationService.createNotification(
                teacher.getUser(),
                "Payout request submitted",
                "Your payout request has been submitted for review.",
                "/teacher/payouts");
        auditLogService.record(
                currentUser.getUser(),
                "PAYOUT_REQUESTED",
                "PAYOUT_REQUEST",
                saved.getId(),
                "Teacher payout request submitted.");
        return mapPayout(saved);
    }

    @Transactional(readOnly = true)
    public List<PayoutRequestResponse> getTeacherPayoutRequests(SecurityUserDetails currentUser) {
        return payoutRequestRepository.findByTeacherUserIdOrderByCreatedAtDesc(currentUser.getUserId()).stream()
                .map(this::mapPayout)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PayoutRequestResponse> getAllPayoutRequests() {
        // Newest payout requests first, sorted by the database. The repo's
        // EntityGraph override eliminates a hidden N+1 on teacher/teacher.user
        // lookups that the mapper performs row-by-row.
        return payoutRequestRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(this::mapPayout)
                .toList();
    }

    @Transactional
    public PayoutRequestResponse approvePayout(SecurityUserDetails currentUser, Long id) {
        PayoutRequest payoutRequest = payoutRequestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Payout request was not found."));
        payoutRequest.setPayoutStatus(PayoutStatus.APPROVED);
        PayoutRequest saved = payoutRequestRepository.save(payoutRequest);
        notifyPayoutStatus(saved, "Payout approved", "Your payout request has been approved.");
        auditLogService.record(currentUser.getUser(), "PAYOUT_APPROVED", "PAYOUT_REQUEST", id, "Payout approved.");
        return mapPayout(saved);
    }

    @Transactional
    public PayoutRequestResponse rejectPayout(SecurityUserDetails currentUser, Long id, PayoutDecisionRequest request) {
        PayoutRequest payoutRequest = payoutRequestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Payout request was not found."));
        payoutRequest.setPayoutStatus(PayoutStatus.REJECTED);
        payoutRequest.setDecisionReason(request.reason());
        PayoutRequest saved = payoutRequestRepository.save(payoutRequest);
        notifyPayoutStatus(saved, "Payout rejected", "Your payout request was rejected.");
        auditLogService.record(currentUser.getUser(), "PAYOUT_REJECTED", "PAYOUT_REQUEST", id, "Payout rejected.");
        return mapPayout(saved);
    }

    @Transactional
    public PayoutRequestResponse markPayoutPaid(SecurityUserDetails currentUser, Long id) {
        PayoutRequest payoutRequest = payoutRequestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Payout request was not found."));
        payoutRequest.setPayoutStatus(PayoutStatus.PAID);
        PayoutRequest saved = payoutRequestRepository.save(payoutRequest);
        notifyPayoutStatus(saved, "Payout paid", "Your payout has been marked as paid.");
        auditLogService.record(currentUser.getUser(), "PAYOUT_MARKED_PAID", "PAYOUT_REQUEST", id, "Payout marked paid.");
        return mapPayout(saved);
    }

    private TeacherEarningResponse mapEarning(TeacherEarning earning) {
        return new TeacherEarningResponse(
                earning.getId(),
                earning.getOrderItem() != null ? earning.getOrderItem().getId() : null,
                earning.getOrderItem() != null ? earning.getOrderItem().getItemTitleSnapshot() : null,
                commerceMoneyService.normalize(earning.getGrossAmount()),
                commerceMoneyService.normalize(earning.getCommissionAmount()),
                commerceMoneyService.normalize(earning.getNetAmount()),
                earning.getEarningStatus(),
                earning.getCreatedAt());
    }

    private PayoutRequestResponse mapPayout(PayoutRequest payoutRequest) {
        return new PayoutRequestResponse(
                payoutRequest.getId(),
                payoutRequest.getTeacher() != null ? payoutRequest.getTeacher().getId() : null,
                payoutRequest.getTeacher() != null && payoutRequest.getTeacher().getUser() != null
                        ? payoutRequest.getTeacher().getUser().getDisplayName()
                        : null,
                commerceMoneyService.normalize(payoutRequest.getAmount()),
                payoutRequest.getRequestedNote(),
                payoutRequest.getDecisionReason(),
                payoutRequest.getPayoutStatus(),
                payoutRequest.getCreatedAt());
    }

    private void notifyPayoutStatus(PayoutRequest payoutRequest, String title, String message) {
        if (payoutRequest.getTeacher() != null && payoutRequest.getTeacher().getUser() != null) {
            notificationService.createNotification(payoutRequest.getTeacher().getUser(), title, message, "/teacher/payouts");
        }
    }
}

package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.math.BigDecimal;
import java.util.EnumSet;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.commerce.AdminPayoutRequestResponse;
import com.example.aichinese.dto.commerce.PayoutDecisionRequest;
import com.example.aichinese.dto.commerce.PayoutRequestCreateRequest;
import com.example.aichinese.dto.commerce.TeacherEarningResponse;
import com.example.aichinese.dto.commerce.TeacherEarningSummaryResponse;
import com.example.aichinese.dto.commerce.TeacherPayoutRequestResponse;
import com.example.aichinese.entity.PayoutRequest;
import com.example.aichinese.entity.TeacherEarning;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.TeacherEarningStatus;
import com.example.aichinese.entity.enums.PayoutStatus;
import com.example.aichinese.repository.PayoutRequestRepository;
import com.example.aichinese.repository.TeacherEarningRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.PaginationUtils;

/**
 * Teacher earnings ledger and payout request workflow.
 *
 * <h2>Earnings</h2>
 * <p>A {@link TeacherEarning} row is created when a learner completes
 * a purchase that contains a teacher-owned item; the amount is the
 * sale price minus the {@code platform_commission_rate} from
 * {@link CommerceSettingsService}. Earnings start in
 * {@link TeacherEarningStatus#PENDING} and become {@code AVAILABLE}
 * once the underlying payment clears the refund window, so a
 * payout request can never draw against earnings that could still
 * be refunded.</p>
 *
 * <h2>Payout requests</h2>
 * <p>Teachers file a {@link PayoutRequest} for some / all of their
 * available balance. The request goes {@code PENDING → APPROVED →
 * PAID} (or {@code REJECTED}) via admin decisions made through
 * {@link PayoutDecisionRequest}. Approval freezes the earmarked
 * earnings (status {@code RESERVED}); payment marks them
 * {@code PAID}; rejection rolls them back to {@code AVAILABLE}.</p>
 *
 * <h2>Reconciliation</h2>
 * <p>All money math goes through {@link CommerceMoneyService} so
 * teacher payouts are reconciled to the same scale and rounding
 * rules as the rest of the commerce stack — preventing one-cent
 * drift across the ledger.</p>
 */
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
    public PageResponse<TeacherEarningResponse> getTeacherEarnings(SecurityUserDetails currentUser, int page, int size) {
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<TeacherEarning> earningPage = teacherEarningRepository.findByTeacherUserIdOrderByCreatedAtDesc(
                currentUser.getUserId(),
                PageRequest.of(safePage, safeSize));
        return mapEarningPage(earningPage, safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public TeacherEarningSummaryResponse getTeacherEarningSummary(SecurityUserDetails currentUser) {
        TeacherProfile teacher = teacherProfileRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Teacher profile could not be found."));
        BigDecimal grossAmount = commerceMoneyService.normalize(
                teacherEarningRepository.sumGrossAmountByTeacherIdAndEarningStatusNot(teacher.getId(), TeacherEarningStatus.REFUNDED));
        BigDecimal totalNetAmount = commerceMoneyService.normalize(
                teacherEarningRepository.sumNetAmountByTeacherIdAndEarningStatusNot(teacher.getId(), TeacherEarningStatus.REFUNDED));
        BigDecimal pendingPayoutAmount = commerceMoneyService.normalize(
                payoutRequestRepository.sumAmountByTeacherIdAndPayoutStatusIn(
                        teacher.getId(),
                        EnumSet.of(PayoutStatus.PENDING, PayoutStatus.APPROVED)));
        BigDecimal committedPayoutAmount = commerceMoneyService.normalize(
                payoutRequestRepository.sumAmountByTeacherIdAndPayoutStatusIn(
                        teacher.getId(),
                        EnumSet.of(PayoutStatus.PENDING, PayoutStatus.APPROVED, PayoutStatus.PAID)));
        return new TeacherEarningSummaryResponse(
                grossAmount,
                totalNetAmount,
                commerceMoneyService.subtract(totalNetAmount, committedPayoutAmount),
                pendingPayoutAmount);
    }

    @Transactional
    public TeacherPayoutRequestResponse createPayoutRequest(SecurityUserDetails currentUser, PayoutRequestCreateRequest request) {
        TeacherProfile teacher = teacherProfileRepository.findLockedByUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Teacher profile could not be found."));
        BigDecimal normalizedAmount = commerceMoneyService.normalize(request.amount());
        if (calculateAvailableBalance(teacher.getId()).compareTo(normalizedAmount) < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Requested payout exceeds available balance.");
        }

        PayoutRequest payoutRequest = new PayoutRequest();
        payoutRequest.setTeacher(teacher);
        payoutRequest.setAmount(normalizedAmount);
        payoutRequest.setRequestedNote(request.requestedNote());
        payoutRequest.setPayoutStatus(PayoutStatus.PENDING);
        PayoutRequest saved = payoutRequestRepository.save(payoutRequest);

        notificationService.safeCreateNotification(
                teacher.getUser(),
                "Payout request submitted",
                "Your payout request has been submitted for review.",
                "/teacher/earnings");
        auditLogService.record(
                currentUser.getUser(),
                "PAYOUT_REQUESTED",
                "PAYOUT_REQUEST",
                saved.getId(),
                "Teacher payout request submitted.");
        return mapTeacherPayout(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<TeacherPayoutRequestResponse> getTeacherPayoutRequests(SecurityUserDetails currentUser, int page, int size) {
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<PayoutRequest> payoutPage = payoutRequestRepository.findByTeacherUserIdOrderByCreatedAtDesc(
                currentUser.getUserId(),
                PageRequest.of(safePage, safeSize));
        return mapTeacherPayoutPage(payoutPage, safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminPayoutRequestResponse> getAllPayoutRequests(int page, int size) {
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<PayoutRequest> payoutPage = payoutRequestRepository.findAll(newestPageable(safePage, safeSize));
        return mapAdminPayoutPage(payoutPage, safePage, safeSize);
    }

    @Transactional
    public AdminPayoutRequestResponse approvePayout(SecurityUserDetails currentUser, Long id) {
        PayoutRequest payoutRequest = payoutRequestRepository.findLockedById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Payout request was not found."));
        // Mirrors the RefundService state-machine guard: only PENDING payouts
        // may be approved. Prevents duplicate notifications and audit-log rows
        // from a double-clicked admin button, and forbids rewinding a REJECTED
        // or already-PAID request back into APPROVED.
        requireTransition(payoutRequest.getPayoutStatus(), PayoutStatus.PENDING, "approved");
        requireCommittedPayoutCovered(payoutRequest, "approved");
        payoutRequest.setPayoutStatus(PayoutStatus.APPROVED);
        PayoutRequest saved = payoutRequestRepository.save(payoutRequest);
        notifyPayoutStatus(saved, "Payout approved", "Your payout request has been approved.");
        auditLogService.record(currentUser.getUser(), "PAYOUT_APPROVED", "PAYOUT_REQUEST", id, "Payout approved.");
        return mapAdminPayout(saved);
    }

    @Transactional
    public AdminPayoutRequestResponse rejectPayout(SecurityUserDetails currentUser, Long id, PayoutDecisionRequest request) {
        PayoutRequest payoutRequest = payoutRequestRepository.findLockedById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Payout request was not found."));
        requireTransition(payoutRequest.getPayoutStatus(), PayoutStatus.PENDING, "rejected");
        payoutRequest.setPayoutStatus(PayoutStatus.REJECTED);
        payoutRequest.setDecisionReason(request.reason());
        PayoutRequest saved = payoutRequestRepository.save(payoutRequest);
        notifyPayoutStatus(saved, "Payout rejected", "Your payout request was rejected.");
        auditLogService.record(currentUser.getUser(), "PAYOUT_REJECTED", "PAYOUT_REQUEST", id, "Payout rejected.");
        return mapAdminPayout(saved);
    }

    @Transactional
    public AdminPayoutRequestResponse markPayoutPaid(SecurityUserDetails currentUser, Long id) {
        PayoutRequest payoutRequest = payoutRequestRepository.findLockedById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Payout request was not found."));
        // Payout must transition APPROVED → PAID. Blocking the PENDING → PAID
        // shortcut ensures every disbursement has a documented approval step
        // in the audit trail, which is important for financial reviews.
        requireTransition(payoutRequest.getPayoutStatus(), PayoutStatus.APPROVED, "marked as paid");
        requireCommittedPayoutCovered(payoutRequest, "marked as paid");
        payoutRequest.setPayoutStatus(PayoutStatus.PAID);
        PayoutRequest saved = payoutRequestRepository.save(payoutRequest);
        notifyPayoutStatus(saved, "Payout paid", "Your payout has been marked as paid.");
        auditLogService.record(currentUser.getUser(), "PAYOUT_MARKED_PAID", "PAYOUT_REQUEST", id, "Payout marked paid.");
        return mapAdminPayout(saved);
    }

    private void requireTransition(PayoutStatus current, PayoutStatus expected, String action) {
        if (current != expected) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Payout cannot be " + action + " because it is currently " + current + ".");
        }
    }

    private void requireCommittedPayoutCovered(PayoutRequest payoutRequest, String action) {
        Long teacherId = payoutRequest.getTeacher() != null ? payoutRequest.getTeacher().getId() : null;
        if (teacherId == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Payout cannot be " + action + " because the teacher profile is missing.");
        }

        if (calculateAvailableBalance(teacherId).compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Payout cannot be " + action + " because current available balance is insufficient.");
        }
    }

    private BigDecimal calculateAvailableBalance(Long teacherId) {
        BigDecimal totalNetAmount = commerceMoneyService.normalize(
                teacherEarningRepository.sumNetAmountByTeacherIdAndEarningStatusNot(teacherId, TeacherEarningStatus.REFUNDED));
        BigDecimal committedPayoutAmount = commerceMoneyService.normalize(
                payoutRequestRepository.sumAmountByTeacherIdAndPayoutStatusIn(
                        teacherId,
                        EnumSet.of(PayoutStatus.PENDING, PayoutStatus.APPROVED, PayoutStatus.PAID)));
        return commerceMoneyService.subtract(totalNetAmount, committedPayoutAmount);
    }

    private Pageable newestPageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private PageResponse<TeacherEarningResponse> mapEarningPage(Page<TeacherEarning> earningPage, int page, int size) {
        return new PageResponse<>(
                earningPage.getContent().stream()
                        .map(this::mapEarning)
                        .toList(),
                page,
                size,
                earningPage.getTotalElements(),
                PaginationUtils.totalPages(earningPage.getTotalElements(), size));
    }

    private PageResponse<TeacherPayoutRequestResponse> mapTeacherPayoutPage(Page<PayoutRequest> payoutPage, int page, int size) {
        return new PageResponse<>(
                payoutPage.getContent().stream()
                        .map(this::mapTeacherPayout)
                        .toList(),
                page,
                size,
                payoutPage.getTotalElements(),
                PaginationUtils.totalPages(payoutPage.getTotalElements(), size));
    }

    private PageResponse<AdminPayoutRequestResponse> mapAdminPayoutPage(Page<PayoutRequest> payoutPage, int page, int size) {
        return new PageResponse<>(
                payoutPage.getContent().stream()
                        .map(this::mapAdminPayout)
                        .toList(),
                page,
                size,
                payoutPage.getTotalElements(),
                PaginationUtils.totalPages(payoutPage.getTotalElements(), size));
    }

    private TeacherEarningResponse mapEarning(TeacherEarning earning) {
        return new TeacherEarningResponse(
                earning.getId(),
                earning.getOrderItem() != null ? earning.getOrderItem().getId() : null,
                earning.getOrderItem() != null ? earning.getOrderItem().getItemTitleSnapshot() : null,
                commerceMoneyService.normalize(earning.getGrossAmount()),
                commerceMoneyService.normalize(earning.getCommissionAmount()),
                commerceMoneyService.normalize(earning.getNetAmount()),
                earning.getEarningStatus().name(),
                earning.getCreatedAt());
    }

    private TeacherPayoutRequestResponse mapTeacherPayout(PayoutRequest payoutRequest) {
        return new TeacherPayoutRequestResponse(
                payoutRequest.getId(),
                commerceMoneyService.normalize(payoutRequest.getAmount()),
                payoutRequest.getRequestedNote(),
                payoutRequest.getDecisionReason(),
                payoutRequest.getPayoutStatus(),
                payoutRequest.getCreatedAt());
    }

    private AdminPayoutRequestResponse mapAdminPayout(PayoutRequest payoutRequest) {
        return new AdminPayoutRequestResponse(
                payoutRequest.getId(),
                payoutRequest.getTeacher() != null ? payoutRequest.getTeacher().getId() : null,
                payoutRequest.getTeacher() != null && payoutRequest.getTeacher().getUser() != null
                        ? payoutRequest.getTeacher().getUser().getDisplayName()
                        : null,
                payoutRequest.getTeacher() != null && payoutRequest.getTeacher().getUser() != null
                        ? payoutRequest.getTeacher().getUser().getEmail()
                        : null,
                commerceMoneyService.normalize(payoutRequest.getAmount()),
                payoutRequest.getRequestedNote(),
                payoutRequest.getDecisionReason(),
                payoutRequest.getPayoutStatus(),
                payoutRequest.getCreatedAt());
    }

    private void notifyPayoutStatus(PayoutRequest payoutRequest, String title, String message) {
        if (payoutRequest.getTeacher() != null && payoutRequest.getTeacher().getUser() != null) {
            notificationService.safeCreateNotification(payoutRequest.getTeacher().getUser(), title, message, "/teacher/earnings");
        }
    }
}

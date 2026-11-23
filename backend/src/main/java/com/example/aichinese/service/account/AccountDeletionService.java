package com.example.aichinese.service.account;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.SupportRequest;
import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.AiChatMessageRepository;
import com.example.aichinese.repository.AiChatSessionRepository;
import com.example.aichinese.repository.AiResponseReportRepository;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.EmailChangeTokenRepository;
import com.example.aichinese.repository.EmailConfirmationTokenRepository;
import com.example.aichinese.repository.LearningPlanRepository;
import com.example.aichinese.repository.LearningPlanStepRepository;
import com.example.aichinese.repository.NotificationRepository;
import com.example.aichinese.repository.PasswordResetTokenRepository;
import com.example.aichinese.repository.SupportMessageRepository;
import com.example.aichinese.repository.SupportRequestRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.repository.XiaomeiMemoryNoteRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AiResponseReportService;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.AuditLogService.AuditTarget;
import com.example.aichinese.service.AuthService;
import com.example.aichinese.service.FileStorageService;
import com.example.aichinese.service.RefreshTokenService;
import com.example.aichinese.service.SecureTokens;
import com.example.aichinese.service.admin.AuditLogQueryService;
import com.example.aichinese.service.support.SupportService;

import lombok.RequiredArgsConstructor;

/**
 * A learner deletes their own account. The user row stays, because learning records and audit entries point to it, but
 * it is anonymised and can never sign in again; the personal data the account owns is removed: name and e-mail,
 * notifications, support requests with their screenshots (those sent from the account's address on the public Help page
 * too), pending e-mail changes and confirmation links, the learning goal in the learner's own words (and what SparkBot
 * wrote from it), what Xiaomei remembered about the learner and a school name an earlier version stored. Other people
 * stop seeing the name too (the Privacy notice and the delete dialog promise it): the notifications admins got about
 * the account's AI reports and support requests are deleted, and the audit entries keep what happened but lose the
 * account's e-mail and name and the subjects of its support requests. AI reports stay in the admins' queue, about the
 * AI answer and no longer naming the learner. Admin accounts are deactivated by another admin instead.
 */
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private static final String DELETED_NAME = "Deleted account";

    private final UserRepository userRepository;
    private final SupportRequestRepository supportRequestRepository;
    private final SupportMessageRepository supportMessageRepository;
    private final NotificationRepository notificationRepository;
    private final EmailChangeTokenRepository emailChangeTokenRepository;
    private final EmailConfirmationTokenRepository emailConfirmationTokenRepository;
    private final LearningPlanRepository learningPlanRepository;
    private final LearningPlanStepRepository learningPlanStepRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final FileStorageService fileStorageService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final AuditLogRepository auditLogRepository;
    private final AiChatMessageRepository aiChatMessageRepository;
    private final AiChatSessionRepository aiChatSessionRepository;
    private final AiResponseReportRepository aiResponseReportRepository;
    private final XiaomeiMemoryNoteRepository xiaomeiMemoryNoteRepository;
    private final RetainedSchoolNames retainedSchoolNames;
    private final CurrentAccount currentAccount;
    /** Lazily initialized per-process random unrecoverable hash so deletion never burns BCrypt CPU under row locks. */
    private volatile String deletedAccountPasswordHash;

    /**
     * Read committed: once the account's lock is held, the deletion reads what committed while it was awaited. An
     * account deactivated, deleted or signed out meanwhile is left as it is: the request's session no longer holds.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(SecurityUserDetails currentUser, String confirmEmail) {
        User user = currentAccount.lock(currentUser);
        if (!user.getEmail().equals(AuthService.normalizeEmail(confirmEmail))) {
            throw new ResponseStatusException(BAD_REQUEST, "The email address you typed doesn't match your account.");
        }
        if (user.requirePrimaryRole() == RoleName.ADMIN) {
            throw new ResponseStatusException(
                    BAD_REQUEST, "Admin accounts can't be deleted. Another admin can deactivate this account.");
        }

        Long userId = user.getId();
        String email = user.getEmail();
        removeSupportRequests(userId, email);
        notificationRepository.deleteByUserId(userId);
        List<Long> reportIds = aiResponseReportRepository.findIdsByStudentUserId(userId);
        if (!reportIds.isEmpty()) {
            notificationRepository.deleteByTarget(AiResponseReportService.TARGET_TYPE, reportIds);
        }
        emailChangeTokenRepository.deleteByUserId(userId);
        emailConfirmationTokenRepository.deleteByUserId(userId);
        learningPlanRepository.clearOwnWords(userId);
        learningPlanStepRepository.clearStepReasons(userId);
        // What Xiaomei remembered about the learner and their SparkBot chat transcripts go with the account.
        xiaomeiMemoryNoteRepository.deleteByStudentUserId(userId);
        aiChatMessageRepository.deleteBySessionUserId(userId);
        aiChatSessionRepository.deleteByUserId(userId);
        retainedSchoolNames.clear(userId);
        LocalDateTime now = LocalDateTime.now();
        passwordResetTokenRepository.retireOpenTokensForUser(userId, now);
        anonymiseAuditTrail(userId);

        // Reloaded: the bulk deletes above cleared the persistence context.
        User account = userRepository.findById(userId).orElseThrow();
        account.setEmail(AccountGuards.deletedAccountEmail(userId));
        account.setDisplayName(DELETED_NAME);
        account.setPasswordHash(deletedAccountPasswordHash());
        account.setActive(false);
        account.setDeletedAt(now);
        refreshTokenService.removeSessions(account);
        auditLogService.record(account, "ACCOUNT_DELETED",
                new AuditTarget(AuditLogQueryService.TARGET_USER, userId, DELETED_NAME), null,
                "The owner deleted the learner account; its personal data was removed.");
    }

    /**
     * The account's requests and the ones sent from its address on the public Help page (often "I can't sign in",
     * written while signed out) go with their messages and screenshots.
     */
    private void removeSupportRequests(Long userId, String email) {
        List<Long> sentIds = supportRequestRepository.findIdsSentBy(userId, email);
        if (sentIds.isEmpty()) {
            return;
        }
        // Locked before their messages are read. The account's lock already keeps support writes off its own requests,
        // but none guards a visitor's request: a support reply either committed first and goes with the request, or
        // waits for this lock and then finds the request gone (instead of adding a message the delete then trips over).
        List<SupportRequest> requests = supportRequestRepository.findLockedByIdIn(sentIds);
        List<Long> requestIds = requests.stream().map(SupportRequest::getId).toList();
        if (requestIds.isEmpty()) {
            return;
        }
        List<UploadedFile> screenshots = supportMessageRepository.findAttachmentsByRequestIdIn(requestIds);
        // Support's replies and solves stay in the audit log under the request number, without the subject.
        // Run before deleting the requests so the bulk update reads each request's number in one round-trip.
        auditLogRepository.anonymiseSupportTargetLabels(SupportService.TARGET_TYPE, requestIds);
        supportMessageRepository.deleteByRequestIdIn(requestIds);
        supportRequestRepository.deleteAllByIdInBatch(requestIds);
        // The admins' "new request" notices name the requester and would now open a request that no longer exists.
        notificationRepository.deleteByTarget(SupportService.TARGET_TYPE, requestIds);
        fileStorageService.deleteSupportScreenshots(screenshots);
    }

    private String deletedAccountPasswordHash() {
        String hash = deletedAccountPasswordHash;
        if (hash == null) {
            synchronized (this) {
                hash = deletedAccountPasswordHash;
                if (hash == null) {
                    hash = passwordEncoder.encode(SecureTokens.newRawToken());
                    deletedAccountPasswordHash = hash;
                }
            }
        }
        return hash;
    }

    /**
     * Audit entries keep what happened and the account id; the account's e-mail (as actor), its name (as target
     * label) and the old address quoted by an e-mail change go.
     */
    private void anonymiseAuditTrail(Long userId) {
        auditLogRepository.anonymiseActorEmail(userId, AccountGuards.deletedAccountEmail(userId));
        auditLogRepository.anonymiseTargetLabel(AuditLogQueryService.TARGET_USER, userId, DELETED_NAME);
        auditLogRepository.replaceDescriptions(
                "EMAIL_CHANGED", AuditLogQueryService.TARGET_USER, userId, "User confirmed a new email address.");
    }
}

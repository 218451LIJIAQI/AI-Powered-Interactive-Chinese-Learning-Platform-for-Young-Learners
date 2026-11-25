package com.example.aichinese.service.account;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.AdminInviteRepository;
import com.example.aichinese.repository.AiChatMessageRepository;
import com.example.aichinese.repository.AiChatSessionRepository;
import com.example.aichinese.repository.AiPracticeCheckRepository;
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
import com.example.aichinese.service.admin.AdminInviteService;
import com.example.aichinese.service.support.SupportService;

import lombok.RequiredArgsConstructor;

/**
 * A learner deletes their own account. The user row stays, because learning records and audit entries point to it, but
 * it is anonymised and can never sign in again; the personal data the account owns is removed: name and e-mail,
 * notifications, support requests with their screenshots (plus unlinked public Help-page support requests and
 * unaccepted admin invites for that address when the account's e-mail is verified), pending e-mail changes and
 * confirmation links, SparkBot chat transcripts and practice checks, the learning goal in the learner's own words (and
 * what SparkBot wrote from it), what Xiaomei remembered about the learner and a school name an earlier version stored.
 * Other people stop seeing the name too (the Privacy notice and the delete dialog promise it): the notifications admins
 * got about the account's AI reports and support requests are deleted, and the audit entries keep what happened but
 * lose the account's e-mail and name and the subjects of its support requests. Learning-plan AI reports are deleted,
 * while other AI reports stay in the admins' queue with learner-written notes and prompt snapshots cleared and no
 * longer naming the learner. Admin accounts are deactivated by another admin instead.
 */
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private static final String DELETED_NAME = "Deleted account";
    private static final int NOTIFICATION_DELETE_BATCH_SIZE = 1000;

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
    private final AiPracticeCheckRepository aiPracticeCheckRepository;
    private final AiResponseReportRepository aiResponseReportRepository;
    private final AdminInviteRepository adminInviteRepository;
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
        boolean emailVerified = user.getEmailVerifiedAt() != null;
        List<Long> requestIds = removeSupportRequests(userId, email, emailVerified);
        if (emailVerified) {
            adminInviteRepository.deleteUnacceptedByEmail(email);
        }
        List<Long> reportIds = aiResponseReportRepository.findIdsByStudentUserId(userId);
        deleteNotificationsInOrder(userId, requestIds, reportIds);
        if (!reportIds.isEmpty()) {
            aiResponseReportRepository.deleteByStudentUserIdAndFeatureKey(userId, AiFeatureKey.LEARNING_PLAN);
            aiResponseReportRepository.clearPersonalTextByStudentUserId(userId);
        }
        emailChangeTokenRepository.deleteByUserId(userId);
        emailConfirmationTokenRepository.deleteByUserId(userId);
        learningPlanRepository.clearOwnWords(userId);
        learningPlanStepRepository.clearStepReasons(userId);
        // What Xiaomei remembered about the learner, their SparkBot chat transcripts and practice checks go too.
        xiaomeiMemoryNoteRepository.deleteByStudentUserId(userId);
        aiPracticeCheckRepository.deleteByStudentUserId(userId);
        aiChatMessageRepository.deleteBySessionUserId(userId);
        aiChatSessionRepository.deleteByUserId(userId);
        retainedSchoolNames.clear(userId);
        LocalDateTime now = LocalDateTime.now();
        passwordResetTokenRepository.retireOpenTokensForUser(userId, now);
        anonymiseAuditTrail(userId, email, emailVerified);

        // Reloaded: the bulk deletes above cleared the persistence context.
        User account = userRepository.findById(userId).orElseThrow();
        account.setEmail(AccountGuards.deletedAccountEmail(userId));
        account.setDisplayName(DELETED_NAME);
        account.setPasswordHash(deletedAccountPasswordHash());
        account.setActive(false);
        account.setDeletedAt(now);
        refreshTokenService.removeSessions(account);
        auditLogService.record(account, "ACCOUNT_DELETED",
                new AuditTarget(AuditLogService.TARGET_USER, userId, DELETED_NAME), null,
                "The owner deleted the learner account; its personal data was removed.");
    }

    /**
     * The account's own requests go with their messages and screenshots; when the account's address is verified,
     * unlinked requests sent from that address on the public Help page (often "I can't sign in", written while signed
     * out) are removed too. Unverified accounts never remove unlinked visitor requests.
     */
    private List<Long> removeSupportRequests(Long userId, String email, boolean emailVerified) {
        List<Long> sentIds = emailVerified
                ? supportRequestRepository.findIdsSentBy(userId, email)
                : supportRequestRepository.findIdsByRequesterId(userId);
        if (sentIds.isEmpty()) {
            return List.of();
        }
        // Locked before their messages are read. The account's lock already keeps support writes off its own requests,
        // but none guards a visitor's request: a support reply either committed first and goes with the request, or
        // waits for this lock and then finds the request gone (instead of adding a message the delete then trips over).
        List<Long> requestIds = supportRequestRepository.lockIdsInOrder(sentIds);
        if (requestIds.isEmpty()) {
            return List.of();
        }
        List<UploadedFile> screenshots = supportMessageRepository.findAttachmentsByRequestIdIn(requestIds);
        // Support's replies and solves stay in the audit log under the request number, without the subject.
        // Run before deleting the requests so the bulk update reads each request's number in one round-trip.
        auditLogRepository.anonymiseSupportTargetLabels(SupportService.TARGET_TYPE, requestIds);
        supportMessageRepository.deleteByRequestIdIn(requestIds);
        supportRequestRepository.deleteAllByIdInBatch(requestIds);
        fileStorageService.deleteSupportScreenshots(screenshots);
        return requestIds;
    }

    /**
     * Deletes the account's own notifications in bounded ascending primary-key slices, then deletes any remaining
     * admin notifications pointing to the account's support requests or AI reports in ascending primary-key order.
     * Deleting the account's own rows first guarantees strictly ascending per-user lock order without overlapping
     * with other users' notifications, avoiding AB-BA deadlocks with concurrent bulk read/unread marks.
     */
    private void deleteNotificationsInOrder(Long userId, List<Long> requestIds, List<Long> reportIds) {
        long afterId = 0L;
        Pageable slice = PageRequest.of(0, NOTIFICATION_DELETE_BATCH_SIZE);
        while (true) {
            List<Long> ownBatch = notificationRepository.findIdsByUserIdAfter(userId, afterId, slice);
            if (ownBatch.isEmpty()) {
                break;
            }
            List<Long> locked = notificationRepository.lockIdsInOrder(ownBatch);
            if (!locked.isEmpty()) {
                notificationRepository.deleteByIdIn(locked);
            }
            if (ownBatch.size() < NOTIFICATION_DELETE_BATCH_SIZE) {
                break;
            }
            long nextAfterId = ownBatch.getLast();
            if (nextAfterId <= afterId) {
                break;
            }
            afterId = nextAfterId;
        }
        TreeSet<Long> targetNotificationIds = new TreeSet<>();
        if (!requestIds.isEmpty()) {
            targetNotificationIds.addAll(notificationRepository.findIdsByTarget(SupportService.TARGET_TYPE, requestIds));
        }
        if (!reportIds.isEmpty()) {
            targetNotificationIds.addAll(notificationRepository.findIdsByTarget(AiResponseReportService.TARGET_TYPE, reportIds));
        }
        if (targetNotificationIds.isEmpty()) {
            return;
        }
        List<Long> ordered = new ArrayList<>(targetNotificationIds);
        for (int start = 0; start < ordered.size(); start += NOTIFICATION_DELETE_BATCH_SIZE) {
            List<Long> batch = ordered.subList(start, Math.min(start + NOTIFICATION_DELETE_BATCH_SIZE, ordered.size()));
            List<Long> locked = notificationRepository.lockIdsInOrder(batch);
            if (!locked.isEmpty()) {
                notificationRepository.deleteByIdIn(locked);
            }
        }
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
     * Audit entries keep what happened and the account id while losing the account's e-mail (as actor) and name (as
     * target label).
     */
    private void anonymiseAuditTrail(Long userId, String email, boolean emailVerified) {
        String anonymousEmail = AccountGuards.deletedAccountEmail(userId);
        auditLogRepository.anonymiseActorEmail(userId, anonymousEmail);
        auditLogRepository.anonymiseTargetLabel(AuditLogService.TARGET_USER, userId, DELETED_NAME);
        if (emailVerified) {
            auditLogRepository.anonymiseTargetLabelByEmail(AdminInviteService.TARGET_TYPE, email, anonymousEmail);
        }
    }
}

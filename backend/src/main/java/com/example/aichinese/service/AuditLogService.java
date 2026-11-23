package com.example.aichinese.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.AfterCommit;
import com.example.aichinese.util.ExceptionLogUtils;
import com.example.aichinese.util.RequestIdHolder;
import com.example.aichinese.util.SensitiveDataRedactor;
import com.example.aichinese.util.TextUtils;

/**
 * Writes audit log entries; entries are never deleted, and the only edit they get is losing a deleted account's
 * e-mail and name (AccountDeletionService), so the admin audit log keeps what happened.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private static final int MAX_AUDIT_DESCRIPTION_LENGTH = 1000;
    private static final int MAX_TARGET_LABEL_LENGTH = 255;
    private static final int MAX_REASON_LENGTH = 1000;

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    /** REQUIRES_NEW transaction of the best-effort audits. */
    private final TransactionTemplate independentAuditTransaction;

    public AuditLogService(
            AuditLogRepository auditLogRepository,
            UserRepository userRepository,
            PlatformTransactionManager transactionManager) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.independentAuditTransaction = new TransactionTemplate(transactionManager);
        this.independentAuditTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** What an entry is about: its type and id, and its name in words as shown in the audit log. */
    public record AuditTarget(String type, Long id, String label) {

        public static AuditTarget of(String type, Long id) {
            return new AuditTarget(type, id, null);
        }
    }

    /** Who made an entry: the account's current name, or the e-mail recorded with it when no account is linked. */
    public static String actorName(AuditLog entry) {
        return entry.getActorUser() != null ? entry.getActorUser().getDisplayName() : entry.getActorEmail();
    }

    @Transactional
    public AuditLog record(User actor, String actionType, String targetType, Long targetId, String description) {
        return record(actor, actionType, AuditTarget.of(targetType, targetId), null, description);
    }

    /** An entry with the target's name and, for an admin decision, the reason the admin gave. */
    @Transactional
    public AuditLog record(User actor, String actionType, AuditTarget target, String reason, String description) {
        return auditLogRepository.save(newAuditLog(
                actor, actor == null ? null : actor.getEmail(), actionType, target, reason, description));
    }

    @Transactional
    public void record(SecurityUserDetails actor, String actionType, String targetType, Long targetId, String description) {
        auditLogRepository.save(newAuditLog(
                userRepository.getReferenceById(actor.getUserId()),
                actor.getUsername(),
                actionType,
                AuditTarget.of(targetType, targetId),
                null,
                description));
    }

    private static AuditLog newAuditLog(
            User actor,
            String actorEmail,
            String actionType,
            AuditTarget target,
            String reason,
            String description) {
        AuditLog auditLog = new AuditLog();
        auditLog.setActorUser(actor);
        auditLog.setActorEmail(actorEmail);
        auditLog.setActionType(actionType);
        auditLog.setTargetType(target.type());
        auditLog.setTargetId(target.id());
        auditLog.setTargetLabel(TextUtils.safeTruncate(TextUtils.trimToNull(target.label()), MAX_TARGET_LABEL_LENGTH));
        auditLog.setReason(TextUtils.safeTruncate(
                SensitiveDataRedactor.redactText(TextUtils.trimToNull(reason)), MAX_REASON_LENGTH));
        auditLog.setDescription(
                TextUtils.safeTruncate(SensitiveDataRedactor.redactText(description), MAX_AUDIT_DESCRIPTION_LENGTH));
        auditLog.setRequestId(RequestIdHolder.get());
        return auditLog;
    }

    /**
     * Best-effort audit: a failed insert is logged and never fails the caller. Inside a transaction the row is
     * written after that transaction commits, in a REQUIRES_NEW transaction of its own, so a failed insert cannot
     * mark the caller's transaction rollback-only and never waits for row locks the caller still holds (such as
     * the user row locked by a password-reset request). Nothing is written when the caller's
     * transaction rolls back.
     */
    public void safeRecord(User actor, String actionType, String targetType, Long targetId, String description) {
        AuditLog auditLog = newAuditLog(actor, actor == null ? null : actor.getEmail(), actionType,
                AuditTarget.of(targetType, targetId), null, description);
        AfterCommit.run(() -> saveInNewTransaction(auditLog));
    }

    /**
     * Best-effort audit of an anonymous event, written at once in a REQUIRES_NEW transaction of its own, so it is kept
     * even when the caller then fails and rolls back (a lockout engaged by a wrong current password in a password
     * change, before its 400 response rolls the transaction back).
     */
    public void safeRecordInNewTransaction(String actionType, String targetType, String description) {
        saveInNewTransaction(newAuditLog(null, null, actionType, AuditTarget.of(targetType, null), null, description));
    }

    private void saveInNewTransaction(AuditLog auditLog) {
        try {
            independentAuditTransaction.executeWithoutResult(status -> {
                if (auditLog.getActorUser() != null) {
                    // Deletion and this deferred insert must agree about the actor's anonymised address.
                    User actor = userRepository.findWithReadLockById(auditLog.getActorUser().getId()).orElseThrow();
                    auditLog.setActorUser(actor);
                    if (actor.getDeletedAt() != null) {
                        auditLog.setActorEmail(actor.getEmail());
                    }
                }
                auditLogRepository.save(auditLog);
            });
        } catch (Exception exception) {
            log.warn("Audit log creation failed for action {} on {}:{}: {}",
                    auditLog.getActionType(),
                    auditLog.getTargetType(),
                    auditLog.getTargetId(),
                    ExceptionLogUtils.safeSummary(exception));
            ExceptionLogUtils.debugOrigin(log, exception);
        }
    }
}

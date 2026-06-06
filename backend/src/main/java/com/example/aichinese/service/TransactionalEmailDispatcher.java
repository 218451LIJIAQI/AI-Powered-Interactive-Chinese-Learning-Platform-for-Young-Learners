package com.example.aichinese.service;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.aichinese.config.AsyncConfig;
import com.example.aichinese.entity.User;
import com.example.aichinese.util.ExceptionLogUtils;

/**
 * Publishes outbound email work from transactional use cases and delivers it
 * only after the surrounding database commit succeeds.
 */
@Service
public class TransactionalEmailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(TransactionalEmailDispatcher.class);

    private final ApplicationEventPublisher eventPublisher;
    private final EmailService emailService;

    public TransactionalEmailDispatcher(ApplicationEventPublisher eventPublisher, EmailService emailService) {
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.emailService = Objects.requireNonNull(emailService, "emailService");
    }

    public void enqueuePasswordReset(User recipient, String resetLink, String rawToken) {
        publish(EmailDeliveryType.PASSWORD_RESET, recipient, resetLink, rawToken);
    }

    public void enqueueTeacherApproval(User recipient, String note) {
        publish(EmailDeliveryType.TEACHER_APPROVAL, recipient, note, null);
    }

    public void enqueueTeacherRejection(User recipient, String reason) {
        publish(EmailDeliveryType.TEACHER_REJECTION, recipient, reason, null);
    }

    @Async(AsyncConfig.APPLICATION_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(EmailDeliveryRequested event) {
        if (event == null) {
            log.warn("Asynchronous email delivery skipped because the delivery event was null.");
            return;
        }
        try {
            User recipient = event.recipient().toUser();
            switch (event.type()) {
                case PASSWORD_RESET ->
                        emailService.sendPasswordResetEmail(recipient, event.primaryContent(), event.secondaryContent());
                case TEACHER_APPROVAL ->
                        emailService.safeSendTeacherApprovalNotice(recipient, event.primaryContent());
                case TEACHER_REJECTION ->
                        emailService.safeSendTeacherRejectionNotice(recipient, event.primaryContent());
            }
        } catch (Exception exception) {
            Long recipientId = recipientId(event);
            log.warn("Asynchronous email delivery failed for user id {}: {}", recipientId, ExceptionLogUtils.safeSummary(exception));
            log.debug("Asynchronous email delivery stack trace for user id {}.", recipientId, exception);
        }
    }

    private void publish(EmailDeliveryType type, User recipient, String primaryContent, String secondaryContent) {
        eventPublisher.publishEvent(new EmailDeliveryRequested(
                type,
                EmailRecipientSnapshot.from(recipient),
                primaryContent,
                secondaryContent));
    }

    private static Long recipientId(EmailDeliveryRequested event) {
        return event.recipient() == null ? null : event.recipient().id();
    }

    private enum EmailDeliveryType {
        PASSWORD_RESET,
        TEACHER_APPROVAL,
        TEACHER_REJECTION
    }

    private record EmailDeliveryRequested(
            EmailDeliveryType type,
            EmailRecipientSnapshot recipient,
            String primaryContent,
            String secondaryContent) {
    }

    private record EmailRecipientSnapshot(
            Long id,
            String email,
            String displayName) {

        private static EmailRecipientSnapshot from(User user) {
            if (user == null) {
                return new EmailRecipientSnapshot(null, null, null);
            }
            return new EmailRecipientSnapshot(user.getId(), user.getEmail(), user.getDisplayName());
        }

        private User toUser() {
            User user = new User();
            user.setId(id);
            user.setEmail(email);
            user.setDisplayName(displayName);
            return user;
        }
    }
}

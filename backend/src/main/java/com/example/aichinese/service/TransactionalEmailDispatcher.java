package com.example.aichinese.service;

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
import com.example.aichinese.util.TextUtils;

/**
 * Composes the account e-mails (password reset, e-mail change, admin invite, teacher decisions, support replies to
 * visitors) in transactional use cases and sends
 * them only after the surrounding database commit succeeds.
 */
@Service
public class TransactionalEmailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(TransactionalEmailDispatcher.class);
    private static final String BLANK_LINE = System.lineSeparator() + System.lineSeparator();

    private final ApplicationEventPublisher eventPublisher;
    private final EmailService emailService;

    public TransactionalEmailDispatcher(ApplicationEventPublisher eventPublisher, EmailService emailService) {
        this.eventPublisher = eventPublisher;
        this.emailService = emailService;
    }

    public void enqueuePasswordReset(User recipient, String resetLink, String rawToken) {
        publish(
                recipient,
                "Password reset request",
                "We received a request to reset the password for your account."
                        + BLANK_LINE
                        + "Click the link below to set a new password (link expires in 60 minutes):"
                        + System.lineSeparator() + resetLink
                        + BLANK_LINE
                        + "If the link does not work, you can paste this token into the reset form: "
                        + rawToken
                        + BLANK_LINE
                        + "If you did not request a password reset, you can safely ignore this email.");
    }

    public void enqueueTeacherApproval(User recipient, String note) {
        publish(
                recipient,
                "Teacher approval update",
                "Your teacher account has been approved." + optionalParagraph("Note: ", note));
    }

    public void enqueueTeacherRejection(User recipient, String reason) {
        publish(
                recipient,
                "Teacher verification update",
                "Your teacher account verification was not approved." + optionalParagraph("Reason: ", reason));
    }

    /** The link that confirms a new e-mail address; it goes to the new address, so it proves the user owns it. */
    public void enqueueEmailChangeConfirmation(String newEmail, String confirmLink) {
        publishTo(
                newEmail,
                "Confirm your new email address",
                "Open this link to use this address for your MandarinSpark account (the link works once, for 60 minutes):"
                        + System.lineSeparator() + confirmLink
                        + BLANK_LINE
                        + "If you did not ask to change your email address, you can ignore this email.");
    }

    /** The link that confirms a new student's address; learning goes on while it waits. */
    public void enqueueSignupConfirmation(String email, String name, String confirmLink) {
        publishTo(
                email,
                "Confirm your email address",
                "Hi " + TextUtils.defaultIfBlank(name, "there") + ", welcome to MandarinSpark!"
                        + BLANK_LINE
                        + "Open this link to confirm your email address (the link works once, for 3 days):"
                        + System.lineSeparator() + confirmLink
                        + BLANK_LINE
                        + "You can keep learning in the meantime. If you did not create this account, you can ignore"
                        + " this email.");
    }

    public void enqueueAdminInvite(String email, String invitedByName, String inviteLink) {
        publishTo(
                email,
                "You are invited to be a MandarinSpark admin",
                TextUtils.defaultIfBlank(invitedByName, "An admin") + " invited you to help run MandarinSpark."
                        + " Admins review content, manage users and change AI settings."
                        + BLANK_LINE
                        + "Open this link to set your password (the link works for 7 days):"
                        + System.lineSeparator() + inviteLink);
    }

    /** Support answered a visitor, who has no account to read the reply in. */
    public void enqueueSupportReply(String email, int requestNumber, String subject, String reply) {
        publishTo(
                email,
                "Re: #" + requestNumber + " " + subject,
                reply
                        + BLANK_LINE
                        + "MandarinSpark support. Reply to this email or send us a message on the Help page.");
    }

    @Async(AsyncConfig.APPLICATION_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(EmailDeliveryRequested event) {
        try {
            emailService.sendEmail(event.recipient().toUser(), event.subject(), event.body());
        } catch (Exception exception) {
            Long recipientId = event.recipient().id();
            log.warn("Asynchronous email delivery failed for user id {}: {}", recipientId, ExceptionLogUtils.safeSummary(exception));
            log.debug("Asynchronous email delivery stack trace for user id {}.", recipientId, exception);
        }
    }

    private void publishTo(String email, String subject, String body) {
        eventPublisher.publishEvent(new EmailDeliveryRequested(new EmailRecipientSnapshot(null, email), subject, body));
    }

    private void publish(User recipient, String subject, String body) {
        eventPublisher.publishEvent(new EmailDeliveryRequested(EmailRecipientSnapshot.from(recipient), subject, body));
    }

    private static String optionalParagraph(String prefix, String value) {
        String text = TextUtils.trimToNull(value);
        return text == null ? "" : BLANK_LINE + prefix + text;
    }

    private record EmailDeliveryRequested(EmailRecipientSnapshot recipient, String subject, String body) {
    }

    private record EmailRecipientSnapshot(Long id, String email) {

        private static EmailRecipientSnapshot from(User user) {
            if (user == null) {
                return new EmailRecipientSnapshot(null, null);
            }
            return new EmailRecipientSnapshot(user.getId(), user.getEmail());
        }

        private User toUser() {
            User user = new User();
            user.setId(id);
            user.setEmail(email);
            return user;
        }
    }
}

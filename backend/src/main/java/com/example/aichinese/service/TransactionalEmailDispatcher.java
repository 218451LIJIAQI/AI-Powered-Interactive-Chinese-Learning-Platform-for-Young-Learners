package com.example.aichinese.service;

import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.aichinese.config.AsyncConfig;
import com.example.aichinese.entity.User;

import lombok.RequiredArgsConstructor;

/**
 * Composes the account e-mails (password reset, e-mail change, sign-up confirmation, admin invite, support replies),
 * which are always sent, and the admins' daily summary e-mail they switch on or off in Account › Email notifications, in
 * transactional use cases, and sends them only after the surrounding database commit succeeds.
 *
 * <p>Every e-mail is written in its reader's language: an account's preferred language (which the interface language
 * switch keeps current), the inviting admin's for an admin invite, and for a visitor's support reply the language of
 * the Help page form the visitor wrote from; English when there is none. The fixed words come from {@link EmailTexts};
 * links, names, numbers and typed text (a reply) go in as they are.
 */
@Service
@RequiredArgsConstructor
public class TransactionalEmailDispatcher {

    private static final String BLANK_LINE = System.lineSeparator() + System.lineSeparator();

    private final ApplicationEventPublisher eventPublisher;
    private final EmailService emailService;
    private final EmailTexts texts;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueuePasswordReset(User recipient, String resetLink) {
        publish(recipient.getEmail(), recipient.getPreferredLanguage(), "passwordReset", Map.of("link", resetLink));
    }

    /**
     * The link that confirms a new e-mail address of {@code account}; it goes to the new address, so it proves the user
     * owns it.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueEmailChangeConfirmation(User account, String newEmail, String confirmLink) {
        publish(newEmail, account.getPreferredLanguage(), "emailChange", Map.of("link", confirmLink));
    }

    /** The link that confirms a new student's address; learning goes on while it waits. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueSignupConfirmation(User account, String confirmLink) {
        String language = account.getPreferredLanguage();
        String name = account.getDisplayName();
        String greeting = name == null || name.isBlank()
                ? texts.text(language, "signupConfirmation.greetingNoName")
                : texts.text(language, "signupConfirmation.greeting", Map.of("name", name));
        publish(account.getEmail(), language, "signupConfirmation", Map.of("greeting", greeting, "link", confirmLink));
    }

    /**
     * An invite to someone without an account yet: written in the language of the admin who sends it, the one thing
     * known about the invite's language.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueAdminInvite(String email, User inviter, String inviteLink) {
        String language = inviter.getPreferredLanguage();
        String name = inviter.getDisplayName();
        String inviterName = name == null || name.isBlank() ? texts.text(language, "adminInvite.someAdmin") : name;
        publish(email, language, "adminInvite", Map.of("inviter", inviterName, "link", inviteLink));
    }

    /**
     * Support's answer to a request, e-mailed to the requester (a learner or a visitor of the Help page). {@code language}
     * is the learner's language, or the one the visitor sent the request in (null when not known: English).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueSupportReply(String email, String language, int requestNumber, String subject, String reply) {
        publish(email, language, "supportReply",
                Map.of("number", Integer.toString(requestNumber), "subject", subject, "reply", reply));
    }

    /** The admins' daily summary e-mail: each queue with something waiting, its count and a link to it. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueReviewQueueDigest(User recipient, List<WaitingQueue> waiting) {
        String language = recipient.getPreferredLanguage();
        StringBuilder body = new StringBuilder(texts.text(language, "reviewQueueDigest.intro"));
        for (WaitingQueue queue : waiting) {
            body.append(BLANK_LINE)
                    .append(texts.text(language, "reviewQueueDigest.queue", Map.of(
                            "queue", texts.text(language, queue.queue().textKey),
                            "count", Long.toString(queue.count()))))
                    .append(System.lineSeparator()).append(queue.link());
        }
        body.append(BLANK_LINE).append(texts.text(language, "reviewQueueDigest.switchOffNote"));
        eventPublisher.publishEvent(new EmailDeliveryRequested(
                recipient.getEmail(), texts.text(language, "reviewQueueDigest.subject"), body.toString()));
    }

    /** The queues of the daily summary, as the admin sidebar counts them. */
    public enum ReviewQueue {

        AI_REPORTS("reviewQueueDigest.aiReports"),
        SUPPORT_REQUESTS("reviewQueueDigest.supportRequests");

        private final String textKey;

        ReviewQueue(String textKey) {
            this.textKey = textKey;
        }
    }

    /** One queue of the daily summary: which one, how many items wait in it and the absolute link to its page. */
    public record WaitingQueue(ReviewQueue queue, long count, String link) {
    }

    @Async(AsyncConfig.APPLICATION_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(EmailDeliveryRequested event) {
        emailService.sendEmail(event.email(), event.subject(), event.body());
    }

    /** An e-mail whose subject and body are the texts {@code name}.subject and {@code name}.body of {@link EmailTexts}. */
    private void publish(String email, String language, String name, Map<String, String> values) {
        eventPublisher.publishEvent(new EmailDeliveryRequested(
                email,
                singleLine(texts.text(language, name + ".subject", values)),
                texts.text(language, name + ".body", values)));
    }

    /**
     * A subject is one header line: line breaks and other control characters become spaces. Subjects carry typed text
     * (a requester's support subject).
     */
    private static String singleLine(String text) {
        return text.replaceAll("\\p{Cntrl}+", " ").strip();
    }

    /**
     * Sent after the commit of the transaction that published it (never when it rolls back). Published outside a
     * transaction it is dropped, as the listener only runs after a commit: every caller queues inside its transaction.
     */
    public record EmailDeliveryRequested(String email, String subject, String body) {
    }
}

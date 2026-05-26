package com.example.aichinese.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.aichinese.entity.User;
import com.example.aichinese.util.ExceptionLogUtils;
import com.example.aichinese.util.TextUtils;

/**
 * Strategy interface for outbound emails: teacher
 * approval / rejection notices and password reset links.
 *
 * <p>Two implementations exist:</p>
 * <ul>
 *   <li>{@link SmtpEmailService} - talks to a real SMTP server when
 *       {@code app.email.enabled=true}.</li>
 *   <li>{@link NoOpEmailService} - permitted for local and test
 *       environments only; production startup rejects disabled SMTP because
 *       password-reset messages cannot be replaced by in-app notification.</li>
 * </ul>
 *
 * <p>The {@code safe*} default helpers swallow exceptions and log at
 * {@code WARN} so a transient SMTP failure does not abort the
 * triggering business transaction - email delivery is best-effort and runs
 * after the committing transaction.</p>
 */
public interface EmailService {

    Logger log = LoggerFactory.getLogger(EmailService.class);

    void sendEmail(User recipient, String subject, String body);

    default void sendTeacherApprovalNotice(User recipient, String note) {
        String normalizedNote = TextUtils.trimToNull(note);
        sendEmail(
                recipient,
                "Teacher approval update",
                "Your teacher account has been approved."
                        + (normalizedNote == null ? "" : System.lineSeparator() + System.lineSeparator() + "Note: " + normalizedNote));
    }

    default void safeSendTeacherApprovalNotice(User recipient, String note) {
        try {
            sendTeacherApprovalNotice(recipient, note);
        } catch (Exception exception) {
            Long recipientId = recipient != null ? recipient.getId() : null;
            log.warn("Teacher approval email delivery failed for user id {}: {}", recipientId, ExceptionLogUtils.safeSummary(exception));
            log.debug("Teacher approval email delivery stack trace for user id {}.", recipientId, exception);
        }
    }

    default void sendTeacherRejectionNotice(User recipient, String reason) {
        String normalizedReason = TextUtils.trimToNull(reason);
        sendEmail(
                recipient,
                "Teacher verification update",
                "Your teacher account verification was not approved."
                        + (normalizedReason == null
                                ? ""
                                : System.lineSeparator() + System.lineSeparator() + "Reason: " + normalizedReason));
    }

    default void safeSendTeacherRejectionNotice(User recipient, String reason) {
        try {
            sendTeacherRejectionNotice(recipient, reason);
        } catch (Exception exception) {
            Long recipientId = recipient != null ? recipient.getId() : null;
            log.warn("Teacher rejection email delivery failed for user id {}: {}", recipientId, ExceptionLogUtils.safeSummary(exception));
            log.debug("Teacher rejection email delivery stack trace for user id {}.", recipientId, exception);
        }
    }

    /**
     * Sends a password-reset email containing the reset link / token.
     *
     * <p>{@code resetLink} is the absolute URL the recipient can click to
     * land on the frontend reset page with the token attached as a query
     * parameter. {@code rawToken} is included separately so the user can
     * paste it manually if their mail client mangles the link.</p>
     */
    default void sendPasswordResetEmail(User recipient, String resetLink, String rawToken) {
        sendEmail(
                recipient,
                "Password reset request",
                "We received a request to reset the password for your account."
                        + System.lineSeparator() + System.lineSeparator()
                        + "Click the link below to set a new password (link expires in 60 minutes):"
                        + System.lineSeparator() + resetLink
                        + System.lineSeparator() + System.lineSeparator()
                        + "If the link does not work, you can paste this token into the reset form: "
                        + rawToken
                        + System.lineSeparator() + System.lineSeparator()
                        + "If you did not request a password reset, you can safely ignore this email.");
    }
}

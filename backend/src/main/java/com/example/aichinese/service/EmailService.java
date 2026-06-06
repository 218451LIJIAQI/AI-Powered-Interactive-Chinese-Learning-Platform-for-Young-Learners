package com.example.aichinese.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.aichinese.entity.User;
import com.example.aichinese.util.ExceptionLogUtils;
import com.example.aichinese.util.TextUtils;

public interface EmailService {

    Logger log = LoggerFactory.getLogger(EmailService.class);
    String BLANK_LINE = System.lineSeparator() + System.lineSeparator();

    void sendEmail(User recipient, String subject, String body);

    default void sendTeacherApprovalNotice(User recipient, String note) {
        String normalizedNote = TextUtils.trimToNull(note);
        sendEmail(
                recipient,
                "Teacher approval update",
                "Your teacher account has been approved."
                        + optionalParagraph("Note: ", normalizedNote));
    }

    default void safeSendTeacherApprovalNotice(User recipient, String note) {
        safeSendTeacherNotice(recipient, "approval", () -> sendTeacherApprovalNotice(recipient, note));
    }

    default void sendTeacherRejectionNotice(User recipient, String reason) {
        String normalizedReason = TextUtils.trimToNull(reason);
        sendEmail(
                recipient,
                "Teacher verification update",
                "Your teacher account verification was not approved."
                        + optionalParagraph("Reason: ", normalizedReason));
    }

    default void safeSendTeacherRejectionNotice(User recipient, String reason) {
        safeSendTeacherNotice(recipient, "rejection", () -> sendTeacherRejectionNotice(recipient, reason));
    }

    private void safeSendTeacherNotice(User recipient, String noticeType, Runnable delivery) {
        try {
            delivery.run();
        } catch (Exception exception) {
            Long recipientId = recipient != null ? recipient.getId() : null;
            log.warn("Teacher {} email delivery failed for user id {}: {}", noticeType, recipientId, ExceptionLogUtils.safeSummary(exception));
            log.debug("Teacher {} email delivery stack trace for user id {}.", noticeType, recipientId, exception);
        }
    }

    default void sendPasswordResetEmail(User recipient, String resetLink, String rawToken) {
        sendEmail(
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

    private String optionalParagraph(String prefix, String value) {
        return value == null ? "" : BLANK_LINE + prefix + value;
    }
}

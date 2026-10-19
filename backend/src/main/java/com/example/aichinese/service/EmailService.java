package com.example.aichinese.service;

import com.example.aichinese.entity.User;
import com.example.aichinese.util.TextUtils;

public interface EmailService {

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

    default void sendTeacherRejectionNotice(User recipient, String reason) {
        String normalizedReason = TextUtils.trimToNull(reason);
        sendEmail(
                recipient,
                "Teacher verification update",
                "Your teacher account verification was not approved."
                        + optionalParagraph("Reason: ", normalizedReason));
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

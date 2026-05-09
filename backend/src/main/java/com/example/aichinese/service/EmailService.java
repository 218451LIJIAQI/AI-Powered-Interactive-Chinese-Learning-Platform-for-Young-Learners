package com.example.aichinese.service;

import com.example.aichinese.entity.User;

public interface EmailService {

    void sendEmail(User recipient, String subject, String body);

    default void sendTeacherApprovalNotice(User recipient, String note) {
        sendEmail(
                recipient,
                "Teacher approval update",
                "Your teacher account has been approved."
                        + (note == null || note.isBlank() ? "" : System.lineSeparator() + System.lineSeparator() + "Note: " + note.trim()));
    }

    default void sendTeacherRejectionNotice(User recipient, String reason) {
        sendEmail(
                recipient,
                "Teacher verification update",
                "Your teacher account verification was not approved."
                        + (reason == null || reason.isBlank()
                                ? ""
                                : System.lineSeparator() + System.lineSeparator() + "Reason: " + reason.trim()));
    }

    default void sendSupportTicketReply(User recipient, String subject, String message) {
        sendEmail(
                recipient,
                "Support ticket update: " + subject,
                message);
    }
}

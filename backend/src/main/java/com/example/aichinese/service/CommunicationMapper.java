package com.example.aichinese.service;

import java.util.Comparator;

import org.springframework.stereotype.Component;

import com.example.aichinese.dto.communication.AuditLogResponse;
import com.example.aichinese.dto.communication.ModerationRecordResponse;
import com.example.aichinese.dto.communication.NotificationResponse;
import com.example.aichinese.dto.communication.ReportResponse;
import com.example.aichinese.dto.communication.ReviewResponse;
import com.example.aichinese.dto.communication.SupportTicketMessageResponse;
import com.example.aichinese.dto.communication.SupportTicketResponse;
import com.example.aichinese.dto.communication.TeacherFeedbackResponse;
import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.ContentModerationRecord;
import com.example.aichinese.entity.Notification;
import com.example.aichinese.entity.Report;
import com.example.aichinese.entity.Review;
import com.example.aichinese.entity.SupportTicket;
import com.example.aichinese.entity.SupportTicketMessage;
import com.example.aichinese.entity.TeacherFeedback;
import com.example.aichinese.entity.User;

@Component
public class CommunicationMapper {

    public NotificationResponse toNotificationResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getNotificationType().name(),
                notification.getNotificationStatus().name(),
                notification.getTargetType(),
                notification.getTargetId(),
                notification.getTargetPath(),
                notification.getCreatedAt(),
                notification.getReadAt());
    }

    public ReportResponse toReportResponse(Report report, String targetTitle, boolean includeReporterDetails) {
        return new ReportResponse(
                report.getId(),
                report.getTargetType(),
                report.getTargetId(),
                targetTitle,
                report.getReason(),
                report.getReportStatus().name(),
                includeReporterDetails && report.getReporter() != null ? report.getReporter().getDisplayName() : null,
                includeReporterDetails && report.getReporter() != null ? report.getReporter().getEmail() : null,
                report.getRelatedTeacher() != null ? report.getRelatedTeacher().getId() : null,
                report.getRelatedTeacher() != null && report.getRelatedTeacher().getUser() != null
                        ? report.getRelatedTeacher().getUser().getDisplayName()
                        : null,
                report.isTeacherRevisionRequested(),
                report.getAdminNote(),
                report.getCreatedAt(),
                report.getUpdatedAt());
    }

    public ReviewResponse toReviewResponse(Review review, String targetTitle) {
        return new ReviewResponse(
                review.getId(),
                review.getTargetType(),
                review.getTargetId(),
                targetTitle,
                review.getRating(),
                review.getReviewComment(),
                review.getReviewer() != null && review.getReviewer().getUser() != null
                        ? review.getReviewer().getUser().getDisplayName()
                        : null,
                review.getReviewStatus().name(),
                review.isVisible(),
                review.getCreatedAt(),
                review.getUpdatedAt());
    }

    public SupportTicketResponse toSupportTicketResponse(SupportTicket ticket) {
        User requester = ticket.getCreatedByUser();
        return new SupportTicketResponse(
                ticket.getId(),
                requester != null ? requester.getId() : null,
                requester != null ? requester.getDisplayName() : null,
                requester != null ? requester.getEmail() : null,
                extractPrimaryRole(requester),
                ticket.getSubject(),
                ticket.getDescription(),
                ticket.getTicketStatus().name(),
                ticket.getTicketPriority().name(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt());
    }

    public SupportTicketMessageResponse toSupportTicketMessageResponse(SupportTicketMessage message) {
        User sender = message.getSenderUser();
        return new SupportTicketMessageResponse(
                message.getId(),
                sender != null ? sender.getId() : null,
                sender != null ? sender.getDisplayName() : null,
                extractPrimaryRole(sender),
                message.getMessage(),
                message.getCreatedAt());
    }

    public TeacherFeedbackResponse toTeacherFeedbackResponse(TeacherFeedback feedback) {
        return new TeacherFeedbackResponse(
                feedback.getId(),
                feedback.getTeacher() != null ? feedback.getTeacher().getId() : null,
                feedback.getTeacher() != null && feedback.getTeacher().getUser() != null
                        ? feedback.getTeacher().getUser().getDisplayName()
                        : null,
                feedback.getStudent() != null ? feedback.getStudent().getId() : null,
                feedback.getStudent() != null && feedback.getStudent().getUser() != null
                        ? feedback.getStudent().getUser().getDisplayName()
                        : null,
                feedback.getLesson() != null ? feedback.getLesson().getId() : null,
                feedback.getLesson() != null ? feedback.getLesson().getTitle() : null,
                feedback.getQuizAttempt() != null ? feedback.getQuizAttempt().getId() : null,
                feedback.getQuizAttempt() != null && feedback.getQuizAttempt().getQuiz() != null
                        ? feedback.getQuizAttempt().getQuiz().getTitle()
                        : null,
                feedback.getFeedbackType(),
                feedback.getFeedbackMessage(),
                feedback.getCreatedAt(),
                feedback.getUpdatedAt());
    }

    public ModerationRecordResponse toModerationRecordResponse(ContentModerationRecord record) {
        return new ModerationRecordResponse(
                record.getId(),
                record.getTargetType(),
                record.getTargetId(),
                record.getTargetTitleSnapshot(),
                record.getActionTaken(),
                record.getNote(),
                record.getAdminUser() != null ? record.getAdminUser().getId() : null,
                record.getAdminUser() != null ? record.getAdminUser().getDisplayName() : null,
                record.getRelatedTeacher() != null ? record.getRelatedTeacher().getId() : null,
                record.getRelatedTeacher() != null && record.getRelatedTeacher().getUser() != null
                        ? record.getRelatedTeacher().getUser().getDisplayName()
                        : null,
                record.getCreatedAt());
    }

    public AuditLogResponse toAuditLogResponse(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getActorUser() != null ? auditLog.getActorUser().getId() : null,
                auditLog.getActorEmail(),
                auditLog.getActionType(),
                auditLog.getTargetType(),
                auditLog.getTargetId(),
                auditLog.getDescription(),
                auditLog.getIpAddress(),
                auditLog.getCreatedAt());
    }

    private String extractPrimaryRole(User user) {
        if (user == null || user.getRoles() == null || user.getRoles().isEmpty()) {
            return null;
        }

        return user.getRoles().stream()
                .map(role -> role.getName().name())
                .sorted(Comparator.naturalOrder())
                .findFirst()
                .orElse(null);
    }
}

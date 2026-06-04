package com.example.aichinese.service;

import org.springframework.stereotype.Component;

import com.example.aichinese.dto.communication.AuditLogResponse;
import com.example.aichinese.dto.communication.ModerationRecordResponse;
import com.example.aichinese.dto.communication.NotificationResponse;
import com.example.aichinese.dto.communication.StudentTeacherFeedbackResponse;
import com.example.aichinese.dto.communication.TeacherFeedbackResponse;
import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.ContentModerationRecord;
import com.example.aichinese.entity.Notification;
import com.example.aichinese.entity.TeacherFeedback;

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

    public StudentTeacherFeedbackResponse toStudentTeacherFeedbackResponse(TeacherFeedback feedback) {
        return new StudentTeacherFeedbackResponse(
                feedback.getId(),
                feedback.getTeacher() != null && feedback.getTeacher().getUser() != null
                        ? feedback.getTeacher().getUser().getDisplayName()
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
                auditLog.getRequestId(),
                auditLog.getCreatedAt());
    }
}
